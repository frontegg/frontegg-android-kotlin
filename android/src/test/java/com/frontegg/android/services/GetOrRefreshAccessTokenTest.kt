package com.frontegg.android.services

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.frontegg.android.FronteggApp
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.FronteggCallback
import com.frontegg.android.utils.NetworkGate
import com.frontegg.android.utils.RequestQueue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.time.Instant
import java.util.Base64
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

class GetOrRefreshAccessTokenTest {
    private companion object {
        const val OFFLINE_FAIL_FAST_LIMIT_MS = 30_000L
    }

    private val storageMock = mockk<FronteggInnerStorage>()
    private val mockContext = mockk<Context>()
    private val credentialManagerMock = mockk<CredentialManager>()
    private val apiMock = mockk<Api>()
    private val appLifecycle = mockk<FronteggAppLifecycle>()
    private val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>()

    @Before
    fun setUp() {
        // Other test classes in this JVM leave objects such as JWTHelper mocked.
        unmockkAll()
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.user.value = null
        FronteggState.isAuthenticated.value = false
        FronteggState.isLoading.value = false
        FronteggState.initializing.value = false
        FronteggState.refreshingToken.value = false
        FronteggState.isOfflineMode.value = false

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() } returns storageMock
        every { storageMock.clientId } returns "TestClientId"
        every { storageMock.applicationId } returns "TestApplicationId"
        every { storageMock.baseUrl } returns "https://base.url.com"
        every { storageMock.regions } returns listOf()
        every { storageMock.enableSessionPerTenant } returns false
        every { storageMock.entitlementsEnabled } returns false
        every { storageMock.tenantResolver } returns null
        mockkObject(FronteggApp)

        every { credentialManagerMock.context } returns mockContext
        every { credentialManagerMock.get(any()) } returns null
        every { credentialManagerMock.getCurrentTenantId() } returns null
        every { credentialManagerMock.setEnableSessionPerTenant(any()) } returns Unit
        every { credentialManagerMock.save(any(), any(), any()) } returns true
        every { credentialManagerMock.clear(any()) } returns Unit
        every { credentialManagerMock.clearOfflineUser() } returns true

        val prefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val editor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor

        every { appLifecycle.startApp } returns FronteggCallback()
        every { appLifecycle.stopApp } returns FronteggCallback()

        every { refreshTokenTimer.refreshTokenIfNeeded } returns FronteggCallback()
        every { refreshTokenTimer.cancelLastTimer() } returns Unit
        every { refreshTokenTimer.scheduleTimer(any()) } returns Unit

        mockkObject(ApiProvider)
        every { ApiProvider.getApi(any()) } returns apiMock
        every { apiMock.getServerUrl() } returns "https://test.frontegg.com"
        every { apiMock.evictIdleConnections() } returns Unit
        every { apiMock.me() } returns User()

        mockkObject(NetworkGate)
        every { NetworkGate.setFronteggBaseUrl(any()) } returns Unit
        every { NetworkGate.isNetworkLikelyGood(any()) } returns true

        mockkConstructor(RequestQueue::class)
        coEvery { anyConstructed<RequestQueue>().enqueue(any(), any(), any()) } returns Unit
        coEvery { anyConstructed<RequestQueue>().processAll() } returns 0
        coEvery { anyConstructed<RequestQueue>().clear() } returns Unit
        every { anyConstructed<RequestQueue>().hasQueuedRequests() } returns false

        mockkConstructor(MutableLiveData::class)
        every { anyConstructed<MutableLiveData<Boolean>>().postValue(any()) } returns Unit
        every { anyConstructed<MutableLiveData<Boolean>>().value } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createAuth(
        enableOfflineMode: Boolean = false,
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): FronteggAuthService {
        return FronteggAuthService(
            credentialManager = credentialManagerMock,
            appLifecycle = appLifecycle,
            refreshTokenTimer = refreshTokenTimer,
            enableOfflineMode = enableOfflineMode,
            ioDispatcher = ioDispatcher,
            mainDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun jwtExpiringIn(seconds: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val expiry = Instant.now().epochSecond + seconds
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = encoder.encodeToString("""{"exp":$expiry}""".toByteArray())
        return "$header.$payload.signature"
    }

    private fun refreshResponse(accessToken: String): AuthResponse {
        val response = AuthResponse()
        response.access_token = accessToken
        response.refresh_token = "RotatedRefreshToken"
        response.id_token = "IdToken"
        response.token_type = "Bearer"
        return response
    }

    @Test
    fun `returns null without refreshing when there is no session`() {
        val auth = createAuth()

        val result = runBlocking { auth.getOrRefreshAccessToken() }

        assertNull(result)
        verify(exactly = 0) { apiMock.refreshToken(any()) }
    }

    @Test
    fun `returns the cached access token when it is valid for more than 15 seconds`() {
        val auth = createAuth()
        val validAccessToken = jwtExpiringIn(300)
        auth.refreshToken.value = "RefreshToken"
        auth.accessToken.value = validAccessToken

        val result = runBlocking { auth.getOrRefreshAccessToken() }

        assertEquals(validAccessToken, result)
        verify(exactly = 0) { apiMock.refreshToken(any()) }
    }

    @Test
    fun `refreshes when the access token expires within 15 seconds`() {
        val auth = createAuth()
        val refreshedAccessToken = jwtExpiringIn(600)
        auth.refreshToken.value = "RefreshToken"
        auth.accessToken.value = jwtExpiringIn(10)
        every { apiMock.refreshToken("RefreshToken") } returns refreshResponse(refreshedAccessToken)

        val result = runBlocking { auth.getOrRefreshAccessToken() }

        assertEquals(refreshedAccessToken, result)
        assertEquals("RotatedRefreshToken", auth.refreshToken.value)
    }

    @Test
    fun `refreshes on the io dispatcher instead of the calling thread`() {
        var ioThread: Thread? = null
        val ioExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable).also { ioThread = it } }
        val auth = createAuth(ioDispatcher = ioExecutor.asCoroutineDispatcher())
        auth.refreshToken.value = "RefreshToken"
        auth.accessToken.value = jwtExpiringIn(-60)
        var refreshThread: Thread? = null
        every { apiMock.refreshToken("RefreshToken") } answers {
            refreshThread = Thread.currentThread()
            refreshResponse(jwtExpiringIn(600))
        }

        try {
            runBlocking { auth.getOrRefreshAccessToken() }
        } finally {
            ioExecutor.shutdown()
        }

        assertSame(ioThread, refreshThread)
    }

    @Test
    fun `returns null without waiting when no region is selected in a multi-region app`() {
        every { storageMock.regions } returns listOf(RegionConfig("eu", "https://eu.frontegg.com", "EuClientId"))
        every { storageMock.selectedRegion } returns null
        val auth = createAuth()
        auth.initializing.value = true

        val result = runBlocking { auth.getOrRefreshAccessToken() }

        assertNull(result)
        verify(exactly = 0) { apiMock.refreshToken(any()) }
    }

    @Test
    fun `restores the refresh token from storage before deciding there is no session`() {
        val auth = createAuth()
        val refreshedAccessToken = jwtExpiringIn(600)
        every { credentialManagerMock.get(CredentialKeys.REFRESH_TOKEN) } returns "StoredRefreshToken"
        every { apiMock.refreshToken("StoredRefreshToken") } returns refreshResponse(refreshedAccessToken)

        val result = runBlocking { auth.getOrRefreshAccessToken() }

        assertEquals(refreshedAccessToken, result)
    }

    @Test
    fun `waits for an in-flight refresh to settle before reading the token`() {
        val auth = createAuth()
        val refreshedAccessToken = jwtExpiringIn(600)
        auth.refreshToken.value = "RefreshToken"
        auth.accessToken.value = jwtExpiringIn(5)
        auth.refreshingToken.value = true

        val result = runBlocking {
            launch(Dispatchers.Default) {
                delay(300)
                auth.accessToken.value = refreshedAccessToken
                auth.refreshingToken.value = false
            }
            auth.getOrRefreshAccessToken()
        }

        assertEquals(refreshedAccessToken, result)
        verify(exactly = 0) { apiMock.refreshToken(any()) }
    }

    @Test
    fun `returns null and clears the session when the refresh token is rejected`() {
        val auth = createAuth()
        auth.refreshToken.value = "RevokedRefreshToken"
        auth.accessToken.value = jwtExpiringIn(-60)
        auth.isAuthenticated.value = true
        every { apiMock.refreshToken(any()) } throws FailedToAuthenticateException(error = "401")

        val result = runBlocking { auth.getOrRefreshAccessToken() }

        assertNull(result)
        assertNull(auth.refreshToken.value)
    }

    @Test
    fun `throws the refresh failure when the network is down and offline mode is disabled`() {
        val auth = createAuth()
        auth.refreshToken.value = "RefreshToken"
        auth.accessToken.value = jwtExpiringIn(-60)
        every { apiMock.refreshToken(any()) } throws IOException("connection reset")

        assertThrows<IOException> { runBlocking { auth.getOrRefreshAccessToken() } }
        assertEquals("RefreshToken", auth.refreshToken.value)
    }

    @Test
    fun `throws without refreshing when the device is offline and offline mode is disabled`() {
        val auth = createAuth()
        auth.refreshToken.value = "RefreshToken"
        auth.accessToken.value = jwtExpiringIn(-60)
        every { NetworkGate.isNetworkLikelyGood(any()) } returns false

        val elapsedMs = measureTimeMillis {
            assertThrows<IOException> { runBlocking { auth.getOrRefreshAccessToken() } }
        }

        assertTrue(elapsedMs < OFFLINE_FAIL_FAST_LIMIT_MS)
        assertEquals("RefreshToken", auth.refreshToken.value)
        verify(exactly = 0) { apiMock.refreshToken(any()) }
    }

    @Test
    fun `returns the cached access token without refreshing when the device is offline in offline mode`() {
        val auth = createAuth(enableOfflineMode = true)
        val cachedAccessToken = jwtExpiringIn(-60)
        auth.refreshToken.value = "RefreshToken"
        auth.accessToken.value = cachedAccessToken
        every { NetworkGate.isNetworkLikelyGood(any()) } returns false

        var result: String? = null
        val elapsedMs = measureTimeMillis {
            result = runBlocking { auth.getOrRefreshAccessToken() }
        }

        assertTrue(elapsedMs < OFFLINE_FAIL_FAST_LIMIT_MS)
        assertEquals(cachedAccessToken, result)
        assertTrue(auth.isOfflineMode.value)
        verify(exactly = 0) { apiMock.refreshToken(any()) }
    }

    @Test
    fun `returns the cached access token on a connectivity failure in offline mode`() {
        val auth = createAuth(enableOfflineMode = true)
        val cachedAccessToken = jwtExpiringIn(-60)
        auth.refreshToken.value = "RefreshToken"
        auth.accessToken.value = cachedAccessToken
        every { apiMock.refreshToken(any()) } throws IOException("connection reset")

        val result = runBlocking { auth.getOrRefreshAccessToken() }

        assertEquals(cachedAccessToken, result)
    }

    @Test
    fun `callback variant delivers the access token`() {
        val auth = createAuth()
        val validAccessToken = jwtExpiringIn(300)
        auth.refreshToken.value = "RefreshToken"
        auth.accessToken.value = validAccessToken
        var deliveredToken: String? = null
        var deliveredError: Exception? = null

        auth.getOrRefreshAccessToken { accessToken, error ->
            deliveredToken = accessToken
            deliveredError = error
        }

        assertEquals(validAccessToken, deliveredToken)
        assertNull(deliveredError)
    }

    @Test
    fun `callback variant delivers the refresh failure`() {
        val auth = createAuth()
        val networkFailure = IOException("connection reset")
        auth.refreshToken.value = "RefreshToken"
        auth.accessToken.value = jwtExpiringIn(-60)
        every { apiMock.refreshToken(any()) } throws networkFailure
        var deliveredToken: String? = "unset"
        var deliveredError: Exception? = null

        auth.getOrRefreshAccessToken { accessToken, error ->
            deliveredToken = accessToken
            deliveredError = error
        }

        assertNull(deliveredToken)
        assertTrue(deliveredError is IOException)
        assertEquals(networkFailure.message, deliveredError?.message)
    }
}
