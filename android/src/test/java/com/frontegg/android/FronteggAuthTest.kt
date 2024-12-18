package com.frontegg.android

import android.app.Activity
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import com.frontegg.android.embedded.CredentialManagerHandler
import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.User
import com.frontegg.android.models.WebAuthnAssertionRequest
import com.frontegg.android.models.WebAuthnRegistrationRequest
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.Api
import com.frontegg.android.services.AuthorizeUrlGeneratorProvider
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.CredentialManagerHandlerProvider
import com.frontegg.android.services.ScopeProvider
import com.frontegg.android.testUtils.BlockCoroutineDispatcher
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.CredentialKeys
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

class FronteggAuthTest {
    private val mockActivity = mockk<Activity>()
    private lateinit var auth: FronteggAuth
    private val mockFronteggApp = mockkClass(FronteggApp::class)
    private val mockCredentialManager = mockk<CredentialManager>()
    private val authResponse = AuthResponse()

    @Before
    fun setUp() {
        every { mockFronteggApp.clientId }.returns("TestClientId")
        every { mockFronteggApp.applicationId }.returns("TestApplicationId")
        every { mockFronteggApp.baseUrl }.returns("https://base.url.com")
        every { mockFronteggApp.credentialManager }.returns(mockCredentialManager)
        mockkObject(FronteggApp.Companion)
        every { FronteggApp.getInstance() }.returns(mockFronteggApp)
        every { mockCredentialManager.get(any()) }.returns(null)
        auth = FronteggAuth(
            baseUrl = "https://base.url.com",
            clientId = "",
            selectedRegion = null,
            regions = listOf(),
            credentialManager = mockCredentialManager,
            applicationId = null,
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
        every { mockFronteggApp.isEmbeddedMode }.returns(true)

        mockkObject(EmbeddedAuthActivity.Companion)
        every { EmbeddedAuthActivity.authenticate(any(), any(), any()) }.returns(Unit)
        auth.login(mockActivity, null, null)

        verify { EmbeddedAuthActivity.authenticate(any(), any(), any()) }
    }

    @Test
    fun `login via EmbeddedAuthActivity if EmbeddedAuthActivity is disable`() {
        every { mockFronteggApp.isEmbeddedMode }.returns(false)

        mockkObject(AuthenticationActivity.Companion)
        every { AuthenticationActivity.authenticate(any(), any(), any()) }.returns(Unit)
        auth.login(mockActivity, null, null)

        verify { AuthenticationActivity.authenticate(any(), any(), any()) }
    }

    @Test
    fun `reinitWithRegion should set up FronteggAuth_selectedRegion`() {
        val regionConfig = RegionConfig(
            key = "key1",
            baseUrl = "TestBaseUrl",
            clientId = "TestClientId",
            applicationId = "TestApplicationId"
        )
        auth.reinitWithRegion(regionConfig)

        assert(auth.selectedRegion == regionConfig)
    }

    @Test
    fun `reinitWithRegion should set up FronteggAuth_baseUrl`() {
        val regionConfig = RegionConfig(
            key = "key1",
            baseUrl = "TestBaseUrl",
            clientId = "TestClientId",
            applicationId = "TestApplicationId"
        )
        auth.reinitWithRegion(regionConfig)

        assert(auth.baseUrl == "https://TestBaseUrl")
    }

    @Test
    fun `reinitWithRegion should set up FronteggAuth_clientId`() {
        val regionConfig = RegionConfig(
            key = "key1",
            baseUrl = "TestBaseUrl",
            clientId = "TestClientId",
            applicationId = "TestApplicationId"
        )
        auth.reinitWithRegion(regionConfig)

        assert(auth.clientId == "TestClientId")
    }

    @Test
    fun `reinitWithRegion should set up FronteggAuth_applicationId`() {
        val regionConfig = RegionConfig(
            key = "key1",
            baseUrl = "TestBaseUrl",
            clientId = "TestClientId",
            applicationId = "TestApplicationId"
        )
        auth.reinitWithRegion(regionConfig)

        assert(auth.applicationId == "TestApplicationId")
    }

    @Test
    fun `sendRefreshToken should call Api_refreshToken and Api_me`() {
        auth.refreshToken.value = "TestRefreshToken"

        val apiMock = mockkClass(Api::class)


        val user = User()

        every { apiMock.refreshToken(any()) }.returns(authResponse)
        every { apiMock.me() }.returns(user)
        auth.setApi(apiMock)

        every { mockCredentialManager.save(any(), any()) }.returns(true)


        mockkObject(JWTHelper.Companion)
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

        val apiMock = mockkClass(Api::class)

        val authResponse = AuthResponse()
        authResponse.id_token = "TestIdToken"
        authResponse.refresh_token = "TestRefreshToken"
        authResponse.access_token = "TestAccessToken"
        authResponse.token_type = "TestTokenToken"
        val user = User()

        every { apiMock.refreshToken(any()) }.returns(authResponse)
        every { apiMock.me() }.returns(user)
        auth.setApi(apiMock)

        every { mockCredentialManager.save(any(), any()) }.returns(true)


        mockkObject(JWTHelper.Companion)
        val jwt = JWT()
        every { JWTHelper.decode(any()) }.returns(jwt)

        val result = auth.sendRefreshToken()
        verify { mockCredentialManager.save(any(), any()) }
        assert(result)
    }

    @Test
    fun `sendRefreshToken should not call CredentialManager_save if Api_refreshToken returns null`() {
        val apiMock = mockkClass(Api::class)
        every { apiMock.refreshToken(any()) }.returns(null)
        auth.setApi(apiMock)

        every { mockCredentialManager.save(any(), any()) }.returns(true)

        val result = auth.sendRefreshToken()
        verify(exactly = 0) { mockCredentialManager.save(any(), any()) }
        assert(!result)
    }

    @Test
    fun `refreshTokenIfNeeded should return false if Exception`() {
        val apiMock = mockkClass(Api::class)
        every { apiMock.refreshToken(any()) }.throws(Exception())
        auth.setApi(apiMock)

        val result = auth.refreshTokenIfNeeded()
        verify(exactly = 0) { mockCredentialManager.save(any(), any()) }
        assert(!result)
    }

    @Test
    fun `logout should refresh FronteggAuth`() {
        val mockCookieManager = mockkClass(CookieManager::class)
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() }.returns(mockCookieManager)
        every { mockCookieManager.getCookie(any()) }.returns(null)
        every { mockCredentialManager.clear() }.returns(Unit)

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
        every { mockCredentialManager.clear() }.returns(Unit)
        every { mockFronteggApp.isEmbeddedMode }.returns(true)


        auth.accessToken.value = "TestRefreshToken"

        val apiMock = mockkClass(Api::class)

        every { apiMock.logout(any(), any()) }.returns(Unit)
        auth.setApi(apiMock)
        auth.logout()

        Thread.sleep(1_000)
        verify { apiMock.logout(any(), any()) }
    }

    @Test
    fun `handleHostedLoginCallback should return false if codeVerifier is null`() {
        every { mockCredentialManager.getCodeVerifier() }.returns(null)
        every { mockFronteggApp.packageName }.returns("dem.test.com")
        every { mockFronteggApp.useAssetsLinks }.returns(true)
        val result = auth.handleHostedLoginCallback("TestCode")
        assert(!result)
    }

    @Test
    fun `handleHostedLoginCallback should return true if codeVerifier is not null`() {
        every { mockCredentialManager.getCodeVerifier() }.returns("TestCodeVerifier")
        every { mockFronteggApp.packageName }.returns("dem.test.com")
        every { mockFronteggApp.useAssetsLinks }.returns(true)

        val apiMock = mockkClass(Api::class)
        every { apiMock.exchangeToken(any(), any(), any()) }.returns(authResponse)
        auth.setApi(apiMock)

        every { mockCredentialManager.save(any(), any()) }.returns(false)

        val result = auth.handleHostedLoginCallback("TestCode")
        assert(result)
    }

    @Test
    fun `handleHostedLoginCallback should call callback if data is not null`() {
        every { mockCredentialManager.getCodeVerifier() }.returns("TestCodeVerifier")
        every { mockFronteggApp.packageName }.returns("dem.test.com")
        every { mockFronteggApp.useAssetsLinks }.returns(true)
        var called = false

        val apiMock = mockkClass(Api::class)
        every { apiMock.exchangeToken(any(), any(), any()) }.returns(authResponse)
        auth.setApi(apiMock)

        every { mockCredentialManager.save(any(), any()) }.returns(false)

        val result = auth.handleHostedLoginCallback("TestCode", callback = { called = true })
        Thread.sleep(1_000)
        assert(result)
        assert(called)
    }

    @Test
    fun `handleHostedLoginCallback should call AuthorizeUrlGenerator_generate if data is null and webView is not null`() {
        every { mockCredentialManager.getCodeVerifier() }.returns("TestCodeVerifier")
        every { mockCredentialManager.saveCodeVerifier(any()) }.returns(true)

        every { mockFronteggApp.packageName }.returns("dem.test.com")
        every { mockFronteggApp.useAssetsLinks }.returns(true)

        val apiMock = mockkClass(Api::class)
        every { apiMock.exchangeToken(any(), any(), any()) }.returns(null)
        auth.setApi(apiMock)

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
        every { mockCredentialManager.getCodeVerifier() }.returns("TestCodeVerifier")
        every { mockCredentialManager.saveCodeVerifier(any()) }.returns(true)

        every { mockFronteggApp.packageName }.returns("dem.test.com")
        every { mockFronteggApp.useAssetsLinks }.returns(true)

        val apiMock = mockkClass(Api::class)
        every { apiMock.exchangeToken(any(), any(), any()) }.returns(null)
        auth.setApi(apiMock)

        val mockAuthorizeUrl = mockkClass(AuthorizeUrlGenerator::class)
        every { mockAuthorizeUrl.generate(any(), any(), any()) }.returns(Pair("First", "Second"))

        mockkObject(AuthorizeUrlGeneratorProvider)
        every { AuthorizeUrlGeneratorProvider.getAuthorizeUrlGenerator() }.returns(mockAuthorizeUrl)

        every { mockFronteggApp.isEmbeddedMode }.returns(true)
        mockkObject(EmbeddedAuthActivity.Companion)
        every { EmbeddedAuthActivity.authenticate(any(), any(), any()) }.returns(Unit)

        val result = auth.handleHostedLoginCallback("TestCode", activity = mockActivity)

        verify(timeout = 1_000) { EmbeddedAuthActivity.authenticate(any(), any(), any()) }
        assert(result)
    }

    @Test
    fun `directLoginAction should call EmbeddedAuthActivity_directLoginAction if is isEmbeddedMode`() {
        every { mockFronteggApp.isEmbeddedMode }.returns(true)

        mockkObject(EmbeddedAuthActivity.Companion)
        every { EmbeddedAuthActivity.directLoginAction(any(), any(), any(), any()) }.returns(Unit)

        auth.directLoginAction(mockActivity, "", "")

        verify { EmbeddedAuthActivity.directLoginAction(any(), any(), any(), any()) }
    }

    @Test
    fun `directLoginAction should call Log_w if is not isEmbeddedMode`() {
        every { mockFronteggApp.isEmbeddedMode }.returns(false)
        every { Log.w(any(), any<String>()) }.returns(0)
        auth.directLoginAction(mockActivity, "", "")

        verify { Log.w(any(), any<String>()) }
    }

    @Test
    fun `switchTenant should call Api_switchTenant`() {
        val mockApi = mockkClass(Api::class)
        every { mockApi.switchTenant(any()) }.returns(Unit)
        auth.setApi(mockApi)

        auth.switchTenant("TestTenantId")

        verify(timeout = 1_000) { mockApi.switchTenant(any()) }
    }

    @Test
    fun `loginWithPasskeys should call Api_webAuthnPrelogin`() {
        val mockApi = mockkClass(Api::class)

        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { mockApi.webAuthnPrelogin() }.returns(request)
        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
        )

        auth.loginWithPasskeys(mockActivity)
        verify(timeout = 1_000) { mockApi.webAuthnPrelogin() }
    }

    @Test
    fun `loginWithPasskeys should call CredentialManagerHandler_getPasskey`() {
        val mockApi = mockkClass(Api::class)

        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { mockApi.webAuthnPrelogin() }.returns(request)
        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
        )

        val response = GetCredentialResponse(
            PublicKeyCredential(
                authenticationResponseJson = "{}",
            )
        )

        coEvery { mockCredentialManagerHandler.getPasskey(any()) }.returns(response)

        auth.loginWithPasskeys(mockActivity)
        coVerify { mockCredentialManagerHandler.getPasskey(any()) }
    }

    @Test
    fun `loginWithPasskeys should call Api_webAuthnPostlogin`() {
        val mockApi = mockkClass(Api::class)

        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        val response = GetCredentialResponse(
            credential = PublicKeyCredential(
                authenticationResponseJson = "{}",
            )
        )
        every { mockApi.webAuthnPostlogin(any(), any()) }.returns(authResponse)
        every { mockApi.webAuthnPrelogin() }.returns(request)
        every { mockApi.toString() }.returns("")

        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
        )

        coEvery { mockCredentialManagerHandler.getPasskey(any()) }.returns(response)

        auth.loginWithPasskeys(mockActivity)
        verify(timeout = 1_000) { mockApi.webAuthnPostlogin(any(), any()) }
    }

    @Test
    fun `loginWithPasskeys should call setCredentials`() {
        val mockApi = mockkClass(Api::class)

        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        val response = GetCredentialResponse(
            credential = PublicKeyCredential(
                authenticationResponseJson = "{}",
            )
        )
        every { mockApi.webAuthnPostlogin(any(), any()) }.returns(authResponse)
        every { mockApi.webAuthnPrelogin() }.returns(request)

        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
        )

        coEvery { mockCredentialManagerHandler.getPasskey(any()) }.returns(response)

        every { mockCredentialManager.save(any(), any()) }.returns(true)

        auth.loginWithPasskeys(mockActivity)

        verify(timeout = 1_000) {
            mockCredentialManager.save(
                CredentialKeys.REFRESH_TOKEN,
                "TestRefreshToken"
            )
        }
        verify(timeout = 1_000) {
            mockCredentialManager.save(
                CredentialKeys.ACCESS_TOKEN,
                "TestAccessToken"
            )
        }
    }

    @Test
    fun `loginWithPasskeys should call callback with null Exception`() {
        val mockApi = mockkClass(Api::class)

        val request = WebAuthnAssertionRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        val response = GetCredentialResponse(
            credential = PublicKeyCredential(
                authenticationResponseJson = "{}",
            )
        )
        every { mockApi.webAuthnPostlogin(any(), any()) }.returns(authResponse)
        every { mockApi.webAuthnPrelogin() }.returns(request)

        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
        )

        coEvery { mockCredentialManagerHandler.getPasskey(any()) }.returns(response)

        every { mockCredentialManager.save(any(), any()) }.returns(false)

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
        val mockApi = mockkClass(Api::class)

        every { mockApi.webAuthnPostlogin(any(), any()) }.throws(Exception())

        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
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
        val mockApi = mockkClass(Api::class)
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { mockApi.getWebAuthnRegisterChallenge() }.returns(request)
        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
        )

        auth.registerPasskeys(mockActivity)
        verify { mockApi.getWebAuthnRegisterChallenge() }
    }

    @Test
    fun `registerPasskeys should call CredentialManagerHandler_createPasskey`() {
        val mockApi = mockkClass(Api::class)
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { mockApi.getWebAuthnRegisterChallenge() }.returns(request)
        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
        )

        val response = CreatePublicKeyCredentialResponse(
            registrationResponseJson = "{}"
        )
        coEvery { mockCredentialManagerHandler.createPasskey(any()) }.returns(response)

        auth.registerPasskeys(mockActivity)
        coVerify { mockCredentialManagerHandler.createPasskey(any()) }
    }

    @Test
    fun `registerPasskeys should call Api_verifyWebAuthnDevice`() {
        val mockApi = mockkClass(Api::class)
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { mockApi.getWebAuthnRegisterChallenge() }.returns(request)
        every { mockApi.verifyWebAuthnDevice(any(), any()) }.returns(Unit)
        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
        )

        val response = CreatePublicKeyCredentialResponse(
            registrationResponseJson = "{}"
        )
        coEvery { mockCredentialManagerHandler.createPasskey(any()) }.returns(response)

        auth.registerPasskeys(mockActivity)
        verify { mockApi.verifyWebAuthnDevice(any(), any()) }
    }

    @Test
    fun `registerPasskeys should call call callback with null Exception`() {
        val mockApi = mockkClass(Api::class)
        val request = WebAuthnRegistrationRequest(
            cookie = "TestCookie",
            jsonChallenge = "TestJsonChallenge"
        )
        every { mockApi.getWebAuthnRegisterChallenge() }.returns(request)
        every { mockApi.verifyWebAuthnDevice(any(), any()) }.returns(Unit)
        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
        )

        val response = CreatePublicKeyCredentialResponse(
            registrationResponseJson = "{}"
        )
        coEvery { mockCredentialManagerHandler.createPasskey(any()) }.returns(response)

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
        val mockApi = mockkClass(Api::class)
        every { mockApi.getWebAuthnRegisterChallenge() }.throws(Exception())
        auth.setApi(mockApi)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))

        mockkObject(CredentialManagerHandlerProvider)
        val mockCredentialManagerHandler = mockkClass(CredentialManagerHandler::class)
        every { CredentialManagerHandlerProvider.getCredentialManagerHandler(any()) }.returns(
            mockCredentialManagerHandler
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
}
