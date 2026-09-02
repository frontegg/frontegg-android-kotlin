package com.frontegg.android.services

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.frontegg.android.FronteggApp
import com.frontegg.android.exceptions.FailedToAuthenticateException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * FR-26903: a 401 from /oauth/token means the refresh token is dead, so no retry can ever
 * succeed. RefreshTokenJobService and RefreshTokenAlarmReceiver already treat it as terminal
 * (both call clearCredentials()), and iOS clears the keychain and goes unauthenticated. The
 * foreground path went through refreshIdempotent, which caught every failure alike: it kept
 * the credentials, left isAuthenticated true, and re-enqueued a HIGH-priority retry that
 * replaced itself on each failure — an unbounded loop with no backoff.
 */
class RefreshToken401TerminalTest {
    private val storageMock = mockk<FronteggInnerStorage>()
    private val mockContext = mockk<Context>()
    private val credentialManagerMock = mockk<CredentialManager>()
    private val apiMock = mockk<Api>()
    private lateinit var auth: FronteggAuthService

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

        val appLifecycle = mockk<FronteggAppLifecycle>()
        every { appLifecycle.startApp } returns FronteggCallback()
        every { appLifecycle.stopApp } returns FronteggCallback()

        val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>()
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

        // enableOfflineMode = true is the configuration the reporting customer runs, and the
        // one that arms the self-replacing retry enqueue in refreshIdempotent's catch.
        auth = FronteggAuthService(
            credentialManager = credentialManagerMock,
            appLifecycle = appLifecycle,
            refreshTokenTimer = refreshTokenTimer,
            enableOfflineMode = true,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            disableAutoRefresh = false
        )

        auth.refreshToken.value = "DeadRefreshToken"
        auth.accessToken.value = "ExpiredAccessToken"
        auth.isAuthenticated.value = true

        every { apiMock.refreshToken(any()) } throws
            FailedToAuthenticateException(error = "Refresh token failed: 401 - invalid_grant")
    }

    @Test
    fun `auto refresh 401 clears credentials and leaves the session unauthenticated`() {
        runBlocking {
            auth.refreshTokenAndWaitInternal(FronteggAuthService.RefreshInvocationSource.INTERNAL_AUTO)
        }

        assertFalse("session must transition to unauthenticated", auth.isAuthenticated.value)
        assertNull("refresh token must be discarded", auth.refreshToken.value)
        assertNull("access token must be discarded", auth.accessToken.value)
    }

    @Test
    fun `auto refresh 401 does not enqueue a retry`() {
        runBlocking {
            auth.refreshTokenAndWaitInternal(FronteggAuthService.RefreshInvocationSource.INTERNAL_AUTO)
        }

        coVerify(exactly = 0) { anyConstructed<RequestQueue>().enqueue(any(), any(), any()) }
    }
}
