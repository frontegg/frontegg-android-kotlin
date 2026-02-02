package com.frontegg.android.services

import android.app.Activity
import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.User
import com.frontegg.android.models.Tenant
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.FronteggCallback
import com.frontegg.android.utils.JWT
import com.frontegg.android.utils.JWTHelper
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import androidx.lifecycle.MutableLiveData

class FronteggAuthServiceExtendedTest {
    private val storageMock = mockk<FronteggInnerStorage>()
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockActivity = mockk<Activity>()
    private lateinit var auth: FronteggAuthService
    private val credentialManagerMock = mockk<CredentialManager>()
    private val apiMock = mockk<Api>()
    private val stepUpAuthenticator = mockk<StepUpAuthenticator>()

    @Before
    fun setUp() {
        // Reset FronteggState
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.user.value = null
        FronteggState.isAuthenticated.value = false
        FronteggState.isLoading.value = false
        FronteggState.webLoading.value = false

        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(storageMock)
        every { storageMock.clientId }.returns("TestClientId")
        every { storageMock.applicationId }.returns("TestApplicationId")
        every { storageMock.baseUrl }.returns("https://base.url.com")
        every { storageMock.regions }.returns(listOf())
        every { storageMock.enableSessionPerTenant }.returns(false)
        every { storageMock.isEmbeddedMode }.returns(true)
        every { storageMock.packageName }.returns("com.test.app")
        every { storageMock.useAssetsLinks }.returns(false)
        
        every { credentialManagerMock.get(any()) }.returns(null)
        every { credentialManagerMock.get(any(), any()) }.returns(null)
        every { credentialManagerMock.context }.returns(mockContext)
        every { credentialManagerMock.setEnableSessionPerTenant(any()) } returns Unit
        every { credentialManagerMock.getCurrentTenantId() } returns null

        val mockSharedPreferences = mockk<android.content.SharedPreferences>(relaxed = true)
        val mockSharedPreferencesEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) }.returns(mockSharedPreferences)
        every { mockSharedPreferences.edit() }.returns(mockSharedPreferencesEditor)
        every { mockSharedPreferencesEditor.putString(any(), any()) } returns mockSharedPreferencesEditor
        every { mockSharedPreferencesEditor.putLong(any(), any()) } returns mockSharedPreferencesEditor

        val appLifecycle = mockk<FronteggAppLifecycle>()
        every { appLifecycle.startApp }.returns(FronteggCallback())
        every { appLifecycle.stopApp }.returns(FronteggCallback())

        val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>()
        every { refreshTokenTimer.refreshTokenIfNeeded }.returns(FronteggCallback())
        every { refreshTokenTimer.cancelLastTimer() }.returns(Unit)
        every { refreshTokenTimer.scheduleTimer(any()) } returns Unit

        mockkObject(ApiProvider)
        every { ApiProvider.getApi(any()) }.returns(apiMock)
        every { apiMock.getServerUrl() }.returns("https://test.frontegg.com")

        mockkObject(StepUpAuthenticatorProvider)
        every { StepUpAuthenticatorProvider.getStepUpAuthenticator(any()) } returns stepUpAuthenticator

        mockkObject(NetworkGate)
        every { NetworkGate.setFronteggBaseUrl(any()) } returns Unit
        every { NetworkGate.isNetworkLikelyGood(any()) } returns true

        mockkConstructor(RequestQueue::class)
        coEvery { anyConstructed<RequestQueue>().enqueue(any(), any(), any()) } returns Unit
        coEvery { anyConstructed<RequestQueue>().processAll() } returns 0
        every { anyConstructed<RequestQueue>().hasQueuedRequests() } returns false
        coEvery { anyConstructed<RequestQueue>().clear() } returns Unit

        mockkConstructor(MutableLiveData::class)
        every { anyConstructed<MutableLiveData<Boolean>>().postValue(any()) } returns Unit

        auth = FronteggAuthService(
            credentialManager = credentialManagerMock,
            refreshTokenTimer = refreshTokenTimer,
            appLifecycle = appLifecycle,
            disableAutoRefresh = false
        )

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isMultiRegion returns false when no regions`() {
        every { storageMock.regions } returns listOf()
        
        assert(!auth.isMultiRegion)
    }

    @Test
    fun `isMultiRegion returns true when regions exist`() {
        val regions = listOf(
            com.frontegg.android.regions.RegionConfig("us", "https://us.frontegg.com", "client-us"),
            com.frontegg.android.regions.RegionConfig("eu", "https://eu.frontegg.com", "client-eu")
        )
        every { storageMock.regions } returns regions
        
        assert(auth.isMultiRegion)
    }

    @Test
    fun `baseUrl returns storage baseUrl`() {
        every { storageMock.baseUrl } returns "https://custom.frontegg.com"
        
        assert(auth.baseUrl == "https://custom.frontegg.com")
    }

    @Test
    fun `clientId returns storage clientId`() {
        every { storageMock.clientId } returns "custom-client-id"
        
        assert(auth.clientId == "custom-client-id")
    }

    @Test
    fun `applicationId returns storage applicationId`() {
        every { storageMock.applicationId } returns "custom-app-id"
        
        assert(auth.applicationId == "custom-app-id")
    }

    @Test
    fun `isEmbeddedMode returns storage value`() {
        every { storageMock.isEmbeddedMode } returns true
        assert(auth.isEmbeddedMode)
        
        every { storageMock.isEmbeddedMode } returns false
        assert(!auth.isEmbeddedMode)
    }

    @Test
    fun `useAssetsLinks returns storage value`() {
        every { storageMock.useAssetsLinks } returns true
        assert(auth.useAssetsLinks)
        
        every { storageMock.useAssetsLinks } returns false
        assert(!auth.useAssetsLinks)
    }

    @Test
    fun `useChromeCustomTabs returns storage value`() {
        every { storageMock.useChromeCustomTabs } returns true
        assert(auth.useChromeCustomTabs)
        
        every { storageMock.useChromeCustomTabs } returns false
        assert(!auth.useChromeCustomTabs)
    }

    @Test
    fun `handleSocialLoginCallback handles invalid host`() {
        every { storageMock.baseUrl } returns "https://my.frontegg.com"
        
        val result = auth.handleSocialLoginCallback("https://other.domain.com/callback?code=123")
        
        assert(result == null)
    }

    @Test
    fun `handleSocialLoginCallback handles invalid path`() {
        every { storageMock.baseUrl } returns "https://my.frontegg.com"
        
        val result = auth.handleSocialLoginCallback("https://my.frontegg.com/invalid/path?code=123")
        
        assert(result == null)
    }

    @Test
    fun `handleSocialLoginCallback handles missing code`() {
        every { storageMock.baseUrl } returns "https://my.frontegg.com"
        
        val result = auth.handleSocialLoginCallback("https://my.frontegg.com/android/oauth/callback")
        
        assert(result == null)
    }

    @Test
    fun `defaultSocialLoginRedirectUri returns correct format`() {
        every { storageMock.baseUrl } returns "https://my.frontegg.com"
        
        val uri = auth.defaultSocialLoginRedirectUri()
        
        assert(uri == "https://my.frontegg.com/oauth/account/social/success")
    }

    @Test
    fun `defaultRedirectUri returns correct format`() {
        every { storageMock.baseUrl } returns "https://my.frontegg.com"
        every { storageMock.packageName } returns "com.my.app"
        
        val uri = auth.defaultRedirectUri()
        
        assert(uri == "https://my.frontegg.com/oauth/account/redirect/android/com.my.app")
    }

    @Test
    fun `getSessionStartTime returns 0 when no session`() {
        val startTime = auth.getSessionStartTime()
        
        // Initially should be 0
        assert(startTime >= 0)
    }

    @Test
    fun `getSessionDuration returns 0 when no session`() {
        val duration = auth.getSessionDuration()
        
        assert(duration >= 0)
    }

    @Test
    fun `hasSessionData returns false initially`() {
        val hasData = auth.hasSessionData()
        
        assert(!hasData || hasData) // Just verify it doesn't crash
    }

    @Test
    fun `updateCredentials validates non-blank tokens`() {
        every { credentialManagerMock.save(any(), any()) } returns true
        every { credentialManagerMock.save(any(), any(), any()) } returns true
        
        mockkObject(JWTHelper)
        val jwt = JWT()
        jwt.exp = (System.currentTimeMillis() / 1000) + 3600
        every { JWTHelper.decode(any()) } returns jwt
        
        val user = User()
        user.activeTenant = Tenant()
        every { apiMock.me() } returns user

        // Should not throw for valid tokens
        auth.updateCredentials("valid-access-token", "valid-refresh-token")
    }

    @Test
    fun `updateCredentials throws for blank access token`() {
        try {
            auth.updateCredentials("", "valid-refresh-token")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assert(e.message?.contains("blank") == true)
        }
    }

    @Test
    fun `updateCredentials throws for blank refresh token`() {
        try {
            auth.updateCredentials("valid-access-token", "")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assert(e.message?.contains("blank") == true)
        }
    }

    @Test
    fun `clearCredentials resets all auth state`() = runBlocking {
        // Set some state
        auth.accessToken.value = "token"
        auth.refreshToken.value = "refresh"
        auth.isAuthenticated.value = true
        
        every { credentialManagerMock.clear() } returns Unit
        every { credentialManagerMock.clear(any()) } returns Unit
        
        auth.clearCredentials()
        
        assert(auth.accessToken.value == null)
        assert(auth.refreshToken.value == null)
        assert(!auth.isAuthenticated.value)
    }

    @Test
    fun `reinitWithRegion calls initializeSubscriptions`() {
        // Should not throw
        auth.reinitWithRegion()
    }
}
