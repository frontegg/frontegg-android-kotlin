package com.frontegg.android.embedded

import WebResourceCache
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.UrlQuerySanitizer
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import com.frontegg.android.fronteggAuth
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.FronteggState
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.Constants
import com.frontegg.android.utils.Constants.Companion.loginRoutes
import com.frontegg.android.utils.Constants.Companion.socialLoginCallbackRoutes
import com.frontegg.android.utils.Constants.Companion.socialLoginRedirectUrl
import com.frontegg.android.utils.Constants.Companion.successLoginRoutes
import com.frontegg.android.utils.generateErrorPage
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule


class FronteggWebClient(
    val context: Context, val passkeyWebListener: PasskeyWebListener,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) :
    WebViewClient() {
    companion object {
        private val TAG = FronteggWebClient::class.java.simpleName
    }

    private val bgScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private var webViewStatusCode: Int = 200
    private var lastErrorResponse: WebResourceResponse? = null
    private val storage = FronteggInnerStorage()

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "onPageStarted $url")
        FronteggState.isLoading.value = true

        passkeyWebListener.onPageStarted()
        view?.evaluateJavascript(PasskeyWebListener.INJECTED_VAL, null)
    }

    private var lastTimer: TimerTask? = null
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "onPageFinished $url")

        try {
            if (url != null) {
                if (lastTimer != null) {
                    lastTimer?.cancel()
                }
                val urlType = getOverrideUrlType(url.toUri())
                if (urlType == OverrideUrlType.internalRoutes) {
                    lastTimer = Timer().schedule(400) {
                        FronteggState.isLoading.value = false
                    }
                } else {
                    FronteggState.isLoading.value = false
                }
            }
        } catch (e: Exception) {
            FronteggState.isLoading.value = false
        }

        if (url?.startsWith("data:text/html,") == true) {
            FronteggState.isLoading.value = false
            return
        }


        if (webViewStatusCode >= 400) {
            checkIfFronteggError(view, url, lastErrorResponse)
            webViewStatusCode = 200
            lastErrorResponse = null
        } else {

            val nativeModuleFunctions = JSONObject()
            nativeModuleFunctions.put(
                "loginWithSocialLogin",
                storage.handleLoginWithSocialLogin
            )
            nativeModuleFunctions.put(
                "loginWithSocialLoginProvider",
                storage.handleLoginWithSocialLoginProvider
            )
            nativeModuleFunctions.put(
                "loginWithCustomSocialLoginProvider",
                storage.handleLoginWithCustomSocialLoginProvider
            )
            nativeModuleFunctions.put("loginWithSSO", storage.handleLoginWithSSO)
            nativeModuleFunctions.put(
                "shouldPromptSocialLoginConsent",
                storage.shouldPromptSocialLoginConsent
            )
            nativeModuleFunctions.put("useNativeLoader", true)
            val jsObject = nativeModuleFunctions.toString()
            view?.evaluateJavascript("window.FronteggNativeBridgeFunctions = ${jsObject};", null)
        }

    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {

        Log.e(TAG, "onReceivedError: ${error?.description}")
        if (error == null) {
            super.onReceivedError(view, request, error)
            return
        }

        // Check if the error code matches the no network error code.
        if (error.errorCode == ERROR_HOST_LOOKUP || error.errorCode == ERROR_CONNECT || error.errorCode == ERROR_TIMEOUT) {
            // Handle the no network connection error
            Log.d(TAG, "No network connection: ${error.description}")
            try {
                val errorMessage = "Check your internet connection and try again."
                val htmlError = Html.escapeHtml(errorMessage)
                val failedUrl = request?.url?.toString() ?: ""
                val fallbackToAuthUrl = failedUrl.contains("/identity/resources/auth/") ||
                        failedUrl.contains("/oauth/")
                val errorRedirectUrl = if (fallbackToAuthUrl) {
                    AuthorizeUrlGenerator(context).generate().first
                } else {
                    failedUrl
                }
                val errorPage = generateErrorPage(
                    htmlError,
                    error = error.description?.toString(),
                    url = errorRedirectUrl,
                )
                val encodedHtml = Base64.encodeToString(errorPage.toByteArray(), Base64.NO_PADDING)
                Handler(Looper.getMainLooper()).post {
                    view?.loadData(encodedHtml, "text/html", "base64")
                }
                return
            } catch (e: Exception) {
                // ignore error
            }
        }
        super.onReceivedError(view, request, error)
    }

    private fun checkIfFronteggError(
        view: WebView?,
        url: String?,
        errorResponse: WebResourceResponse?
    ) {
        if (view == null || url == null) {
            return
        }
        val status = errorResponse?.statusCode

        if (url.startsWith("${storage.baseUrl}/oauth/authorize")) {
            val reloadScript =
                "setTimeout(()=>window.location.href=\"${url.replace("\"", "\\\"")}\", 4000)"
            val jsCode = "(function(){\n" +
                    "                var script = document.createElement('script');\n" +
                    "                script.innerHTML=`$reloadScript`;" +
                    "                document.body.appendChild(script)\n" +
                    "            })()"
            view.evaluateJavascript(jsCode, null)
            FronteggState.isLoading.value = false
            return
        }


        view.evaluateJavascript("document.body.innerText") { result ->
            try {
                var text = result
                if (text == null) {
                    return@evaluateJavascript
                }


                var json = JsonParser.parseString(text)
                while (!json.isJsonObject) {
                    text = json.asString
                    json = JsonParser.parseString(text)
                }
                val error = json.asJsonObject.get("errors").asJsonArray.map {
                    it.asString
                }.joinToString("\n")


                Log.e(TAG, "Frontegg ERROR: $error")

                val htmlError = Html.escapeHtml(error)
                val errorPage = generateErrorPage(htmlError, status = status)
                val encodedHtml = Base64.encodeToString(errorPage.toByteArray(), Base64.NO_PADDING)
                Handler(Looper.getMainLooper()).post {
                    view.loadData(encodedHtml, "text/html", "base64")
                }

            } catch (e: Exception) {
                // ignore error
            }
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {

        if (view == null || request == null) {
            return
        }
        val requestUrl = request.url
        val authorizeUrlPrefix = "${storage.baseUrl}/oauth/authorize"
        if (view.url == requestUrl.toString()
            || requestUrl.toString().startsWith(authorizeUrlPrefix)
        ) {
            Log.d(TAG, "onReceivedHttpError: Direct load url, ${requestUrl.path}")
            webViewStatusCode = errorResponse?.statusCode ?: 200
            lastErrorResponse = errorResponse
        } else {
            Log.d(TAG, "onReceivedHttpError: HTTP api call, ${request.url?.path}")
        }

        super.onReceivedHttpError(view, request, errorResponse)
    }

    private fun setUriParameter(uri: Uri, key: String, newValue: String): Uri {
        val params: Set<String> = uri.queryParameterNames
        val newUri: Uri.Builder = uri.buildUpon().clearQuery()
        var paramExist = false
        for (param in params) {
            if (param == key) {
                paramExist = true
                newUri.appendQueryParameter(param, newValue)
            } else {
                newUri.appendQueryParameter(param, uri.getQueryParameter(param))
            }
        }
        if (!paramExist) {
            newUri.appendQueryParameter(key, newValue)
        }
        return newUri.build()
    }

    enum class OverrideUrlType {
        HostedLoginCallback,
        SocialOauthPreLogin,
        SocialLoginCallback,
        loginRoutes,
        internalRoutes,
        Unknown
    }

    private fun isSocialLoginPath(string: String): Boolean {
        val patterns = listOf(
            "^/frontegg/identity/resources/auth/[^/]+/user/sso/default/[^/]+/prelogin$",
            "^/identity/resources/auth/[^/]+/user/sso/default/[^/]+/prelogin$"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern)
            if (regex.containsMatchIn(string)) {
                return true
            }
        }

        return false
    }

    private fun getOverrideUrlType(url: Uri): OverrideUrlType {
        val urlPath = url.path
        val urlString = url.toString()
        val hostedLoginCallback = Constants.oauthCallbackUrl(storage.baseUrl)

        if (urlString.startsWith(hostedLoginCallback)) {
            return OverrideUrlType.HostedLoginCallback
        }
        
        // Check for custom scheme social login callback
        if (Constants.isSocialLoginCallbackUrl(urlString)) {
            return OverrideUrlType.SocialLoginCallback
        }
        
        if (urlPath != null && urlString.startsWith(storage.baseUrl)) {

            if (isSocialLoginPath(urlPath)) {
                return OverrideUrlType.SocialOauthPreLogin
            }

            // Check for Android social login callback routes
            if (socialLoginCallbackRoutes.find { u -> urlPath.startsWith(u) } != null) {
                return OverrideUrlType.SocialLoginCallback
            }

            return if (successLoginRoutes.find { u -> urlPath.startsWith(u) } != null) {
                OverrideUrlType.internalRoutes
            } else if (loginRoutes.find { u -> urlPath.startsWith(u) } != null) {
                OverrideUrlType.loginRoutes
            } else {
                OverrideUrlType.internalRoutes
            }
        }

        return OverrideUrlType.Unknown
    }


    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url

        if (url != null) {
            if (url.toString().contains("terms", ignoreCase = true) ||
                url.toString().contains("privacy", ignoreCase = true) ||
                url.toString().endsWith(".pdf", ignoreCase = true)
            ) {
                openExternalBrowser(url)
                return true
            }

            if (url.scheme.equals(storage.deepLinkScheme, ignoreCase = true)) {
                val intent = Intent(Intent.ACTION_VIEW, url)
                context.startActivity(intent)

                (context as? Activity)?.runOnUiThread {
                    (context).setResult(Activity.RESULT_OK)
                    (context).finish()
                }

                return true
            }

            when (getOverrideUrlType(url)) {
                OverrideUrlType.HostedLoginCallback -> {
                    return handleHostedLoginCallback(view, url.query)
                }

                OverrideUrlType.SocialOauthPreLogin -> {
                    if (setSocialLoginRedirectUri(view!!, url)) {
                        return true
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }

                OverrideUrlType.SocialLoginCallback -> {
                    return handleSocialLoginCallback(view, url)
                }
//                OverrideUrlType.Unknown -> {
//                    openExternalBrowser(request.url)
//                    return true
//                }
                else -> {
                    return super.shouldOverrideUrlLoading(view, request)
                }
            }
        }

        return super.shouldOverrideUrlLoading(view, request)
    }

    private val cache = WebResourceCache.getInstance(context)

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {

        if (storage.useDiskCacheWebview) {
            request?.let {
                val url = it.url.toString()
                Log.d(TAG, "shouldInterceptRequest: $url")
                if (cache.shouldCache(url)) {
                    Log.d(TAG, "shouldInterceptRequest: cacheable $url")
                    cache.get(url)?.let { response ->
                        return response
                    }
                } else {
                    Log.d(TAG, "shouldInterceptRequest: not cacheable $url")
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onLoadResource(view: WebView?, url: String?) {
        if (url == null || view == null) {
            super.onLoadResource(view, url)
            return
        }

        val uri = url.toUri()
        val urlType = getOverrideUrlType(uri)
        val query = uri.query
        when (urlType) {
            OverrideUrlType.HostedLoginCallback -> {
                if (handleHostedLoginCallback(view, query)) {
                    return
                }
                super.onLoadResource(view, url)
            }

            OverrideUrlType.SocialOauthPreLogin -> {
                if (setSocialLoginRedirectUri(view, uri)) {
                    return
                }
                super.onLoadResource(view, url)
            }

            else -> {
                super.onLoadResource(view, url)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun setSocialLoginRedirectUri(
        @Suppress("UNUSED_PARAMETER") webView: WebView,
        uri: Uri
    ): Boolean {
        Log.d(TAG, "setSocialLoginRedirectUri setting redirect uri for social login")

        if (uri.getQueryParameter("redirectUri") != null) {
            Log.d(TAG, "redirectUri exist, forward navigation to webView")
            return false
        }
        val baseUrl = storage.baseUrl
        val oauthRedirectUri = socialLoginRedirectUrl(baseUrl)
        val newUri = setUriParameter(uri, "redirectUri", oauthRedirectUri)

        FronteggState.isLoading.value = true
        try {
            bgScope.launch {
                val requestBuilder = Request.Builder()
                requestBuilder.method("GET", null)
                requestBuilder.url(newUri.toString())

                val client = OkHttpClient().newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                val response = client.newCall(requestBuilder.build()).execute()

                val redirect = response.headers["Location"]?.toUri()
                if (redirect == null) {
                    Log.e(TAG, "setSocialLoginRedirectUri failed to generate social login url")
                    return@launch
                }
                val socialLoginUrl = setUriParameter(redirect, "external", "true")

                withContext(mainDispatcher) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, socialLoginUrl)
                    context.startActivity(browserIntent)
                    FronteggState.isLoading.value = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setSocialLoginRedirectUri failed to generate social login url")
//            webView.loadUrl(newUri.toString())
        }
        return true
    }

    private fun openExternalBrowser(externalLink: Uri) {
        val browserIntent = Intent(Intent.ACTION_VIEW, externalLink)
        context.startActivity(browserIntent)
    }


    private fun handleHostedLoginCallback(webView: WebView?, query: String?): Boolean {
        Log.d(TAG, "handleHostedLoginCallback received query: $query")
        if (query == null || webView == null) {
            Log.d(
                TAG,
                "handleHostedLoginCallback failed of nullable value, query: $query, webView: $webView"
            )
            return false
        }

        val querySanitizer = UrlQuerySanitizer()
        querySanitizer.allowUnregisteredParamaters = true
        querySanitizer.parseQuery(query)
        val code = querySanitizer.getValue("code")

        if (code == null) {
            Log.d(TAG, "handleHostedLoginCallback failed of nullable code value, query: $query")
            return false
        }

        if ((context.fronteggAuth as FronteggAuthService).handleHostedLoginCallback(
                code,
                webView
            )
        ) {
            return true
        }

        val authorizeUrl = AuthorizeUrlGenerator(context)
        val url = authorizeUrl.generate()
        webView.loadUrl(url.first)
        return false
    }

    private fun handleSocialLoginCallback(webView: WebView?, url: Uri): Boolean {
        Log.d(TAG, "handleSocialLoginCallback received url: $url")
        if (webView == null) {
            Log.d(TAG, "handleSocialLoginCallback failed: webView is null")
            return false
        }

        val query = url.query
        if (query == null) {
            Log.d(TAG, "handleSocialLoginCallback failed: query is null")
            return false
        }

        val querySanitizer = UrlQuerySanitizer()
        querySanitizer.allowUnregisteredParamaters = true
        querySanitizer.parseQuery(query)

        val state = querySanitizer.getValue("state")
        val code = querySanitizer.getValue("code")
        val idToken = querySanitizer.getValue("id_token")

        if (state == null) {
            Log.d(TAG, "handleSocialLoginCallback failed: state is null")
            return false
        }

        // Parse state to get provider information
        try {
            val stateJson = JsonParser.parseString(state).asJsonObject
            val provider = stateJson.get("provider")?.asString
            val action = stateJson.get("action")?.asString

            if (provider == null || action != "login") {
                Log.d(TAG, "handleSocialLoginCallback failed: invalid state - provider: $provider, action: $action")
                return false
            }

            Log.d(TAG, "handleSocialLoginCallback processing for provider: $provider")

            // Call FronteggAuthService to handle the social login callback
            if ((context.fronteggAuth as FronteggAuthService).handleSocialLoginCallback(
                    url.toString(),
                    code,
                    idToken,
                    state
                )
            ) {
                return true
            }

        } catch (e: Exception) {
            Log.e(TAG, "handleSocialLoginCallback failed to parse state: $state", e)
        }

        return false
    }

}