package com.frontegg.android.embedded

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.browser.customtabs.CustomTabsIntent
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.utils.AuthorizeUrlGenerator
import org.json.JSONException
import org.json.JSONObject


class FronteggNativeBridge(val context: Context) {

    @JavascriptInterface
    fun loginWithSSO(email: String) {
        Log.d("FronteggNativeBridge", "loginWithSSO(${email})")
        val generatedUrl = AuthorizeUrlGenerator().generate(email)
        val authorizationUrl = Uri.parse(generatedUrl.first)
        val browserIntent = Intent(Intent.ACTION_VIEW, authorizationUrl)
        browserIntent.addCategory(Intent.CATEGORY_BROWSABLE)
        context.startActivity(browserIntent)
    }


    companion object {
        fun directLoginWithContext(
            context: Context,
            directLogin: Map<String, Any>,
            preserveCodeVerifier: Boolean
        ) {

            val generatedUrl = try {
                val jsonData = JSONObject(directLogin)
                val additionalQueryParams = JSONObject()
                additionalQueryParams.put("prompt", "consent")
                jsonData.put("additionalQueryParams", additionalQueryParams)
                val loginAction = jsonData.toString().toByteArray(Charsets.UTF_8)

                val jsonString = Base64.encodeToString(loginAction, Base64.NO_WRAP)
                AuthorizeUrlGenerator().generate(null, jsonString, preserveCodeVerifier)
            } catch (e: JSONException) {
                AuthorizeUrlGenerator().generate(null, null, preserveCodeVerifier)
            }

            val authorizationUrl = Uri.parse(generatedUrl.first)

            if (FronteggInnerStorage().useChromeCustomTabs) {
                val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(false).build()
                customTabsIntent.intent.setPackage("com.android.chrome");
                customTabsIntent.intent.setData(authorizationUrl)
                (context as Activity).startActivityForResult(
                    customTabsIntent.intent,
                    EmbeddedAuthActivity.OAUTH_LOGIN_REQUEST
                )
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, authorizationUrl)
                browserIntent.addCategory(Intent.CATEGORY_BROWSABLE)
                context.startActivity(browserIntent)
            }
        }
    }

    @JavascriptInterface
    fun loginWithSocialLogin(socialLoginUrl: String) {
        Log.d("FronteggNativeBridge", "loginWithSocialLogin(${socialLoginUrl})")

        val directLogin: Map<String, Any> = mapOf(
            "type" to "direct",
            "data" to socialLoginUrl
        )

        directLoginWithContext(context, directLogin, true)
    }

    @JavascriptInterface
    fun loginWithSocialLoginProvider(provider: String) {
        Log.d("FronteggNativeBridge", "loginWithSocialLoginProvider(${provider})")

        val directLogin: Map<String, Any> = mapOf(
            "type" to "social-login",
            "data" to provider
        )

        directLoginWithContext(context, directLogin, true)
    }

    @JavascriptInterface
    fun loginWithCustomSocialLoginProvider(provider: String) {
        Log.d("FronteggNativeBridge", "loginWithCustomSocialLoginProvider(${provider})")

        val directLogin: Map<String, Any> = mapOf(
            "type" to "custom-social-login",
            "data" to provider
        )

        directLoginWithContext(context, directLogin, true)
    }

    @JavascriptInterface
    fun setLoading(value: Boolean) {
        Log.d("FronteggNativeBridge", "setLoading(${if (value) "true" else "false"})")
        FronteggAuthService.instance.webLoading.value = value
    }

    @JavascriptInterface
    override fun toString(): String {
        return "injectedObject"
    }
}