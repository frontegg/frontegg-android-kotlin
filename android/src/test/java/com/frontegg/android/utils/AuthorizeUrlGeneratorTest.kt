package com.frontegg.android.utils

import android.net.Uri
import com.frontegg.android.FronteggApp
import com.frontegg.android.services.CredentialManager
import io.mockk.every
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
    private val baseUrl = "https://base.url.com/"
    private val clientId = "Test Client Id"
    @Before
    fun setUp() {
        fronteggApp = mockkClass(FronteggApp::class)
        credentialManager = mockkClass(CredentialManager::class)
        mockkObject(FronteggApp.Companion)
        every { FronteggApp.getInstance() }.returns(fronteggApp)
        every { fronteggApp.clientId }.returns(clientId)
        every { fronteggApp.applicationId }.returns(null)
        every { fronteggApp.baseUrl }.returns(baseUrl)
        every { fronteggApp.packageName }.returns("test.com")
        every { fronteggApp.useAssetsLinks }.returns(false)
        every { fronteggApp.credentialManager }.returns(credentialManager)
        every { credentialManager.saveCodeVerifier(any()) }.returns(true)

        authorizeUrlGenerator = AuthorizeUrlGenerator()
    }

    @Test
    fun `generate should contains base url and oauth_logout path`() {
        val url = authorizeUrlGenerator.generate()

        assert(url.first.contains(baseUrl))
        assert(url.first.contains("/oauth/logout"))
    }

    @Test
    fun `generate should return url with post_logout_redirect_uri query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)
    }

    @Test
    fun `post_logout_redirect_uri query parameter should contains baseUrl and oauth_authorize path`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)
        assert(postLogoutRedirectUri!!.contains(baseUrl))
        assert(postLogoutRedirectUri.contains("/oauth/authorize"))
    }

    @Test
    fun `post_logout_redirect_uri query parameter should contains response_type query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)

        val responseType = Uri.parse(postLogoutRedirectUri!!).getQueryParameter("response_type")
        assert(responseType != null)
        assert(responseType == "code")
    }

    @Test
    fun `post_logout_redirect_uri query parameter should contains client_id query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)

        val clientId = Uri.parse(postLogoutRedirectUri!!).getQueryParameter("client_id")
        assert(clientId != null)
        assert(clientId == clientId)
    }

    @Test
    fun `client_id query parameter should be applicationId if applicationId not null`() {
        every { fronteggApp.applicationId }.returns("Test Application Id")
        val url = AuthorizeUrlGenerator().generate()
        every { fronteggApp.applicationId }.returns(null)
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)

        val clientId = Uri.parse(postLogoutRedirectUri!!).getQueryParameter("client_id")
        assert(clientId != null)
        assert(clientId == "Test Application Id")
    }

    @Test
    fun `post_logout_redirect_uri query parameter should contains scope query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)

        val scope = Uri.parse(postLogoutRedirectUri!!).getQueryParameter("scope")
        assert(scope != null)
        assert(scope == "openid email profile")
    }

    @Test
    fun `post_logout_redirect_uri query parameter should contains redirect_uri query parameter`() {
        mockkObject(Constants.Companion)
        every { Constants.oauthCallbackUrl(baseUrl) }.returns("oauthCallbackUrl")

        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)

        val redirectUri = Uri.parse(postLogoutRedirectUri!!).getQueryParameter("redirect_uri")
        assert(redirectUri != null)
        assert(redirectUri == "oauthCallbackUrl")
    }

    @Test
    fun `post_logout_redirect_uri query parameter should contains code_challenge query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)

        val codeChallenge = Uri.parse(postLogoutRedirectUri!!).getQueryParameter("code_challenge")
        assert(codeChallenge != null)
    }

    @Test
    fun `post_logout_redirect_uri query parameter should contains code_challenge_method query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)

        val codeChallengeMethod = Uri.parse(postLogoutRedirectUri!!).getQueryParameter("code_challenge_method")
        assert(codeChallengeMethod != null)
        assert(codeChallengeMethod == "S256")
    }

    @Test
    fun `post_logout_redirect_uri query parameter should contains nonce query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)

        val nonce = Uri.parse(postLogoutRedirectUri!!).getQueryParameter("nonce")
        assert(nonce != null)
    }

    @Test
    fun `post_logout_redirect_uri query parameter should not contains loginHint query parameter`() {
        val url = authorizeUrlGenerator.generate()
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)

        val loginHint = Uri.parse(postLogoutRedirectUri!!).getQueryParameter("login_hint")
        assert(loginHint == null)
    }

    @Test
    fun `post_logout_redirect_uri query parameter should contains loginHint query parameter if pass value to generate call`() {
        val url = authorizeUrlGenerator.generate(loginHint = "loginHint@mail.com")
        val uri = Uri.parse(url.first)

        val postLogoutRedirectUri = uri.getQueryParameter("post_logout_redirect_uri")
        assert(postLogoutRedirectUri != null)

        val loginHint = Uri.parse(postLogoutRedirectUri!!).getQueryParameter("login_hint")
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
