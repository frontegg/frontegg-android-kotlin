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
import com.frontegg.android.FronteggApp
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.google.androidbrowserhelper.trusted.LauncherActivity
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

    @JavascriptInterface
    fun loginWithSocialLogin(socialLoginUrl: String) {
        Log.d("FronteggNativeBridge", "loginWithSocialLogin(${socialLoginUrl})")

        val directLogin: Map<String, Any> = mapOf(
            "type" to "direct",
            "data" to socialLoginUrl
        )

        val generatedUrl = try {
            val jsonData = JSONObject(directLogin).toString().toByteArray(Charsets.UTF_8)
            val jsonString = Base64.encodeToString(jsonData, Base64.DEFAULT)
            AuthorizeUrlGenerator().generate(null, jsonString, true)
        } catch (e: JSONException) {
            AuthorizeUrlGenerator().generate(null, null, true)
        }

        val authorizationUrl = Uri.parse(generatedUrl.first)

        if(FronteggApp.getInstance().useChromeCustomTabs) {
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

    @JavascriptInterface
    override fun toString(): String {
        return "injectedObject"
    }
}