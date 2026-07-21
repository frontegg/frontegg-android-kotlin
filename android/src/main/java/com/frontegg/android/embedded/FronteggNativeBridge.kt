package com.frontegg.android.embedded

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.browser.customtabs.CustomTabsIntent
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

    /**
     * Same-origin gate for the @JavascriptInterface methods (FR-25935). The bridge is
     * injected for every page load, so only content served from the Frontegg base URL may
     * drive native login/SSO/social intents or the loader. If the WebView is navigated or
     * redirected elsewhere, its scripts must not reach these methods.
     */
    private fun isFromTrustedOrigin(action: String): Boolean {
        val trusted = isTrustedOrigin(webClient?.currentUrlMainSafe(), context.fronteggAuth.baseUrl)
        if (!trusted) {
            Log.e("FronteggNativeBridge", "$action refused — untrusted origin")
        }
        return trusted
    }

    @JavascriptInterface
    fun loginWithSSO(email: String) {
        if (!isFromTrustedOrigin("loginWithSSO")) return
        Log.d("FronteggNativeBridge", "loginWithSSO(${email})")
        val generatedUrl = AuthorizeUrlGenerator(context).generate(email)
        val authorizationUrl = Uri.parse(generatedUrl.first)
        val browserIntent = Intent(Intent.ACTION_VIEW, authorizationUrl)
        browserIntent.addCategory(Intent.CATEGORY_BROWSABLE)
        context.startActivity(browserIntent)
    }


    // Native token bridge (getTokens): lets the embedded login box (e.g. step-up /
    // re-auth) bootstrap from the existing native session instead of the cookie
    // token-refresh that 401s in the WebView, mirroring the Admin Portal bridge.
    // Resolves into the redux-store window.FronteggNativeBridgeCallbacks registry.
    // Dispatchers.IO is used directly here (mirrors DispatcherProvider); this bridge is
    // constructed by the WebView setup, not via DI, so there's no dispatcher to inject.
    @Suppress("InjectDispatcher")
    @JavascriptInterface
    fun getTokens(callbackId: String) {
        Log.i("FronteggNativeBridge", "getTokens called (callbackId=$callbackId)")
        val client = webClient
        if (client == null) {
            Log.e("FronteggNativeBridge", "getTokens: no webClient available")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            if (!isTrustedOrigin(client.currentUrlMainSafe(), context.fronteggAuth.baseUrl)) {
                Log.e("FronteggNativeBridge", "getTokens refused — untrusted origin")
                client.evaluateJavascriptOnMain(rejectCallbackJs(callbackId, "untrusted_origin"))
                return@launch
            }
            try {
                context.fronteggAuth.refreshTokenAndWait()
            } catch (_: Throwable) {
            }
            val auth = context.fronteggAuth
            val access = auth.accessToken.value
            val refresh = auth.refreshToken.value
            if (access.isNullOrEmpty() || refresh.isNullOrEmpty()) {
                client.evaluateJavascriptOnMain(rejectCallbackJs(callbackId, "no_tokens"))
                return@launch
            }
            val json = JSONObject()
                .put("accessToken", access)
                .put("refreshToken", refresh)
                .toString()
            client.evaluateJavascriptOnMain(resolveCallbackJs(callbackId, json))
        }
    }

    companion object {

        /** Same-origin check (scheme + host + port) gating the getTokens bridge. */
        internal fun isTrustedOrigin(currentUrl: String?, baseUrl: String): Boolean {
            val current = originOf(currentUrl) ?: return false
            val trusted = originOf(baseUrl) ?: return false
            return current == trusted
        }

        internal fun originOf(url: String?): String? {
            if (url == null) return null
            return try {
                val u = Uri.parse(url)
                if (u.scheme == null || u.host == null) null else "${u.scheme}://${u.host}:${u.port}"
            } catch (_: Throwable) {
                null
            }
        }

        /** JS that resolves the redux-store getTokens callback with the native tokens. */
        internal fun resolveCallbackJs(callbackId: String, json: String): String =
            "(function(){var r=window.FronteggNativeBridgeCallbacks;" +
                "if(r&&r['$callbackId']){r['$callbackId'].resolve($json);delete r['$callbackId'];}})();"

        /** JS that rejects the redux-store getTokens callback (single quotes escaped). */
        internal fun rejectCallbackJs(callbackId: String, message: String): String {
            val safe = message.replace("\\", "\\\\").replace("'", "\\'")
            return "(function(){var r=window.FronteggNativeBridgeCallbacks;" +
                "if(r&&r['$callbackId']){r['$callbackId'].reject('$safe');delete r['$callbackId'];}})();"
        }

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

            // If context is EmbeddedAuthActivity, don't open browser - let the Activity load URL in WebView
            // This ensures passkey support works correctly
            val isEmbeddedAuthActivity = context is Activity && 
                context.javaClass.simpleName == "EmbeddedAuthActivity"

            if (isEmbeddedAuthActivity) {
                // Don't open browser - EmbeddedAuthActivity will load the URL in its WebView
                Log.d("FronteggNativeBridge", "Context is EmbeddedAuthActivity, skipping browser launch - URL will be loaded in WebView")
                return
            }

            // For other contexts, use existing logic (Chrome Custom Tabs or browser)
            val storage = FronteggInnerStorage()
            if (storage.useChromeCustomTabs) {
                val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(false).build()
                customTabsIntent.intent.setPackage("com.android.chrome")
                customTabsIntent.intent.setData(authorizationUrl)
                (context as Activity).startActivity(customTabsIntent.intent)
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
        if (!isFromTrustedOrigin("loginWithSocialLogin")) return
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
        if (!isFromTrustedOrigin("loginWithSocialLoginProvider")) return
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
        if (!isFromTrustedOrigin("loginWithCustomSocialLoginProvider")) return
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
        if (!isFromTrustedOrigin("setLoading")) return
        Log.d("FronteggNativeBridge", "setLoading(${if (value) "true" else "false"})")
        FronteggState.webLoading.value = value
    }

    @JavascriptInterface
    override fun toString(): String {
        return "injectedObject"
    }
}