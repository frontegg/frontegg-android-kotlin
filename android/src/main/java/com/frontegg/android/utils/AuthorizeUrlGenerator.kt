package com.frontegg.android.utils

import android.net.Uri
import android.util.Log
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.StorageProvider
import java.security.MessageDigest
import java.util.Base64
import kotlin.time.Duration

class AuthorizeUrlGenerator {
    companion object {
        private val TAG = AuthorizeUrlGenerator::class.java.simpleName
    }

    private var storage = StorageProvider.getInnerStorage()
    private val clientId: String
        get() = storage.clientId
    private val applicationId: String?
        get() = storage.applicationId
    private val baseUrl: String
        get() = storage.baseUrl

    private fun createRandomString(length: Int = 16): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    private fun generateCodeChallenge(codeVerifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = codeVerifier.toByteArray()
        val bytes = md.digest(input)
        val digest = Base64.getEncoder().encodeToString(bytes)

        return digest
            .replace("=", "")
            .replace("+", "-")
            .replace("/", "_")

    }


    fun generate(
        loginHint: String? = null,
        loginAction: String? = null,
        preserveCodeVerifier: Boolean? = false,
        stepUp: Boolean? = null,
        maxAge: Duration? = null,
    ): Pair<String, String> {
        val nonce = createRandomString()
        val credentialManager = FronteggAuthService.instance.credentialManager


        val codeVerifier: String = if (preserveCodeVerifier == true) {
            credentialManager.getCodeVerifier()!!
        } else {
            val code = createRandomString()
            credentialManager.saveCodeVerifier(code)
            code
        }

        val codeChallenge = generateCodeChallenge(codeVerifier)

        val redirectUrl = Constants.oauthCallbackUrl(baseUrl)

        val authorizeUrlBuilder = Uri.Builder()
            .encodedPath(baseUrl)
            .appendEncodedPath("oauth/authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", applicationId ?: clientId)
            .appendQueryParameter("scope", "openid email profile")
            .appendQueryParameter("redirect_uri", redirectUrl)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("nonce", nonce)

        if (stepUp == true) {
            authorizeUrlBuilder.appendQueryParameter(
                "acr_values",
                "http://schemas.openid.net/pape/policies/2007/06/multi-factor"
            )
            if (maxAge != null) {
                authorizeUrlBuilder.appendQueryParameter(
                    "max-age",
                    maxAge.inWholeSeconds.toString()
                )
            }
        }

        if (loginHint != null) {
            authorizeUrlBuilder.appendQueryParameter("login_hint", loginHint)
        }

        if (loginAction != null) {
            authorizeUrlBuilder.appendQueryParameter("login_direct_action", loginAction)
            authorizeUrlBuilder.appendQueryParameter("prompt", "login")
            return Pair(authorizeUrlBuilder.build().toString(), codeVerifier)
        }

        val url = authorizeUrlBuilder.build().toString()
        Log.d(TAG, "Generated url: $url")

        if (stepUp == true) {
            return Pair(url, codeVerifier)
        }

        val authorizeUrl = Uri.Builder()
            .encodedPath(baseUrl)
            .appendEncodedPath("oauth/logout")
            .appendQueryParameter("post_logout_redirect_uri", url)
            .build().toString()

        return Pair(authorizeUrl, codeVerifier)
    }
}


