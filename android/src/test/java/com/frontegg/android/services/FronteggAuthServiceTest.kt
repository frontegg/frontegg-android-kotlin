package com.frontegg.android.services

import android.app.Activity
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.lifecycle.MutableLiveData
import com.frontegg.android.AuthenticationActivity
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.FronteggApp
import com.frontegg.android.embedded.CredentialManagerHandler
import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.Tenant
import com.frontegg.android.models.User
import com.frontegg.android.models.WebAuthnAssertionRequest
import com.frontegg.android.models.WebAuthnRegistrationRequest
import com.frontegg.android.testUtils.BlockCoroutineDispatcher
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.FronteggCallback
import com.frontegg.android.utils.JWT
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.StorageProvider
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.ApiProvider
import com.frontegg.android.services.StepUpAuthenticatorProvider
import com.frontegg.android.services.StepUpAuthenticator
import com.frontegg.android.services.FronteggAppLifecycle
import com.frontegg.android.utils.NetworkGate
import com.frontegg.android.utils.RequestQueue
import io.mockk.mockkClass
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.mockkConstructor
import kotlin.time.DurationUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.toDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.runBlocking

class FronteggAuthServiceTest {
    private val storageMock = mockk<FronteggInnerStorage>()
    private val mockContext = mockk<Context>()
    private val mockActivity = mockk<Activity>()
    private lateinit var auth: FronteggAuthService
    private val credentialManagerMock = mockk<CredentialManager>()
    private val apiMock = mockk<Api>()
    private val authResponse = AuthResponse()
    private val stepUpAuthenticator = mockk<StepUpAuthenticator>()
    @Before
    fun setUp() {
        // FronteggState is a singleton shared across tests; reset to avoid test order dependence.
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
        every { storageMock.entitlementsEnabled }.returns(false)
        every { storageMock.tenantResolver }.returns(null)
        mockkObject(FronteggApp)
        every { credentialManagerMock.get(any()) }.returns(null)
        every { credentialManagerMock.context }.returns(mockContext)
        every { credentialManagerMock.setEnableSessionPerTenant(any()) } returns Unit
        
        // Mock Context methods needed by SessionTracker
        val mockSharedPreferences = mockk<android.content.SharedPreferences>(relaxed = true)
        val mockSharedPreferencesEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) }.returns(mockSharedPreferences)
        every { mockSharedPreferences.edit() }.returns(mockSharedPreferencesEditor)
        every { mockSharedPreferencesEditor.putString(any(), any()) } returns mockSharedPreferencesEditor
        val appLifecycle = mockk<FronteggAppLifecycle>()
        every { appLifecycle.startApp }.returns(FronteggCallback())
        every { appLifecycle.stopApp }.returns(FronteggCallback())

        val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>()
        every { refreshTokenTimer.refreshTokenIfNeeded }.returns(FronteggCallback())
        every { refreshTokenTimer.cancelLastTimer() }.returns(Unit)

        mockkObject(ApiProvider)
        every { ApiProvider.getApi(any()) }.returns(apiMock)
        every { apiMock.getServerUrl() }.returns("https://test.frontegg.com")

        mockkObject(StepUpAuthenticatorProvider)
        every {
            StepUpAuthenticatorProvider.getStepUpAuthenticator(
                any(),
            )
        } returns stepUpAuthenticator

        mockkObject(NetworkGate)
        every { NetworkGate.setFronteggBaseUrl(any()) } returns Unit
        every { NetworkGate.isNetworkLikelyGood(any()) } returns true

        mockkConstructor(RequestQueue::class)
        coEvery { anyConstructed<RequestQueue>().enqueue(any(), any(), any()) } returns Unit
        coEvery { anyConstructed<RequestQueue>().processAll() } returns 0
        every { anyConstructed<RequestQueue>().hasQueuedRequests() } returns false

        mockkConstructor(MutableLiveData::class)
        every { anyConstructed<MutableLiveData<Boolean>>().postValue(any()) } returns Unit
        every { anyConstructed<MutableLiveData<Boolean>>().value } returns false

        auth = FronteggAuthService(
            credentialManager = credentialManagerMock,
            appLifecycle = appLifecycle,
            refreshTokenTimer = refreshTokenTimer,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            disableAutoRefresh = false
        )

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        authResponse.id_token = "TestIdToken"
        authResponse.refresh_token = "TestRefreshToken"
        authResponse.access_token = "TestAccessToken"
        authResponse.token_type = "TestTokenToken"
    }

    @Test
    fun `login via EmbeddedAuthActivity if EmbeddedAuthActivity is enable`() {
        every { storageMock.isEmbeddedMode }.returns(true)

        mockkObject(EmbeddedAuthActivity)
        every { EmbeddedAuthActivity.authenticate(any(), any(), any(), any()) }.returns(Unit)
        auth.login(mockActivity, null, null)

        verify { EmbeddedAuthActivity.authenticate(any(), any(), any(), any()) }
    }

    @Test
    fun `login via AuthenticationActivity if EmbeddedAuthActivity is disable`() {
        every { storageMock.isEmbeddedMode }.returns(false)

        mockkObject(AuthenticationActivity)
        every { AuthenticationActivity.authenticate(any(), any(), any(), any()) }.returns(Unit)
        auth.login(mockActivity, null, null)

        verify { AuthenticationActivity.authenticate(any(), any(), any(), any()) }
    }

    @Test
    fun `sendRefreshToken should call Api_refreshToken and Api_me`() {
        auth.refreshToken.value = "TestRefreshToken"

        val user = User()

        every { apiMock.refreshToken(any()) }.returns(authResponse)
        every { apiMock.me() }.returns(user)

        every { credentialManagerMock.save(any(), any()) }.returns(true)

        mockkObject(JWTHelper)
        val jwt = JWT()
        every { JWTHelper.decode(any()) }.returns(jwt)

        val result = auth.sendRefreshToken()
        verify { apiMock.refreshToken(any()) }
        verify { apiMock.me() }
        assert(result)
    }

    @Test
    fun `sendRefreshToken should call CredentialManager_save`() {
        auth.refreshToken.value = "TestRefreshToken"

        val authResponse = AuthResponse()
        authResponse.id_token = "TestIdToken"
        authResponse.refresh_token = "TestRefreshToken"
        authResponse.access_token = "TestAccessToken"
        authResponse.token_type = "TestTokenToken"
        val user = User()

        every { apiMock.refreshToken(any()) }.returns(authResponse)
        every { apiMock.me() }.returns(user)

        every { credentialManagerMock.save(any(), any()) }.returns(true)

        mockkObject(JWTHelper)
        val jwt = JWT()
        every { JWTHelper.decode(any()) }.returns(jwt)

        val result = auth.sendRefreshToken()
        verify { credentialManagerMock.save(any(), any()) }
        assert(result)
    }

    @Test
    fun `sendRefreshToken should not call CredentialManager_save if Api_refreshToken throws exception`() {
        auth.refreshToken.value = "TestRefreshToken"  // Set refresh token first
        every { apiMock.refreshToken(any()) }.throws(Exception("Refresh token failed"))
        every { credentialManagerMock.save(any(), any()) }.returns(true)

        assertThrows<Exception> {
            auth.sendRefreshToken()
        }

        verify(exactly = 0) { credentialManagerMock.save(any(), any()) }
    }

    @Test
    fun `refreshTokenIfNeeded should work even when auto refresh is disabled`() {
        every { apiMock.refreshToken(any()) }.returns(authResponse)
        every { NetworkGate.isNetworkLikelyGood(any()) }.returns(true)
        every { credentialManagerMock.save(any(), any()) }.returns(true)
        every { apiMock.me() }.returns(mockk<User>())

        // Test that refreshTokenIfNeeded works regardless of disableAutoRefresh setting
        // because manual calls should always work
        val result = auth.refreshTokenIfNeeded()

        // Should return true because manual calls should work even when auto refresh is disabled
        assert(result)
    }

    @Test
    fun `logout should refresh FronteggAuth`() = runBlocking {
        val mockCookieManager = mockkClass(CookieManager::class)
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() }.returns(mockCookieManager)
        every { mockCookieManager.getCookie(any()) }.returns(null)
        every { credentialManagerMock.clear() }.returns(Unit)

        auth.logout()
        delay(100)
        assert(auth.accessToken.value == null)
        assert(auth.refreshToken.value == null)
        assert(auth.user.value == null)
        assert(!auth.isAuthenticated.value)
    }

    @Test
    fun `logout should call Api_logout if isEmbeddedMode and cookies is not empty`() = runBlocking {
        val mockCookieManager = mockkClass(CookieManager::class)
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() }.returns(mockCookieManager)
        every { mockCookieManager.getCookie(any()) }.returns("TestCoolies")
        every { credentialManagerMock.clear() }.returns(Unit)
        every { storageMock.isEmbeddedMode }.returns(true)

        auth.refreshToken.value = "TestRefreshToken"

        every { apiMock.logout(any<String>()) }.returns(Unit)

        auth.logout()

        delay(100)
        verify { apiMock.logout(any<String>()) }
    }

    @Test
    fun `handleHostedLoginCallback should return false if codeVerifier is null`() {
        every { stepUpAuthenticator.handleHostedLoginCallback(any()) } returns false
        every { credentialManagerMock.getCodeVerifier() }.returns(null)
        every { storageMock.packageName }.returns("dem.test.com")
        every { storageMock.useAssetsLinks }.returns(true)

        val result = auth.handleHostedLoginCallback("TestCode")

        assert(!result)
    }

    @Test
    fun `handleHostedLoginCallback should return true if codeVerifier is not null`() {
        every { stepUpAuthenticator.handleHostedLoginCallback(any()) } returns false
        every { credentialManagerMock.getCodeVerifier() }.returns("TestCodeVerifier")
        every { storageMock.packageName }.returns("dem.test.com")
        every { storageMock.useAssetsLinks }.returns(true)
        every { apiMock.exchangeToken(any(), any(), any()) }.returns(authResponse)

        every { credentialManagerMock.save(any(), any()) }.returns(false)

        val result = auth.handleHostedLoginCallback("TestCode")
        assert(result)
    }

    @Test
    fun `handleHostedLoginCallback should call callback if data is not null`() = runBlocking {
        every { credentialManagerMock.getCodeVerifier() }.returns("TestCodeVerifier")
        every { storageMock.packageName }.returns("dem.test.com")
        every { storageMock.useAssetsLinks }.returns(true)

        every { stepUpAuthenticator.handleHostedLoginCallback(any()) } returns false
        var called = false

        every { apiMock.exchangeToken(any(), any(), any()) }.returns(authResponse)

        every { credentialManagerMock.save(any(), any(), any()) }.returns(true)
        every { apiMock.me() }.returns(mockk<User>())

        val result = auth.handleHostedLoginCallback("TestCode", callback = { called = true })
        delay(200) // Wait for coroutine to complete
        assert(result)
        assert(called)
    }

    @Test
    fun `handleHostedLoginCallback should call login if data is null and activity is not null and callback is null`() {
        every { credentialManagerMock.getCodeVerifier() }.returns("TestCodeVerifier")
        every { credentialManagerMock.saveCodeVerifier(any()) }.returns(true)

        every { storageMock.packageName }.returns("dem.test.com")
        every { storageMock.useAssetsLinks }.returns(true)

        every { stepUpAuthenticator.handleHostedLoginCallback(any()) } returns false

        every { apiMock.exchangeToken(any(), any(), any()) }.returns(null)

        val mockAuthorizeUrl = mockkClass(AuthorizeUrlGenerator::class)
        every { mockAuthorizeUrl.generate(any(), any(), any()) }.returns(Pair("First", "Second"))

        mockkObject(AuthorizeUrlGeneratorProvider)
        every { AuthorizeUrlGeneratorProvider.getAuthorizeUrlGenerator(any()) }.returns(mockAuthorizeUrl)

        every { storageMock.isEmbeddedMode }.returns(true)
        mockkObject(EmbeddedAuthActivity)
        every { EmbeddedAuthActivity.authenticate(any(), any(), any(), any()) }.returns(Unit)

        val result = auth.handleHostedLoginCallback("TestCode", activity = mockActivity)

        verify(timeout = 1_000) { EmbeddedAuthActivity.authenticate(any(), any(), any(), any()) }
        assert(result)
    }

    @Test
    fun `directLoginAction should call EmbeddedAuthActivity_directLoginAction if is isEmbeddedMode`() {
        every { storageMock.isEmbeddedMode }.returns(true)

        mockkObject(EmbeddedAuthActivity)
        every { EmbeddedAuthActivity.directLoginAction(any(), any(), any(), any()) }.returns(Unit)

        auth.directLoginAction(mockActivity, "", "")

        verify { EmbeddedAuthActivity.directLoginAction(any(), any(), any(), any()) }
    }

    @Test
    fun `directLoginAction should call Log_w if is not isEmbeddedMode`() {
        every { storageMock.isEmbeddedMode }.returns(false)
        every { Log.w(any(), any<String>()) }.returns(0)
        auth.directLoginAction(mockActivity, "", "")

        verify { Log.w(any(), any<String>()) }
    }

    @Test
    fun `switchTenant should call Api_switchTenant`() {
        every { apiMock.switchTenant(any()) }.returns(Unit)

        auth.switchTenant("TestTenantId")

        verify(timeout = 1_000) { apiMock.switchTenant(any()) }
    }

    @Test
    fun `switchTenant forces a token refresh even when the existing access token is still valid by TTL`() {
        // Regression: Frontegg access tokens are tenant-bound (tenantId lives in the JWT
        // claims), so any tenant switch must re-mint tokens. Pre-fix, switchTenant fell
        // through to refreshIdempotent's skip-if-not-expired guard
        // (FronteggAuthService.kt:377-386). When the existing access token still had time
        // on its TTL the guard returned success without actually refreshing, leaving
        // accessToken.value pinned to the OLD tenant's JWT. Subsequent calls to
        // loadEntitlements(forceRefresh=true) sent the stale token to the entitlements
        // API, which reads tenant from the JWT and returned the previous tenant's
        // entitlement set — so getFeatureEntitlements("sso") kept reporting
        // isEntitled=true even after a switch to a tenant that did not have SSO. The
        // state only self-healed when the access token's TTL elapsed (~45s) and
        // auto-refresh fired.
        //
        // Expected (post-fix): switchTenant always issues a real refresh so the access
        // token's tenantId claim matches the newly-switched tenant.

        every { storageMock.enableSessionPerTenant }.returns(false)

        // Existing session: access token whose `exp` is well in the future — this is the
        // exact condition under which the skip-if-not-expired guard would have fired.
        auth.accessToken.value = "stale-tenant-A-access-token"
        auth.refreshToken.value = "current-refresh-token"

        mockkObject(JWTHelper)
        val jwt = JWT()
        jwt.exp = (System.currentTimeMillis() / 1000) + 3600 // 1 hour in the future
        every { JWTHelper.decode(any()) }.returns(jwt)

        // Server-side switch succeeds.
        every { apiMock.switchTenant(any()) }.returns(Unit)
        every { apiMock.evictIdleConnections() }.returns(Unit)

        // Server mints tenant-B-bound tokens on the next refresh.
        val tenantBAuth = AuthResponse().apply {
            id_token = "id-token-for-B"
            refresh_token = "refresh-token-for-B"
            access_token = "access-token-for-B"
            token_type = "Bearer"
        }
        every { apiMock.refreshToken(any()) }.returns(tenantBAuth)

        val tenantB = Tenant().apply { id = "tenant-B"; tenantId = "tenant-B" }
        val user = User().apply {
            id = "user-1"
            tenants = listOf(tenantB)
            activeTenant = tenantB
        }
        every { apiMock.me() }.returns(user)

        every { credentialManagerMock.save(any(), any()) }.returns(true)
        every { credentialManagerMock.save(any(), any(), any()) }.returns(true)
        every { credentialManagerMock.getCurrentTenantId() }.returns(null)
        every { Log.w(any(), any<String>()) } returns 0

        auth.switchTenant("tenant-B")

        // Pre-fix: 0 calls (skip-if-not-expired guard hit, no real refresh). Post-fix: 1.
        verify(timeout = 2_000, exactly = 1) { apiMock.refreshToken("current-refresh-token") }

        // And the freshly-minted tenant-B token must replace the stale tenant-A one in memory.
        assert(auth.accessToken.value == "access-token-for-B") {
            "accessToken.value must reflect the freshly-issued tenant-B token, was ${auth.accessToken.value}"
        }
    }

    @Test
    fun `loginWithPasskeys should call Api_webAuthnPrelogin`() {
        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.webAuthnPrelogin() }.returns(request)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        auth.loginWithPasskeys(mockActivity)
        verify(timeout = 1_000) { apiMock.webAuthnPrelogin() }
    }

    @Test
    fun `loginWithPasskeys should call CredentialManagerHandler_getPasskey`() = runBlocking {
        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.webAuthnPrelogin() }.returns(request)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        val response = GetCredentialResponse(
            PublicKeyCredential(
                authenticationResponseJson = "{}",
            )
        )

        coEvery { credentialManagerMockHandler.getPasskey(any()) }.returns(response)

        auth.loginWithPasskeys(mockActivity)
        
        // Wait a bit for the coroutine to complete
        delay(100)
        
        coVerify { credentialManagerMockHandler.getPasskey(any()) }
    }

    @Test
    fun `loginWithPasskeys should call Api_webAuthnPostlogin`() {
        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        val response = GetCredentialResponse(
            credential = PublicKeyCredential(
                authenticationResponseJson = "{}",
            )
        )
        every { apiMock.webAuthnPostlogin(any(), any()) }.returns(authResponse)
        every { apiMock.webAuthnPrelogin() }.returns(request)
        every { apiMock.toString() }.returns("")


        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        coEvery { credentialManagerMockHandler.getPasskey(any()) }.returns(response)

        auth.loginWithPasskeys(mockActivity)
        verify(timeout = 1_000) { apiMock.webAuthnPostlogin(any(), any()) }
    }

    @Test
    fun `loginWithPasskeys should call setCredentials`() {
        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        val response = GetCredentialResponse(
            credential = PublicKeyCredential(
                authenticationResponseJson = "{}",
            )
        )
        every { apiMock.webAuthnPostlogin(any(), any()) }.returns(authResponse)
        every { apiMock.webAuthnPrelogin() }.returns(request)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        coEvery { credentialManagerMockHandler.getPasskey(any()) }.returns(response)

        every { credentialManagerMock.save(any(), any()) }.returns(true)

        auth.loginWithPasskeys(mockActivity)

        verify(timeout = 1_000) {
            credentialManagerMock.save(
                CredentialKeys.REFRESH_TOKEN,
                "TestRefreshToken"
            )
        }
        verify(timeout = 1_000) {
            credentialManagerMock.save(
                CredentialKeys.ACCESS_TOKEN,
                "TestAccessToken"
            )
        }
    }

    @Test
    fun `loginWithPasskeys should call callback with null Exception`() = runBlocking {
        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        val response = GetCredentialResponse(
            credential = PublicKeyCredential(
                authenticationResponseJson = "{}",
            )
        )
        every { apiMock.webAuthnPostlogin(any(), any()) }.returns(authResponse)
        every { apiMock.webAuthnPrelogin() }.returns(request)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        coEvery { credentialManagerMockHandler.getPasskey(any()) }.returns(response)

        every { credentialManagerMock.save(any(), any()) }.returns(true)
        every { apiMock.me() }.returns(mockk<User>())

        var called = false
        var exception: Exception? = null
        auth.loginWithPasskeys(mockActivity, callback = {
            called = true
            exception = it
        })

        delay(200) // Wait for coroutine to complete
        assert(called)
        assert(exception == null)
    }

    @Test
    fun `loginWithPasskeys should call callback with Exception if some error occurred`() = runBlocking {
        every { apiMock.webAuthnPostlogin(any(), any()) }.throws(Exception())

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        var called = false
        var exception: Exception? = null
        auth.loginWithPasskeys(mockActivity, callback = {
            called = true
            exception = it
        })

        delay(200) // Wait for coroutine to complete
        assert(called)
        assert(exception != null)
    }

    @Test
    fun `registerPasskeys should call Api_getWebAuthnRegisterChallenge`() = runBlocking {
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.getWebAuthnRegisterChallenge() }.returns(request)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        auth.registerPasskeys(mockActivity)
        verify(timeout = 1_000) { apiMock.getWebAuthnRegisterChallenge() }
    }

    @Test
    fun `registerPasskeys should call CredentialManagerHandler_createPasskey`() = runBlocking {
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.getWebAuthnRegisterChallenge() }.returns(request)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        val response = CreatePublicKeyCredentialResponse(
            registrationResponseJson = "{}"
        )
        coEvery { credentialManagerMockHandler.createPasskey(any()) }.returns(response)

        auth.registerPasskeys(mockActivity)
        
        // Wait a bit for the coroutine to complete
        delay(100)
        
        coVerify { credentialManagerMockHandler.createPasskey(any()) }
    }

    @Test
    fun `registerPasskeys should call Api_verifyWebAuthnDevice`() = runBlocking {
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.getWebAuthnRegisterChallenge() }.returns(request)
        every { apiMock.verifyWebAuthnDevice(any(), any()) }.returns(Unit)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        val response = CreatePublicKeyCredentialResponse(
            registrationResponseJson = "{}"
        )
        coEvery { credentialManagerMockHandler.createPasskey(any()) }.returns(response)

        auth.registerPasskeys(mockActivity)
        verify(timeout = 1_000) { apiMock.verifyWebAuthnDevice(any(), any()) }
    }

    @Test
    fun `registerPasskeys should call call callback with null Exception`() = runBlocking {
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.getWebAuthnRegisterChallenge() }.returns(request)
        every { apiMock.verifyWebAuthnDevice(any(), any()) }.returns(Unit)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        val response = CreatePublicKeyCredentialResponse(
            registrationResponseJson = "{}"
        )
        coEvery { credentialManagerMockHandler.createPasskey(any()) }.returns(response)

        var called = false
        var exception: Exception? = null
        auth.registerPasskeys(mockActivity, callback = {
            called = true
            exception = it
        })

        // Wait for the coroutine to complete
        delay(1000)
        assert(called)
        assert(exception == null)
    }

    @Test
    fun `registerPasskeys should call call callback with Exception if some error occurred`() = runBlocking {
        every { apiMock.getWebAuthnRegisterChallenge() }.throws(Exception())

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(Dispatchers.Unconfined))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        var called = false
        var exception: Exception? = null
        auth.registerPasskeys(mockActivity, callback = {
            called = true
            exception = it
        })

        // Wait for the coroutine to complete
        delay(1000)
        assert(called)
        assert(exception != null)
    }

    @Test
    fun `isSteppedUp calls stepUpAuthenticator and returns result`() {
        every { stepUpAuthenticator.isSteppedUp(any()) } returns true

        val duration = 5.toDuration(DurationUnit.MINUTES)
        assert(auth.isSteppedUp(duration))
        verify { stepUpAuthenticator.isSteppedUp(duration) }
    }

    @Test
    fun `stepUp calls stepUpAuthenticator`() {
        coEvery { stepUpAuthenticator.stepUp(any(), any(), any()) } returns Unit

        val duration = 5.toDuration(DurationUnit.MINUTES)
        auth.stepUp(mockActivity, duration, null)
        coVerify { stepUpAuthenticator.stepUp(any(), any(), any()) }
    }

    @Test
    fun `initializeSubscriptions preserves session when network probe transiently fails on cold start`() = runBlocking {
        // Reproduces customer-reported bug: app backgrounded 5+ hours, FCM cold-start triggers
        // initializeSubscriptions while the radio is still resuming from doze. The synchronous
        // network-quality probe (HEAD /<base>/test) returns false for ~one cycle, even though
        // the device is online and the refresh token is valid.
        //
        // With enableOfflineMode = false (default), FronteggAuthService.kt:1216-1230 currently
        // calls clearCredentials() on a single failed probe — wiping a perfectly good session.
        //
        // Expected (post-fix): a transient probe failure must NOT destroy the session. Tokens
        // remain on disk and in memory; the SDK proceeds to /me / refresh validation.
        // This test currently FAILS and is the reproduction for the bug.

        // Logged-in state: tokens were persisted on disk by a previous successful login.
        every { credentialManagerMock.get(CredentialKeys.ACCESS_TOKEN, null) } returns "saved-access-token"
        every { credentialManagerMock.get(CredentialKeys.REFRESH_TOKEN, null) } returns "saved-refresh-token"
        every { credentialManagerMock.clear() } returns Unit
        every { credentialManagerMock.clear(any()) } returns Unit
        every { credentialManagerMock.clearOfflineUser() } returns true
        every { credentialManagerMock.getOfflineUser() } returns null
        every { credentialManagerMock.saveLastTenantIdForUser(any(), any()) } returns true
        every { credentialManagerMock.setCurrentTenantId(any()) } returns true
        every { credentialManagerMock.save(any(), any()) } returns true
        every { credentialManagerMock.save(any(), any(), any()) } returns true

        // The trigger: probe returns false (cached radio state during wake-from-doze).
        every { NetworkGate.isNetworkLikelyGood(any()) } returns false

        // The actual /me call would succeed if the SDK didn't short-circuit on the probe.
        val user = User()
        every { apiMock.me() } returns user
        mockkObject(JWTHelper)
        val jwt = JWT()
        jwt.exp = (System.currentTimeMillis() / 1000) + 3600
        every { JWTHelper.decode(any()) } returns jwt

        auth.initializeSubscriptions()
        delay(300) // bgScope.launch on Dispatchers.Unconfined; small slack for safety

        // Post-fix expectations: session is preserved, credentials NOT wiped.
        assert(auth.accessToken.value == "saved-access-token") {
            "Access token should remain in memory after a transient probe failure, was ${auth.accessToken.value}"
        }
        assert(auth.refreshToken.value == "saved-refresh-token") {
            "Refresh token should remain in memory after a transient probe failure, was ${auth.refreshToken.value}"
        }
        assert(auth.isAuthenticated.value) {
            "isAuthenticated should remain true on a transient probe failure with valid stored tokens — currently flips to false because clearCredentials() is called"
        }
        verify(exactly = 0) { credentialManagerMock.clear(any()) }
    }

    @Test
    fun `setCredentials does not reconcile to cached per-user tenant on fresh post-logout login`() {
        // FR-24632: same human / same user.id with memberships in tenants A and B.
        // Customer logs in under A, logs out, logs back in under B (e.g. via
        // organization param or a tenant-specific login URL). After logout,
        // last_tenant_id_user_<id> is intentionally preserved on disk
        // (CredentialManager.kt:152-167). Pre-fix, setCredentials's reconciliation
        // (FronteggAuthService.kt:773-801) saw initialTenantId=null + cachedTenantId=A
        // valid for the user, picked A as desiredTenantId, and called
        // api.switchTenant(A) + api.refreshToken — pinning state back to A even
        // though the user just logged in under B. The customer's app then called
        // switchTenant(B) to correct, which fell through to refreshIdempotent and
        // hit the v1.3.23 skip-if-not-expired guard, so state stayed on A until
        // the access token expired (~45s) and auto-refresh fired.
        //
        // Expected (post-fix): a fresh login lands on the server's active tenant.
        // The "remember last tenant" cache is appropriate for cold-start with
        // persisted tokens (handled by initialTenantId != null), not for fresh
        // post-logout logins where the user has just made an explicit choice.

        every { storageMock.enableSessionPerTenant }.returns(true)

        // Post-logout state: in-memory tokens cleared, currentTenantId cleared,
        // but per-user "last tenant" preserved (== "tenant-A").
        every { credentialManagerMock.getCurrentTenantId() }.returns(null) andThen "tenant-B"
        every { credentialManagerMock.getLastTenantIdForUser("user-1") }.returns("tenant-A")
        every { credentialManagerMock.setCurrentTenantId(any()) }.returns(true)
        every { credentialManagerMock.saveLastTenantIdForUser(any(), any()) }.returns(true)
        every { credentialManagerMock.save(any(), any()) }.returns(true)
        every { credentialManagerMock.save(any(), any(), any()) }.returns(true)

        // The user just logged in under tenant-B; the server confirms that.
        val tenantA = Tenant().apply { id = "tenant-A"; tenantId = "tenant-A" }
        val tenantB = Tenant().apply { id = "tenant-B"; tenantId = "tenant-B" }
        val user = User().apply {
            id = "user-1"
            tenants = listOf(tenantA, tenantB)
            activeTenant = tenantB
        }
        every { apiMock.me() }.returns(user)

        mockkObject(JWTHelper)
        val jwt = JWT()
        jwt.exp = (System.currentTimeMillis() / 1000) + 3600
        every { JWTHelper.decode(any()) }.returns(jwt)

        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        // Drives setCredentials(access, refresh) through the public surface.
        auth.updateCredentials("access-token-for-B", "refresh-token-for-B")

        // No spurious reconciliation: the SDK must not yank the user back to the cached tenant.
        verify(exactly = 0) { apiMock.switchTenant(any()) }
        verify(exactly = 0) { apiMock.refreshToken(any()) }

        // State reflects the server's active tenant (B), not the cached tenant (A).
        verify { credentialManagerMock.setCurrentTenantId("tenant-B") }
        assert(auth.user.value?.activeTenant?.tenantId == "tenant-B") {
            "user.activeTenant should be tenant-B (server's active tenant), was ${auth.user.value?.activeTenant?.tenantId}"
        }
        assert(auth.accessToken.value == "access-token-for-B") {
            "accessToken.value should be the freshly-minted token for tenant-B, was ${auth.accessToken.value}"
        }
    }
}