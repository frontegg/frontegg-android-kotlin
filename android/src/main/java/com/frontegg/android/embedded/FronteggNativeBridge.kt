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
import com.frontegg.android.fronteggAuth
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.FronteggState
import com.frontegg.android.utils.AuthorizeUrlGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject


class FronteggNativeBridge(val context: Context, private val webClient: FronteggWebClient? = null) {

    private fun getFormAction(): String {
        // Get form action from web client if available, otherwise use default
        return webClient?.getFormAction() ?: "login"
    }

    @JavascriptInterface
    fun loginWithSSO(email: String) {
        Log.d("FronteggNativeBridge", "loginWithSSO(${email})")
        val generatedUrl = AuthorizeUrlGenerator(context).generate(email)
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
                val formAction = directLogin["action"] as? String
                AuthorizeUrlGenerator(context).generate(null, jsonString, preserveCodeVerifier, null, null, formAction)
            } catch (e: JSONException) {
                val formAction = directLogin["action"] as? String
                AuthorizeUrlGenerator(context).generate(null, null, preserveCodeVerifier, null, null, formAction)
            }

            val authorizationUrl = Uri.parse(generatedUrl.first)

            if (FronteggInnerStorage().useChromeCustomTabs) {
                val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(false).build()
                customTabsIntent.intent.setPackage("com.android.chrome")
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
    @Suppress("UNUSED")
    fun loginWithSocialLogin(socialLoginUrl: String) {
        Log.d("FronteggNativeBridge", "loginWithSocialLogin(${socialLoginUrl})")

        val directLogin: Map<String, Any> = mapOf(
            "type" to "direct",
            "data" to socialLoginUrl
        )

        directLoginWithContext(context, directLogin, true)
    }

    @JavascriptInterface
    @Suppress("UNUSED")
    fun loginWithSocialLoginProvider(provider: String) {
        Log.d("FronteggNativeBridge", "loginWithSocialLoginProvider(${provider})")

        val formAction = getFormAction()
        
        // Check if legacy social login flow is enabled
        val useLegacySocialLoginFlow = try {
            val configCache = com.frontegg.android.init.ConfigCache.loadLastRegionsInit(context)
            val flag = configCache?.second?.useLegacySocialLoginFlow ?: false
            Log.d("FronteggNativeBridge", "useLegacySocialLoginFlow = $flag")
            flag
        } catch (e: Exception) {
            Log.w("FronteggNativeBridge", "Failed to check legacy social login flow flag", e)
            false
        }

        if (useLegacySocialLoginFlow) {
            // Use legacy implementation
            Log.d("FronteggNativeBridge", "Using legacy social login flow for $provider")
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    val directLogin: Map<String, Any> = mapOf(
                        "type" to "social-login",
                        "data" to provider,
                        "action" to formAction
                    )
                    directLoginWithContext(context, directLogin, true)
                } catch (e: Exception) {
                    Log.e("FronteggNativeBridge", "Failed to get form action", e)
                }
            }
        } else {
            // Use new social login implementation
            Log.d("FronteggNativeBridge", "Using new social login flow for $provider")
            val socialLoginProvider = com.frontegg.android.models.SocialLoginProvider.fromString(provider)
            Log.d("FronteggNativeBridge", "Parsed provider: $socialLoginProvider")
            if (socialLoginProvider != null) {
                kotlinx.coroutines.CoroutineScope(com.frontegg.android.utils.DefaultDispatcherProvider.io).launch {
                    try {
                        val socialLoginAction = com.frontegg.android.models.SocialLoginAction.fromString(formAction) ?: com.frontegg.android.models.SocialLoginAction.LOGIN
                        Log.d("FronteggNativeBridge", "Social login action: $socialLoginAction")
                        
                        val authService = context.fronteggAuth as com.frontegg.android.services.FronteggAuthService
                        authService.loginWithSocialLoginProvider(context as android.app.Activity, socialLoginProvider, socialLoginAction)
                    } catch (e: Exception) {
                        Log.e("FronteggNativeBridge", "Failed to start social login", e)
                    }
                }
            } else {
                Log.w("FronteggNativeBridge", "Unknown social login provider: $provider, falling back to legacy")
                // Fallback to legacy implementation
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    try {
                        val directLogin: Map<String, Any> = mapOf(
                            "type" to "social-login",
                            "data" to provider,
                            "action" to formAction
                        )
                        directLoginWithContext(context, directLogin, true)
                    } catch (e: Exception) {
                        Log.e("FronteggNativeBridge", "Failed to get form action", e)
                    }
                }
            }
        }
    }

    @JavascriptInterface
    @Suppress("UNUSED")
    fun loginWithCustomSocialLoginProvider(provider: String) {
        Log.d("FronteggNativeBridge", "loginWithCustomSocialLoginProvider(${provider})")

        val formAction = getFormAction()
        val directLogin: Map<String, Any> = mapOf(
            "type" to "custom-social-login",
            "data" to provider,
            "action" to formAction
        )

        directLoginWithContext(context, directLogin, true)
    }

    @JavascriptInterface
    fun setLoading(value: Boolean) {
        Log.d("FronteggNativeBridge", "setLoading(${if (value) "true" else "false"})")
        FronteggState.webLoading.value = value
    }

    @JavascriptInterface
    override fun toString(): String {
        return "injectedObject"
    }
}