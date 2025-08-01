package com.frontegg.android.utils

import android.content.Context
import android.net.Uri
import com.frontegg.android.FronteggApp
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.FronteggAppService
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.StorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthorizeUrlGeneratorTest {
    private lateinit var authorizeUrlGenerator: AuthorizeUrlGenerator
    private lateinit var credentialManager: CredentialManager
    private lateinit var fronteggApp: FronteggApp
    private lateinit var fronteggAuth: FronteggAuthService
    private val mockContext = mockk<Context>()
    private val mockStorage = mockk<FronteggInnerStorage>()
    private val baseUrl = "https://base.url.com/"
    private val clientId = "Test Client Id"

    @Before
    fun setUp() {
        fronteggApp = mockkClass(FronteggAppService::class)
        fronteggAuth = mockkClass(FronteggAuthService::class)

        credentialManager = mockkClass(CredentialManager::class)
        mockkObject(FronteggApp.Companion)
        mockkObject(FronteggApp)
        every { FronteggApp.instance } returns fronteggApp

        every { fronteggApp.auth }.returns(fronteggAuth)
        every { fronteggAuth.credentialManager }.returns(credentialManager)

        every { mockStorage.clientId }.returns(clientId)
        every { mockStorage.applicationId }.returns(null)
        every { mockStorage.baseUrl }.returns(baseUrl)
        every { mockStorage.packageName }.returns("test.com")
        every { mockStorage.useAssetsLinks }.returns(false)

        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(mockStorage)

        every { credentialManager.saveCodeVerifier(any()) }.returns(true)

        authorizeUrlGenerator = AuthorizeUrlGenerator(mockContext)
    }

    @Test
    fun `query parameter should contains baseUrl and oauth_authorize path`() {
        val url = authorizeUrlGenerator.generate()

        assert(url.first.contains(baseUrl))
        assert(url.first.contains("/oauth/authorize"))
    }

    @Test
    fun `should contains response_type query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val responseType = uri.getQueryParameter("response_type")
        assert(responseType != null)
        assert(responseType == "code")
    }

    @Test
    fun `should contains client_id query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val clientId = uri.getQueryParameter("client_id")
        assert(clientId != null)
        assert(clientId == clientId)
    }

    @Test
    fun `client_id query parameter should be applicationId if applicationId not null`() {
        every { mockStorage.applicationId }.returns("Test Application Id")
        val url = AuthorizeUrlGenerator(mockContext).generate()
        every { mockStorage.applicationId }.returns(null)
        val uri = Uri.parse(url.first)

        val clientId = uri.getQueryParameter("client_id")
        assert(clientId != null)
        assert(clientId == "Test Application Id")
    }

    @Test
    fun `should contains scope query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val scope = uri.getQueryParameter("scope")
        assert(scope != null)
        assert(scope == "openid email profile")
    }

    @Test
    fun `should contains redirect_uri query parameter`() {
        mockkObject(Constants.Companion)
        every { Constants.oauthCallbackUrl(baseUrl) }.returns("oauthCallbackUrl")

        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val redirectUri = uri.getQueryParameter("redirect_uri")
        assert(redirectUri != null)
        assert(redirectUri == "oauthCallbackUrl")
    }

    @Test
    fun `should contains code_challenge query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)


        val codeChallenge = uri.getQueryParameter("code_challenge")
        assert(codeChallenge != null)
    }

    @Test
    fun `should contains code_challenge_method query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val codeChallengeMethod =
            uri.getQueryParameter("code_challenge_method")
        assert(codeChallengeMethod != null)
        assert(codeChallengeMethod == "S256")
    }

    @Test
    fun `should contains nonce query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val nonce = uri.getQueryParameter("nonce")
        assert(nonce != null)
    }

    @Test
    fun `should not contains loginHint query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val loginHint = uri.getQueryParameter("login_hint")
        assert(loginHint == null)
    }

    @Test
    fun `should contains loginHint query parameter if pass value to generate call`() {
        val url = authorizeUrlGenerator.generate(loginHint = "loginHint@mail.com")
        val uri = Uri.parse(url.first)

        val loginHint = uri.getQueryParameter("login_hint")
        assert(loginHint != null)
        assert(loginHint == "loginHint@mail.com")
    }

    @Test
    fun `generate should contains base url and oauth_authorize path if pass loginAction to generate`() {
        val url = authorizeUrlGenerator.generate(loginAction = "loginAction")

        assert(url.first.contains(baseUrl))
        assert(url.first.contains("/oauth/authorize"))
    }

    @Test
    fun `generate with loginAction should contains login_direct_action query parameter`() {
        val url = authorizeUrlGenerator.generate(loginAction = "loginAction")
        val uri = Uri.parse(url.first)
        val loginDirectAction = uri.getQueryParameter("login_direct_action")
        assert(loginDirectAction != null)
        assert(loginDirectAction == "loginAction")
    }

    @Test
    fun `generate with loginAction should contains prompt query parameter`() {
        val url = authorizeUrlGenerator.generate(loginAction = "loginAction")
        val uri = Uri.parse(url.first)
        val prompt = uri.getQueryParameter("prompt")
        assert(prompt != null)
        assert(prompt == "login")
    }

    @Test
    fun `generate with preserveCodeVerifier should call credentialManager_getCodeVerifier`() {
        every { credentialManager.getCodeVerifier() }.returns("CodeVerifier")

        authorizeUrlGenerator.generate(preserveCodeVerifier = true)

        verify { credentialManager.getCodeVerifier() }
    }

    @Test
    fun `generate without preserveCodeVerifier should call credentialManager_saveCodeVerifier`() {
        authorizeUrlGenerator.generate(preserveCodeVerifier = false)

        verify { credentialManager.saveCodeVerifier(any()) }
    }
}
