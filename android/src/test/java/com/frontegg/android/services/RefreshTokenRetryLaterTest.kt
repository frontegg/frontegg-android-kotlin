package com.frontegg.android.services

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.frontegg.android.FronteggApp
import com.frontegg.android.exceptions.RefreshTokenRetryLaterException
import com.frontegg.android.utils.FronteggCallback
import com.frontegg.android.utils.NetworkGate
import com.frontegg.android.utils.RequestQueue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * FR-28398: a 408 or 429 from /oauth/token keeps the session, is not retried at once,
 * and re-arms the refresh timer with backoff so the session recovers on its own.
 */
class RefreshTokenRetryLaterTest {
    private val storageMock = mockk<FronteggInnerStorage>()
    private val mockContext = mockk<Context>()
    private val credentialManagerMock = mockk<CredentialManager>()
    private val apiMock = mockk<Api>()
    private val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>()
    private lateinit var appLifecycle: FronteggAppLifecycle

    @Before
    fun setUp() {
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.user.value = null
        FronteggState.isAuthenticated.value = false
        FronteggState.isLoading.value = false
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
        every { credentialManagerMock.clear(any()) } returns Unit
        every { credentialManagerMock.clearOfflineUser() } returns true

        val prefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val editor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor

        appLifecycle = mockk()
        every { appLifecycle.startApp } returns FronteggCallback()
        every { appLifecycle.stopApp } returns FronteggCallback()

        every { refreshTokenTimer.refreshTokenIfNeeded } returns FronteggCallback()
        every { refreshTokenTimer.cancelLastTimer() } returns Unit
        every { refreshTokenTimer.scheduleTimer(any()) } returns Unit

        mockkObject(ApiProvider)
        every { ApiProvider.getApi(any()) } returns apiMock
        every { apiMock.getServerUrl() } returns "https://test.frontegg.com"
        every { apiMock.evictIdleConnections() } returns Unit

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

    private fun createAuth(enableOfflineMode: Boolean): FronteggAuthService {
        val auth = FronteggAuthService(
            credentialManager = credentialManagerMock,
            appLifecycle = appLifecycle,
            refreshTokenTimer = refreshTokenTimer,
            enableOfflineMode = enableOfflineMode,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            disableAutoRefresh = false
        )
        auth.refreshToken.value = "ValidRefreshToken"
        auth.accessToken.value = "ExpiredAccessToken"
        auth.isAuthenticated.value = true
        return auth
    }

    private fun FronteggAuthService.refreshOnce() = runBlocking {
        refreshTokenAndWaitInternal(FronteggAuthService.RefreshInvocationSource.INTERNAL_AUTO)
    }

    @Test
    fun `429 keeps the session, sends one request and retries after Retry-After`() {
        val auth = createAuth(enableOfflineMode = true)
        every { apiMock.refreshToken(any()) } throws
            RefreshTokenRetryLaterException(statusCode = 429, retryAfterMillis = 30_000L, message = "429")

        auth.refreshOnce()

        assertEquals("ValidRefreshToken", auth.refreshToken.value)
        assertTrue(auth.isAuthenticated.value)
        verify(exactly = 1) { apiMock.refreshToken(any()) }
        verify(exactly = 1) { refreshTokenTimer.scheduleTimer(30_000L) }
        coVerify(exactly = 0) { anyConstructed<RequestQueue>().enqueue(any(), any(), any()) }
    }

    @Test
    fun `repeated 429 backs off from 10 seconds`() {
        val auth = createAuth(enableOfflineMode = true)
        every { apiMock.refreshToken(any()) } throws
            RefreshTokenRetryLaterException(statusCode = 429, retryAfterMillis = null, message = "429")

        repeat(3) { auth.refreshOnce() }

        verifyOrder {
            refreshTokenTimer.scheduleTimer(10_000L)
            refreshTokenTimer.scheduleTimer(20_000L)
            refreshTokenTimer.scheduleTimer(40_000L)
        }
    }

    @Test
    fun `Retry-After longer than the backoff cap is honoured in full`() {
        val auth = createAuth(enableOfflineMode = true)
        every { apiMock.refreshToken(any()) } throws
            RefreshTokenRetryLaterException(statusCode = 429, retryAfterMillis = 600_000L, message = "429")

        auth.refreshOnce()

        verify(exactly = 1) { refreshTokenTimer.scheduleTimer(600_000L) }
    }

    @Test
    fun `clearing the session resets the backoff`() {
        val auth = createAuth(enableOfflineMode = true)
        every { apiMock.refreshToken(any()) } throws
            RefreshTokenRetryLaterException(statusCode = 429, retryAfterMillis = null, message = "429")
        repeat(2) { auth.refreshOnce() }

        auth.clearCredentials()
        auth.refreshToken.value = "NextSessionRefreshToken"
        auth.accessToken.value = "NextSessionAccessToken"
        auth.refreshOnce()

        verify(exactly = 2) { refreshTokenTimer.scheduleTimer(10_000L) }
    }

    @Test
    fun `transient failure without offline mode re-arms the refresh timer`() {
        val auth = createAuth(enableOfflineMode = false)
        every { apiMock.refreshToken(any()) } throws IOException("503")

        auth.refreshOnce()

        assertEquals("ValidRefreshToken", auth.refreshToken.value)
        verify(exactly = 1) { refreshTokenTimer.scheduleTimer(10_000L) }
    }
}
