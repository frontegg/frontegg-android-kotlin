package com.frontegg.android.services

import android.app.Activity
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import com.frontegg.android.AuthenticationActivity
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.FronteggApp
import com.frontegg.android.embedded.CredentialManagerHandler
import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.User
import com.frontegg.android.models.WebAuthnAssertionRequest
import com.frontegg.android.models.WebAuthnRegistrationRequest
import com.frontegg.android.testUtils.BlockCoroutineDispatcher
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.FronteggCallback
import com.frontegg.android.utils.JWT
import com.frontegg.android.utils.JWTHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import org.junit.Before
import org.junit.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class FronteggAuthServiceTest {
    private val mockActivity = mockk<Activity>()
    private lateinit var auth: FronteggAuthService
    private val credentialManagerMock = mockk<CredentialManager>()
    private val apiMock = mockk<Api>()
    private val authResponse = AuthResponse()
    private val storageMock = mockk<FronteggInnerStorage>()

    private val stepUpAuthenticator = mockk<StepUpAuthenticator>()


    @Before
    fun setUp() {
        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(storageMock)
        every { storageMock.clientId }.returns("TestClientId")
        every { storageMock.applicationId }.returns("TestApplicationId")
        every { storageMock.baseUrl }.returns("https://base.url.com")
        every { storageMock.regions }.returns(listOf())
        mockkObject(FronteggApp)
        every { credentialManagerMock.get(any()) }.returns(null)
        val appLifecycle = mockk<FronteggAppLifecycle>()
        every { appLifecycle.startApp }.returns(FronteggCallback())
        every { appLifecycle.stopApp }.returns(FronteggCallback())

        val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>()
        every { refreshTokenTimer.refreshTokenIfNeeded }.returns(FronteggCallback())
        every { refreshTokenTimer.cancelLastTimer() }.returns(Unit)

        mockkObject(ApiProvider)
        every { ApiProvider.getApi(any()) }.returns(apiMock)

        mockkObject(StepUpAuthenticatorProvider)
        every {
            StepUpAuthenticatorProvider.getStepUpAuthenticator(
                any(),
                any()
            )
        } returns stepUpAuthenticator

        auth = FronteggAuthService(
            credentialManager = credentialManagerMock,
            refreshTokenTimer = refreshTokenTimer,
            appLifecycle = appLifecycle,
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
        every { EmbeddedAuthActivity.authenticate(any(), any(), any()) }.returns(Unit)
        auth.login(mockActivity, null, null)

        verify { EmbeddedAuthActivity.authenticate(any(), any(), any()) }
    }

    @Test
    fun `login via AuthenticationActivity if EmbeddedAuthActivity is disable`() {
        every { storageMock.isEmbeddedMode }.returns(false)

        mockkObject(AuthenticationActivity)
        every { AuthenticationActivity.authenticate(any(), any(), any()) }.returns(Unit)
        auth.login(mockActivity, null, null)

        verify { AuthenticationActivity.authenticate(any(), any(), any()) }
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
    fun `sendRefreshToken should not call CredentialManager_save if Api_refreshToken returns null`() {
        every { apiMock.refreshToken(any()) }.returns(null)
        every { credentialManagerMock.save(any(), any()) }.returns(true)

        val result = auth.sendRefreshToken()

        verify(exactly = 0) { credentialManagerMock.save(any(), any()) }
        assert(!result)
    }

    @Test
    fun `refreshTokenIfNeeded should return false if Exception`() {
        every { apiMock.refreshToken(any()) }.throws(Exception())

        val result = auth.refreshTokenIfNeeded()

        verify(exactly = 0) { credentialManagerMock.save(any(), any()) }
        assert(!result)
    }

    @Test
    fun `logout should refresh FronteggAuth`() {
        val mockCookieManager = mockkClass(CookieManager::class)
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() }.returns(mockCookieManager)
        every { mockCookieManager.getCookie(any()) }.returns(null)
        every { credentialManagerMock.clear() }.returns(Unit)

        auth.logout()
        Thread.sleep(1_000)
        assert(auth.accessToken.value == null)
        assert(auth.refreshToken.value == null)
        assert(auth.user.value == null)
        assert(!auth.isAuthenticated.value)
    }

    @Test
    fun `logout should call Api_logout if isEmbeddedMode and cookies is not empty`() {
        val mockCookieManager = mockkClass(CookieManager::class)
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() }.returns(mockCookieManager)
        every { mockCookieManager.getCookie(any()) }.returns("TestCoolies")
        every { credentialManagerMock.clear() }.returns(Unit)
        every { storageMock.isEmbeddedMode }.returns(true)

        auth.accessToken.value = "TestRefreshToken"

        every { apiMock.logout(any(), any()) }.returns(Unit)

        auth.logout()

        Thread.sleep(1_000)
        verify { apiMock.logout(any(), any()) }
    }

    @Test
    fun `handleHostedLoginCallback should return false if codeVerifier is null`() {
        every { credentialManagerMock.getCodeVerifier() }.returns(null)
        every { storageMock.packageName }.returns("dem.test.com")
        every { storageMock.useAssetsLinks }.returns(true)

        val result = auth.handleHostedLoginCallback("TestCode")

        assert(!result)
    }

    @Test
    fun `handleHostedLoginCallback should return true if codeVerifier is not null`() {
        every { credentialManagerMock.getCodeVerifier() }.returns("TestCodeVerifier")
        every { storageMock.packageName }.returns("dem.test.com")
        every { storageMock.useAssetsLinks }.returns(true)
        every { apiMock.exchangeToken(any(), any(), any()) }.returns(authResponse)

        every { credentialManagerMock.save(any(), any()) }.returns(false)

        val result = auth.handleHostedLoginCallback("TestCode")
        assert(result)
    }

    @Test
    fun `handleHostedLoginCallback should call callback if data is not null`() {
        every { credentialManagerMock.getCodeVerifier() }.returns("TestCodeVerifier")
        every { storageMock.packageName }.returns("dem.test.com")
        every { storageMock.useAssetsLinks }.returns(true)
        var called = false

        every { apiMock.exchangeToken(any(), any(), any()) }.returns(authResponse)

        every { credentialManagerMock.save(any(), any()) }.returns(false)

        val result = auth.handleHostedLoginCallback("TestCode", callback = { called = true })
        Thread.sleep(1_000)
        assert(result)
        assert(called)
    }

    @Test
    fun `handleHostedLoginCallback should call AuthorizeUrlGenerator_generate if data is null and webView is not null`() {
        every { credentialManagerMock.getCodeVerifier() }.returns("TestCodeVerifier")
        every { credentialManagerMock.saveCodeVerifier(any()) }.returns(true)

        every { storageMock.packageName }.returns("dem.test.com")
        every { storageMock.useAssetsLinks }.returns(true)

        every { apiMock.exchangeToken(any(), any(), any()) }.returns(null)

        val mockWebView = mockkClass(WebView::class)
        every { mockWebView.loadUrl(any()) }.returns(Unit)


        val mockAuthorizeUrl = mockkClass(AuthorizeUrlGenerator::class)
        every { mockAuthorizeUrl.generate(any(), any(), any()) }.returns(Pair("First", "Second"))

        mockkObject(AuthorizeUrlGeneratorProvider)
        every { AuthorizeUrlGeneratorProvider.getAuthorizeUrlGenerator() }.returns(mockAuthorizeUrl)

        val result = auth.handleHostedLoginCallback("TestCode", webView = mockWebView)
        verify(timeout = 1_000) { mockAuthorizeUrl.generate(any(), any(), any()) }
        assert(result)
    }

    @Test
    fun `handleHostedLoginCallback should call login if data is null and activity is not null and callback is null`() {
        every { credentialManagerMock.getCodeVerifier() }.returns("TestCodeVerifier")
        every { credentialManagerMock.saveCodeVerifier(any()) }.returns(true)

        every { storageMock.packageName }.returns("dem.test.com")
        every { storageMock.useAssetsLinks }.returns(true)

        every { apiMock.exchangeToken(any(), any(), any()) }.returns(null)

        val mockAuthorizeUrl = mockkClass(AuthorizeUrlGenerator::class)
        every { mockAuthorizeUrl.generate(any(), any(), any()) }.returns(Pair("First", "Second"))

        mockkObject(AuthorizeUrlGeneratorProvider)
        every { AuthorizeUrlGeneratorProvider.getAuthorizeUrlGenerator() }.returns(mockAuthorizeUrl)

        every { storageMock.isEmbeddedMode }.returns(true)
        mockkObject(EmbeddedAuthActivity)
        every { EmbeddedAuthActivity.authenticate(any(), any(), any()) }.returns(Unit)

        val result = auth.handleHostedLoginCallback("TestCode", activity = mockActivity)

        verify(timeout = 1_000) { EmbeddedAuthActivity.authenticate(any(), any(), any()) }
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
    fun `loginWithPasskeys should call Api_webAuthnPrelogin`() {
        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.webAuthnPrelogin() }.returns(request)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        auth.loginWithPasskeys(mockActivity)
        verify(timeout = 1_000) { apiMock.webAuthnPrelogin() }
    }

    @Test
    fun `loginWithPasskeys should call CredentialManagerHandler_getPasskey`() {
        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.webAuthnPrelogin() }.returns(request)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

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
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

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
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

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
    fun `loginWithPasskeys should call callback with null Exception`() {
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
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        coEvery { credentialManagerMockHandler.getPasskey(any()) }.returns(response)

        every { credentialManagerMock.save(any(), any()) }.returns(false)

        var called = false
        var exception: Exception? = null
        auth.loginWithPasskeys(mockActivity, callback = {
            called = true
            exception = it
        })

        Thread.sleep(1_000)
        assert(called)
        assert(exception == null)
    }

    @Test
    fun `loginWithPasskeys should call callback with Exception if some error occurred`() {
        every { apiMock.webAuthnPostlogin(any(), any()) }.throws(Exception())

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

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

        Thread.sleep(1_000)
        assert(called)
        assert(exception != null)
    }

    @Test
    fun `registerPasskeys should call Api_getWebAuthnRegisterChallenge`() {
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.getWebAuthnRegisterChallenge() }.returns(request)


        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val credentialManagerMockHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            credentialManagerMockHandler
        )

        auth.registerPasskeys(mockActivity)
        verify { apiMock.getWebAuthnRegisterChallenge() }
    }

    @Test
    fun `registerPasskeys should call CredentialManagerHandler_createPasskey`() {
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.getWebAuthnRegisterChallenge() }.returns(request)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

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
        coVerify { credentialManagerMockHandler.createPasskey(any()) }
    }

    @Test
    fun `registerPasskeys should call Api_verifyWebAuthnDevice`() {
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.getWebAuthnRegisterChallenge() }.returns(request)
        every { apiMock.verifyWebAuthnDevice(any(), any()) }.returns(Unit)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

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
        verify { apiMock.verifyWebAuthnDevice(any(), any()) }
    }

    @Test
    fun `registerPasskeys should call call callback with null Exception`() {
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { apiMock.getWebAuthnRegisterChallenge() }.returns(request)
        every { apiMock.verifyWebAuthnDevice(any(), any()) }.returns(Unit)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

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

        Thread.sleep(1_000)
        assert(called)
        assert(exception == null)
    }

    @Test
    fun `registerPasskeys should call call callback with Exception if some error occurred`() {
        every { apiMock.getWebAuthnRegisterChallenge() }.throws(Exception())

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

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

        Thread.sleep(1_000)
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
}