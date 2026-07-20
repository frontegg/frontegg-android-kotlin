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
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.embedded.CredentialManagerHandler
import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.EntitlementState
import com.frontegg.android.models.Tenant
import com.frontegg.android.models.User
import com.frontegg.android.models.WebAuthnAssertionRequest
import com.frontegg.android.models.WebAuthnRegistrationRequest
import com.frontegg.android.testUtils.BlockCoroutineDispatcher
import com.frontegg.android.testUtils.QueueingCoroutineDispatcher
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

    /**
     * Regression for the token-refresh-timer death (reported by Retail Success — Dev env,
     * 5-minute token TTL).
     *
     * The refresh timer is scheduled at 80% of TTL, so it fires with ~20% of the TTL still
     * on the clock. The "already refreshed by concurrent call" guard in [refreshIdempotent]
     * used to key off *remaining TTL* (`calculateTimerOffset() > 0`, i.e. > 20s left) instead
     * of *token identity*. For any TTL > 100s, 20% of TTL is still > 20s, so the timer mistook
     * the very token it was scheduled for as a concurrent refresh, skipped the network call,
     * and — because the next timer is only ever rescheduled inside sendRefreshTokenInternal —
     * never rescheduled. Auto-refresh died silently and the session expired.
     *
     * A 5-minute (300s) token that is NOT changed by anyone else must actually refresh and
     * reschedule the timer.
     */
    @Test
    fun `auto refresh does not skip a long-TTL token as a phantom concurrent refresh`() = runBlocking {
        val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>()
        every { refreshTokenTimer.refreshTokenIfNeeded }.returns(FronteggCallback())
        every { refreshTokenTimer.cancelLastTimer() }.returns(Unit)
        every { refreshTokenTimer.scheduleTimer(any()) }.returns(Unit)
        val appLifecycle = mockk<FronteggAppLifecycle>()
        every { appLifecycle.startApp }.returns(FronteggCallback())
        every { appLifecycle.stopApp }.returns(FronteggCallback())
        auth = FronteggAuthService(
            credentialManager = credentialManagerMock,
            appLifecycle = appLifecycle,
            refreshTokenTimer = refreshTokenTimer,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            disableAutoRefresh = false
        )

        auth.accessToken.value = "current-access-token"
        auth.refreshToken.value = "current-refresh-token"
        auth.isAuthenticated.value = true

        mockkObject(JWTHelper)
        val jwt = JWT()
        jwt.exp = (System.currentTimeMillis() / 1000) + 300 // 5-minute TTL, well above the 100s threshold
        every { JWTHelper.decode(any()) }.returns(jwt)

        every { NetworkGate.isNetworkLikelyGood(any()) }.returns(true)
        every { apiMock.evictIdleConnections() }.returns(Unit)
        every { apiMock.refreshToken("current-refresh-token") }.returns(
            AuthResponse().apply {
                access_token = "new-access-token"
                refresh_token = "new-refresh-token"
            }
        )
        every { apiMock.me() }.returns(mockk<User>(relaxed = true))
        every { credentialManagerMock.save(any(), any()) }.returns(true)

        val result = auth.refreshTokenAndWaitInternal(
            source = FronteggAuthService.RefreshInvocationSource.INTERNAL_AUTO
        )

        assert(result) { "auto refresh must report success, was $result" }
        // The refresh must hit the network instead of being skipped as a phantom concurrent refresh.
        verify(exactly = 1) { apiMock.refreshToken("current-refresh-token") }
        // And the next timer must be scheduled so auto-refresh keeps looping.
        verify(atLeast = 1) { refreshTokenTimer.scheduleTimer(any()) }
    }

    @Test
    fun `wasRefreshedConcurrently is false when the token is unchanged since the refresh was requested`() {
        // The core of the bug: a token that nobody else touched must NOT be treated as
        // "already refreshed" — otherwise the scheduled refresh is skipped forever.
        assert(!auth.wasRefreshedConcurrently("same-token", "same-token"))
    }

    @Test
    fun `wasRefreshedConcurrently is true when another caller swapped the token while we waited`() {
        // Genuine concurrent refresh: the live token differs from the one we snapshotted
        // before taking the mutex, so we correctly skip our redundant network call.
        assert(auth.wasRefreshedConcurrently("token-at-request", "token-from-concurrent-refresh"))
    }

    @Test
    fun `wasRefreshedConcurrently is false when there was no token to compare against`() {
        // No baseline (cold start / logged-out) means there is nothing to dedup against;
        // never skip on a null token.
        assert(!auth.wasRefreshedConcurrently(null, "any-token"))
        assert(!auth.wasRefreshedConcurrently("token-at-request", null))
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
        every { mockCookieManager.setCookie(any(), any<String>()) } returns Unit
        every { mockCookieManager.flush() } returns Unit
        every { credentialManagerMock.clear() }.returns(Unit)
        every { storageMock.isEmbeddedMode }.returns(true)

        auth.refreshToken.value = "TestRefreshToken"

        every { apiMock.logout(any<String>()) }.returns(Unit)

        auth.logout()

        delay(100)
        verify { apiMock.logout(any<String>()) }
    }

    @Test
    fun `logout expires fe_refresh cookies but leaves other cookies alone`() = runBlocking {
        // Setup: cookies for baseUrl contain two fe_refresh entries plus an
        // unrelated cookie. After logout, only the fe_refresh ones should be
        // expired (Max-Age=0). The unrelated cookie must not be touched.
        val mockCookieManager = mockkClass(CookieManager::class)
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() }.returns(mockCookieManager)
        every {
            mockCookieManager.getCookie(any())
        }.returns("fe_refresh_TestClientId=value1; some_other=keep_me; fe_refresh_AppX=value2")
        every { mockCookieManager.setCookie(any(), any<String>()) } returns Unit
        every { mockCookieManager.flush() } returns Unit
        every { credentialManagerMock.clear() }.returns(Unit)
        every { credentialManagerMock.clearAllTokens() } returns Unit
        every { credentialManagerMock.clearOfflineUser() } returns true
        every { credentialManagerMock.setCurrentTenantId(any()) } returns true
        every { storageMock.isEmbeddedMode } returns false

        auth.logout()
        delay(100)

        verify(atLeast = 1) { mockCookieManager.getCookie(any()) }
        verify(exactly = 1) {
            mockCookieManager.setCookie(
                any(),
                match<String> { it.startsWith("fe_refresh_TestClientId=") && it.contains("Max-Age=0") }
            )
        }
        verify(exactly = 1) {
            mockCookieManager.setCookie(
                any(),
                match<String> { it.startsWith("fe_refresh_AppX=") && it.contains("Max-Age=0") }
            )
        }
        verify(exactly = 0) {
            mockCookieManager.setCookie(
                any(),
                match<String> { it.startsWith("some_other=") }
            )
        }
        verify { mockCookieManager.flush() }
    }

    @Test
    fun `logout no-ops cookie cleanup when no fe_refresh cookies are present`() = runBlocking {
        val mockCookieManager = mockkClass(CookieManager::class)
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() }.returns(mockCookieManager)
        every {
            mockCookieManager.getCookie(any())
        }.returns("some_other=keep_me; unrelated=also_keep")
        every { mockCookieManager.setCookie(any(), any<String>()) } returns Unit
        every { mockCookieManager.flush() } returns Unit
        every { credentialManagerMock.clear() }.returns(Unit)
        every { credentialManagerMock.clearAllTokens() } returns Unit
        every { credentialManagerMock.clearOfflineUser() } returns true
        every { credentialManagerMock.setCurrentTenantId(any()) } returns true
        every { storageMock.isEmbeddedMode } returns false

        auth.logout()
        delay(100)

        // No fe_refresh cookies → no setCookie calls and no flush.
        verify(exactly = 0) { mockCookieManager.setCookie(any(), any<String>()) }
        verify(exactly = 0) { mockCookieManager.flush() }
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
    fun `switchTenant stale credentials regression while access token TTL is still valid`() {
        // Customer regression (SDK > 1.0.20): switchTenant/await returned success but
        // accessToken and entitlements still reflected the previous tenant for ~45s until
        // the JWT expired and auto-refresh ran. Pre-fix: refresh was skipped when exp was
        // still in the future, or (session-per-tenant) cached tokens were reused without
        // OAuth refresh after api.switchTenant.
        //
        // This test fails pre-fix at verify (0 refresh calls) or at assertNotEquals (stale
        // token still in memory). It passes once switchTenant always re-mints tokens.

        every { storageMock.enableSessionPerTenant }.returns(false)

        val staleAccessToken = "stale-tenant-A-access-token"
        auth.accessToken.value = staleAccessToken
        auth.refreshToken.value = "current-refresh-token"

        mockkObject(JWTHelper)
        val jwt = JWT()
        jwt.exp = (System.currentTimeMillis() / 1000) + 45
        every { JWTHelper.decode(any()) }.returns(jwt)

        every { apiMock.switchTenant("tenant-B") }.returns(Unit)
        every { apiMock.evictIdleConnections() }.returns(Unit)

        val tenantBAuth = AuthResponse().apply {
            access_token = "access-token-for-B"
            refresh_token = "refresh-token-for-B"
        }
        every { apiMock.refreshToken("current-refresh-token") }.returns(tenantBAuth)

        val tenantB = Tenant().apply { id = "tenant-B"; tenantId = "tenant-B" }
        every { apiMock.me() }.returns(
            User().apply {
                id = "user-1"
                tenants = listOf(tenantB)
                activeTenant = tenantB
            }
        )

        every { credentialManagerMock.save(any(), any()) }.returns(true)
        every { credentialManagerMock.save(any(), any(), any()) }.returns(true)
        every { credentialManagerMock.getCurrentTenantId() }.returns(null)
        every { Log.w(any(), any<String>()) } returns 0

        auth.switchTenant("tenant-B")

        verify(timeout = 2_000, exactly = 1) { apiMock.refreshToken("current-refresh-token") }
        assert(auth.accessToken.value == "access-token-for-B") {
            "accessToken must be the freshly minted tenant-B token, was ${auth.accessToken.value}"
        }
        assert(auth.accessToken.value != staleAccessToken) {
            "accessToken must not remain the pre-switch tenant-A JWT after switchTenant completes"
        }
        assert(auth.user.value?.activeTenant?.tenantId == "tenant-B") {
            "activeTenant must be tenant-B after switchTenant, was ${auth.user.value?.activeTenant?.tenantId}"
        }
    }

    @Test
    fun `switchTenant session per tenant refreshes the LIVE re-scoped token, not the stored one`() {
        // With enableSessionPerTenant, switchTenant must still OAuth-refresh (not reuse a
        // TTL-valid token) — that was #252's fix. But it must refresh the LIVE token that
        // api.switchTenant just re-scoped, NOT a per-tenant *stored* refresh token.
        //
        // Frontegg refresh tokens are single-use / rotating. A stored per-tenant refresh
        // token was already consumed the first time we switched away from that tenant, so
        // restoring + refreshing it 401s on the 2nd+ switch back ("Refresh token is not
        // valid"). See `switchTenant repeated switches…` below for the multi-switch repro;
        // this test pins the single-switch contract: the live token is refreshed and the
        // stored token is NOT touched.
        every { storageMock.enableSessionPerTenant }.returns(true)

        val tenantA = Tenant().apply { id = "tenant-A"; tenantId = "tenant-A" }
        val tenantB = Tenant().apply { id = "tenant-B"; tenantId = "tenant-B" }
        val userA = User().apply {
            id = "user-1"
            tenants = listOf(tenantA, tenantB)
            activeTenant = tenantA
        }
        auth.user.value = userA
        auth.accessToken.value = "stale-tenant-A-access-token"
        auth.refreshToken.value = "live-refresh-token"

        every { apiMock.switchTenant(any()) }.returns(Unit)
        every { apiMock.evictIdleConnections() }.returns(Unit)
        every { credentialManagerMock.setCurrentTenantId(any()) }.returns(true)
        // A stale stored refresh token exists for tenant-B; the SDK must NOT use it.
        every { credentialManagerMock.get(CredentialKeys.ACCESS_TOKEN, "tenant-B") }.returns("stale-stored-tenant-B-access")
        every { credentialManagerMock.get(CredentialKeys.REFRESH_TOKEN, "tenant-B") }.returns("stale-stored-tenant-B-refresh")
        every { credentialManagerMock.save(any(), any()) }.returns(true)
        every { credentialManagerMock.save(any(), any(), any()) }.returns(true)
        every { credentialManagerMock.getCurrentTenantId() }.returns("tenant-B")
        every { credentialManagerMock.saveLastTenantIdForUser(any(), any()) }.returns(true)

        // api.switchTenant re-scoped the live session to tenant-B, so refreshing the LIVE
        // token yields tenant-B's tokens.
        val tenantBAuth = AuthResponse().apply {
            access_token = "fresh-tenant-B-access-token"
            refresh_token = "fresh-tenant-B-refresh-token"
        }
        every { apiMock.refreshToken("live-refresh-token") }.returns(tenantBAuth)

        val userB = User().apply {
            id = "user-1"
            tenants = listOf(tenantA, tenantB)
            activeTenant = tenantB
        }
        every { apiMock.me() }.returns(userB)

        mockkObject(JWTHelper)
        val jwt = JWT()
        jwt.exp = (System.currentTimeMillis() / 1000) + 3600
        every { JWTHelper.decode(any()) }.returns(jwt)
        every { Log.w(any(), any<String>()) } returns 0

        auth.switchTenant("tenant-B")

        // Refreshes the LIVE token...
        verify(timeout = 2_000, exactly = 1) { apiMock.refreshToken("live-refresh-token") }
        // ...and never the stale stored one.
        verify(exactly = 0) { apiMock.refreshToken("stale-stored-tenant-B-refresh") }
        verify(timeout = 2_000, exactly = 1) { apiMock.me() }
        assert(auth.accessToken.value == "fresh-tenant-B-access-token") {
            "accessToken.value must be the token returned by refreshing the live token, was ${auth.accessToken.value}"
        }
    }

    @Test
    fun `switchTenant repeated switches refresh the live token and never reuse a rotated stored token`() {
        // Regression for the FR-24821 follow-up Pavel hit: the SECOND switch failed with
        // 401 "Refresh token is not valid". Cause: per-tenant stored refresh tokens are
        // single-use; switching away from a tenant consumes its refresh token (rotation),
        // and switching back restored + refreshed that now-dead stored token.
        //
        // This test models single-use rotation faithfully (reusing any refresh token
        // throws FailedToAuthenticateException, exactly as the server 401s) and performs
        // two consecutive switches. Pre-fix the 2nd switch re-refreshes the consumed
        // tenant-A token and fails; post-fix each switch refreshes the LIVE token and both
        // succeed.
        every { storageMock.enableSessionPerTenant }.returns(true)

        val tenantA = Tenant().apply { id = "tenant-A"; tenantId = "tenant-A" }
        val tenantB = Tenant().apply { id = "tenant-B"; tenantId = "tenant-B" }
        fun userOn(active: Tenant) = User().apply {
            id = "user-1"
            tenants = listOf(tenantA, tenantB)
            activeTenant = active
        }

        auth.user.value = userOn(tenantA)
        auth.accessToken.value = "AT0"
        auth.refreshToken.value = "RT0"

        every { apiMock.switchTenant(any()) }.returns(Unit)
        every { apiMock.evictIdleConnections() }.returns(Unit)
        every { credentialManagerMock.setCurrentTenantId(any()) }.returns(true)
        every { credentialManagerMock.save(any(), any()) }.returns(true)
        every { credentialManagerMock.save(any(), any(), any()) }.returns(true)
        every { credentialManagerMock.getCurrentTenantId() }.returns(null)
        every { credentialManagerMock.saveLastTenantIdForUser(any(), any()) }.returns(true)
        // Stale stored refresh tokens exist for BOTH tenants — the bug restored and
        // refreshed them. Stub them so that IF the buggy code reads them, the
        // single-use model below will 401 on reuse.
        every { credentialManagerMock.get(CredentialKeys.ACCESS_TOKEN, "tenant-A") }.returns("AT0")
        every { credentialManagerMock.get(CredentialKeys.REFRESH_TOKEN, "tenant-A") }.returns("RT0")
        every { credentialManagerMock.get(CredentialKeys.ACCESS_TOKEN, "tenant-B") }.returns(null)
        every { credentialManagerMock.get(CredentialKeys.REFRESH_TOKEN, "tenant-B") }.returns(null)

        // Single-use / rotating refresh tokens: reusing one throws, exactly like the
        // server's 401 "Refresh token is not valid".
        val consumed = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        every { apiMock.refreshToken(any()) }.answers {
            val token = firstArg<String>()
            if (!consumed.add(token)) {
                throw FailedToAuthenticateException(error = "Refresh token is not valid")
            }
            when (token) {
                "RT0" -> AuthResponse().apply { access_token = "AT1-tenantB"; refresh_token = "RT1" }
                "RT1" -> AuthResponse().apply { access_token = "AT2-tenantA"; refresh_token = "RT2" }
                else -> throw FailedToAuthenticateException(error = "Refresh token is not valid")
            }
        }

        // me() returns the user scoped to whichever tenant the switch targets, in order.
        every { apiMock.me() }.returnsMany(listOf(userOn(tenantB), userOn(tenantA)))

        mockkObject(JWTHelper)
        val jwt = JWT()
        jwt.exp = (System.currentTimeMillis() / 1000) + 3600
        every { JWTHelper.decode(any()) }.returns(jwt)
        every { Log.w(any(), any<String>()) } returns 0

        // Switch 1: A → B. Refreshes live RT0 → AT1.
        auth.switchTenant("tenant-B")
        verify(timeout = 2_000, exactly = 1) { apiMock.refreshToken("RT0") }
        assert(auth.accessToken.value == "AT1-tenantB") {
            "after switch 1, accessToken must be AT1-tenantB, was ${auth.accessToken.value}"
        }

        // Switch 2: B → A. Must refresh the LIVE token (RT1), NOT re-refresh the consumed
        // RT0. Pre-fix this refreshed RT0 again → FailedToAuthenticateException → switch
        // fails and accessToken stays AT1-tenantB.
        auth.switchTenant("tenant-A")
        verify(timeout = 2_000, exactly = 1) { apiMock.refreshToken("RT1") }
        verify(exactly = 1) { apiMock.refreshToken("RT0") } // still only the one legit use
        assert(auth.accessToken.value == "AT2-tenantA") {
            "after switch 2, accessToken must be AT2-tenantA (live-token refresh), was ${auth.accessToken.value}"
        }
    }

    /**
     * Shared setup for the three switchTenant-entitlements regression tests below.
     *
     * Re-builds [auth] with `entitlementsEnabled = true` (the flag is read at construction
     * time and baked into the [EntitlementsService] instance — see
     * FronteggAuthService.kt:116-117), seeds tenant A's cache with `{sso}`, and stubs the
     * full switchTenant happy-path API surface (api.switchTenant, api.refreshToken minting
     * tenant-B tokens, api.me returning tenant B as active, credentialManager saves).
     *
     * The tenant-B entitlements reload is intentionally left unmocked — each test sets it
     * to return null, an empty state, or a state-capturing answer block depending on what
     * it's exercising.
     */
    private fun setupSwitchTenantEntitlementsHarness() {
        every { storageMock.enableSessionPerTenant }.returns(false)
        every { storageMock.entitlementsEnabled }.returns(true)

        val appLifecycle = mockk<FronteggAppLifecycle>()
        every { appLifecycle.startApp }.returns(FronteggCallback())
        every { appLifecycle.stopApp }.returns(FronteggCallback())
        val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>()
        every { refreshTokenTimer.refreshTokenIfNeeded }.returns(FronteggCallback())
        every { refreshTokenTimer.cancelLastTimer() }.returns(Unit)
        every { refreshTokenTimer.scheduleTimer(any()) }.returns(Unit)
        auth = FronteggAuthService(
            credentialManager = credentialManagerMock,
            appLifecycle = appLifecycle,
            refreshTokenTimer = refreshTokenTimer,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            disableAutoRefresh = false
        )

        auth.accessToken.value = "stale-tenant-A-access-token"
        auth.refreshToken.value = "current-refresh-token"
        auth.isAuthenticated.value = true

        mockkObject(JWTHelper)
        val jwt = JWT()
        jwt.exp = (System.currentTimeMillis() / 1000) + 3600
        every { JWTHelper.decode(any()) }.returns(jwt)

        // Pre-populate cache for tenant A: SSO is entitled.
        every {
            apiMock.getUserEntitlements(accessTokenOverride = "stale-tenant-A-access-token")
        }.returns(EntitlementState(featureKeys = setOf("sso"), permissionKeys = emptySet()))
        auth.entitlements.load("stale-tenant-A-access-token")
        assert(auth.entitlements.state.featureKeys.contains("sso")) {
            "Sanity check: tenant A's cache must hold sso before switchTenant runs"
        }

        every { apiMock.switchTenant(any()) }.returns(Unit)
        every { apiMock.evictIdleConnections() }.returns(Unit)
        every { apiMock.refreshToken("current-refresh-token") }.returns(
            AuthResponse().apply {
                access_token = "access-token-for-B"
                refresh_token = "refresh-token-for-B"
            }
        )

        val tenantB = Tenant().apply { id = "tenant-B"; tenantId = "tenant-B" }
        every { apiMock.me() }.returns(
            User().apply {
                id = "user-1"
                tenants = listOf(tenantB)
                activeTenant = tenantB
            }
        )

        every { credentialManagerMock.save(any(), any()) }.returns(true)
        every { credentialManagerMock.save(any(), any(), any()) }.returns(true)
        every { credentialManagerMock.getCurrentTenantId() }.returns(null)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @Test
    fun `switchTenant reloads entitlements with the new tenant view (FR-24821 happy path)`() {
        // Customer scenario from FR-24821 — the bug that started this whole thread:
        // Tenant A has the "sso" feature; tenant B does not. After switching from A to B,
        // fronteggAuth.getFeatureEntitlements("sso") was reporting isEntitled=true on
        // Android (correct on web). This test reproduces that exact flow end-to-end and
        // asserts the post-switch verdict matches tenant B's reality.
        //
        // On master (post-PR #252) this test already passes — updateStateWithCredentials
        // fires loadEntitlements(forceRefresh = true) and the cache is replaced with
        // tenant B's set. It's preserved here as a top-level regression guard for the
        // customer-reported symptom: if either #252's wiring OR this PR's cache
        // invalidation ever silently regresses, this test catches it.
        setupSwitchTenantEntitlementsHarness()

        // Tenant B has no entitlements (server returns empty featureKeys/permissionKeys).
        every {
            apiMock.getUserEntitlements(accessTokenOverride = "access-token-for-B")
        }.returns(EntitlementState(featureKeys = emptySet(), permissionKeys = emptySet()))

        auth.switchTenant("tenant-B")

        verify(timeout = 2_000, exactly = 1) { apiMock.refreshToken("current-refresh-token") }
        verify(timeout = 2_000) {
            apiMock.getUserEntitlements(accessTokenOverride = "access-token-for-B")
        }

        // Cache reflects tenant B's reality, not tenant A's pre-switch view.
        assert(auth.entitlements.state.featureKeys.isEmpty()) {
            "entitlements cache must reflect tenant B's (empty) featureKeys after switchTenant; was ${auth.entitlements.state.featureKeys}"
        }
        assert(auth.entitlements.hasLoadedOnce) {
            "hasLoadedOnce must be true after a successful reload on tenant B"
        }

        // The customer's exact reproduction: getFeatureEntitlements("sso") must now report
        // not-entitled with MISSING_FEATURE (not ENTITLEMENTS_NOT_LOADED — the reload ran)
        // and definitely not isEntitled = true.
        val entitlement = auth.getFeatureEntitlements("sso")
        assert(!entitlement.isEntitled) {
            "FR-24821: getFeatureEntitlements(\"sso\") must be isEntitled=false after switching from tenant A (had sso) to tenant B (no sso); was ${entitlement.isEntitled}"
        }
        assert(entitlement.justification == "MISSING_FEATURE") {
            "justification must be MISSING_FEATURE after a successful reload on tenant B (which lacks sso), was ${entitlement.justification}"
        }
    }

    @Test
    fun `switchTenant clears entitlements cache BEFORE triggering the reload (in-flight window)`() {
        // Differential test for the specific behavior this PR adds: entitlements.clear()
        // runs BEFORE loadEntitlements() inside updateStateWithCredentials. Without that
        // ordering, getFeatureEntitlements() called during the in-flight load window
        // (between loadEntitlements dispatch and api.getUserEntitlements completing)
        // would return the PREVIOUS tenant's verdict — state and hasLoadedOnce are
        // unchanged until EntitlementsService.load assigns _state.
        //
        // We capture the cache state at the exact moment api.getUserEntitlements is
        // invoked. With the fix: state is already empty, hasLoadedOnce is already false
        // (clear ran first). Without the fix: state still holds tenant A's {sso}.
        setupSwitchTenantEntitlementsHarness()

        var stateAtLoadTime: EntitlementState? = null
        var hasLoadedOnceAtLoadTime: Boolean? = null
        every {
            apiMock.getUserEntitlements(accessTokenOverride = "access-token-for-B")
        }.answers {
            stateAtLoadTime = auth.entitlements.state
            hasLoadedOnceAtLoadTime = auth.entitlements.hasLoadedOnce
            EntitlementState(featureKeys = emptySet(), permissionKeys = emptySet())
        }

        auth.switchTenant("tenant-B")

        verify(timeout = 2_000) {
            apiMock.getUserEntitlements(accessTokenOverride = "access-token-for-B")
        }

        // Pre-fix: stateAtLoadTime would be {sso} (cache not cleared before load runs).
        // Post-fix: stateAtLoadTime is empty (clear ran first).
        assert(stateAtLoadTime?.featureKeys == emptySet<String>()) {
            "entitlements cache must be cleared BEFORE the reload is triggered; cache held ${stateAtLoadTime?.featureKeys} at the moment api.getUserEntitlements was called"
        }
        assert(hasLoadedOnceAtLoadTime == false) {
            "hasLoadedOnce must be false BEFORE the reload is triggered (clear resets it); was ${hasLoadedOnceAtLoadTime}"
        }
    }

    @Test
    fun `switchTenant clears stale entitlements cache so a failed reload does not leak the previous tenant view`() {
        // Regression (FR-24821 follow-up): PR #252 routes switchTenant through
        // updateStateWithCredentials, which fires loadEntitlements(forceRefresh = true).
        // On the happy path that replaces the cache. But the cache is never explicitly
        // invalidated, so if api.getUserEntitlements fails (5xx, network flake — the Api
        // layer catches the exception and returns null; EntitlementsService.load returns
        // false without touching _state), the cache keeps the PREVIOUS tenant's
        // featureKeys/permissionKeys forever, and getFeatureEntitlements() silently
        // reports the wrong verdict against the new tenant.
        //
        // Post-fix: updateStateWithCredentials calls entitlements.clear() before
        // loadEntitlements, so a failed reload yields ENTITLEMENTS_NOT_LOADED — not stale.
        setupSwitchTenantEntitlementsHarness()

        // Tenant B's entitlements reload fails — 5xx, network flake, whatever. Api
        // swallows the exception and returns null; EntitlementsService.load returns
        // false without touching _state.
        every {
            apiMock.getUserEntitlements(accessTokenOverride = "access-token-for-B")
        }.returns(null)

        auth.switchTenant("tenant-B")

        // Wait for the access-token rotation + entitlements reload attempt so we know
        // switchTenant has reached updateStateWithCredentials.
        verify(timeout = 2_000, exactly = 1) { apiMock.refreshToken("current-refresh-token") }
        verify(timeout = 2_000) {
            apiMock.getUserEntitlements(accessTokenOverride = "access-token-for-B")
        }

        // Pre-fix: the cache still holds tenant A's {sso} (load returned false without
        // touching _state). Post-fix: clear() ran before loadEntitlements, so the cache
        // is empty.
        assert(auth.entitlements.state.featureKeys.isEmpty()) {
            "entitlements cache must be cleared on tenant switch even when the reload fails; was ${auth.entitlements.state.featureKeys}"
        }
        assert(!auth.entitlements.hasLoadedOnce) {
            "hasLoadedOnce must be false after a failed reload following clear()"
        }

        // The user-facing surface: getFeatureEntitlements must not silently leak tenant A's
        // verdict. Pre-fix it returned isEntitled=true (tenant A had sso); post-fix it
        // reports ENTITLEMENTS_NOT_LOADED.
        val entitlement = auth.getFeatureEntitlements("sso")
        assert(!entitlement.isEntitled) {
            "After switching to tenant B with a failed entitlements reload, getFeatureEntitlements(\"sso\").isEntitled must be false; the cache is leaking tenant A's verdict"
        }
        assert(entitlement.justification == "ENTITLEMENTS_NOT_LOADED") {
            "justification must be ENTITLEMENTS_NOT_LOADED after clear() + failed reload, was ${entitlement.justification}"
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

    private fun buildAuthWith(dispatcher: kotlinx.coroutines.CoroutineDispatcher): FronteggAuthService {
        val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>()
        every { refreshTokenTimer.refreshTokenIfNeeded }.returns(FronteggCallback())
        every { refreshTokenTimer.cancelLastTimer() }.returns(Unit)
        every { refreshTokenTimer.scheduleTimer(any()) }.returns(Unit)
        val appLifecycle = mockk<FronteggAppLifecycle>()
        every { appLifecycle.startApp }.returns(FronteggCallback())
        every { appLifecycle.stopApp }.returns(FronteggCallback())
        return FronteggAuthService(
            credentialManager = credentialManagerMock,
            appLifecycle = appLifecycle,
            refreshTokenTimer = refreshTokenTimer,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            disableAutoRefresh = false
        )
    }

    /**
     * FR-25932: refreshTokenIfNeeded() is documented as "returns immediately" but used to
     * run NetworkGate's blocking OkHttp ping synchronously on the caller's thread — a
     * NetworkOnMainThreadException / crash when called from the main thread. The blocking
     * network work must be offloaded to the background dispatcher.
     */
    @Test
    fun `refreshTokenIfNeeded does not touch the network on the calling thread`() {
        val dispatcher = QueueingCoroutineDispatcher()
        val auth = buildAuthWith(dispatcher)
        dispatcher.clearQueue() // drop construction-time launches

        every { NetworkGate.isNetworkLikelyGood(any()) }.returns(true)
        every { apiMock.refreshToken(any()) }.returns(authResponse)
        every { apiMock.me() }.returns(mockk<User>(relaxed = true))
        every { credentialManagerMock.save(any(), any()) }.returns(true)
        every { credentialManagerMock.save(any(), any(), any()) }.returns(true)

        val result = auth.refreshTokenIfNeeded()

        // Contract preserved: a refresh was scheduled.
        assert(result)
        // The blocking network-quality check must NOT have run on this (calling) thread...
        verify(exactly = 0) { NetworkGate.isNetworkLikelyGood(any()) }
        // ...it must have been offloaded to the background dispatcher instead.
        assert(dispatcher.size >= 1) { "expected blocking work to be dispatched to background, queue was empty" }
    }

    /**
     * FR-25932: updateCredentials() -> setCredentials() called api.me() (blocking OkHttp
     * .execute()) inline on the caller's thread. It must be offloaded, while blank-token
     * validation stays synchronous.
     */
    @Test
    fun `updateCredentials does not call api_me on the calling thread`() {
        val dispatcher = QueueingCoroutineDispatcher()
        val auth = buildAuthWith(dispatcher)
        dispatcher.clearQueue() // drop construction-time launches

        every { credentialManagerMock.save(any(), any()) }.returns(true)
        every { credentialManagerMock.save(any(), any(), any()) }.returns(true)
        every { apiMock.me() }.returns(mockk<User>(relaxed = true))

        auth.updateCredentials("valid-access-token", "valid-refresh-token")

        // The blocking api.me() call must NOT have run on this (calling) thread...
        verify(exactly = 0) { apiMock.me() }
        // ...it must have been offloaded to the background dispatcher instead.
        assert(dispatcher.size >= 1) { "expected blocking work to be dispatched to background, queue was empty" }
    }
}