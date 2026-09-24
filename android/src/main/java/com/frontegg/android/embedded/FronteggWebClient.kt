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
import android.webkit.RenderProcessGoneDetail
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
import com.frontegg.android.utils.AppIdHeaderHelper
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.Constants
import com.frontegg.android.utils.Constants.Companion.loginRoutes
import com.frontegg.android.utils.Constants.Companion.socialLoginRedirectUrl
import com.frontegg.android.utils.Constants.Companion.successLoginRoutes
import com.frontegg.android.utils.LogUrlSanitizer
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

        /**
         * Builds the `window.FronteggNativeBridgeFunctions` capability map injected into
         * the embedded login WebView. `getTokens` lets the login box (step-up / re-auth)
         * bootstrap from native tokens instead of the cookie refresh that 401s.
         */
        internal fun buildNativeBridgeFunctions(storage: FronteggInnerStorage): JSONObject {
            return JSONObject().apply {
                put("loginWithSocialLogin", storage.handleLoginWithSocialLogin)
                put("loginWithSocialLoginProvider", storage.handleLoginWithSocialLoginProvider)
                put("loginWithCustomSocialLoginProvider", storage.handleLoginWithCustomSocialLoginProvider)
                put("loginWithSSO", storage.handleLoginWithSSO)
                put("shouldPromptSocialLoginConsent", storage.shouldPromptSocialLoginConsent)
                put("useNativeLoader", true)
                put("getTokens", true)
            }
        }
    }

    private val bgScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private var webViewStatusCode: Int = 200
    private var lastErrorResponse: WebResourceResponse? = null
    private val storage = FronteggInnerStorage()
    private var currentWebView: WebView? = null

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        // If we don't handle this, Android may kill the entire app process:
        // "Render process ... wasn't handled by all associated webviews, killing application."
        val didCrash = try { detail?.didCrash() } catch (_: Throwable) { null }
        val priorityAtExit = try { detail?.rendererPriorityAtExit() } catch (_: Throwable) { null }
        Log.e(TAG, "WebView renderer process gone. didCrash=$didCrash priorityAtExit=$priorityAtExit")

        // Stop any loaders/spinners
        try {
            FronteggState.isLoading.value = false
            FronteggState.showLoader.value = false
            FronteggState.webLoading.value = false
        } catch (_: Throwable) {
            // best-effort
        }

        currentWebView = null

        // Clean up the dead WebView on the main thread and clear the stored reference in the auth service
        Handler(Looper.getMainLooper()).post {
            try {
                (context.fronteggAuth as? FronteggAuthService)?.webview = null
            } catch (_: Throwable) {
                // best-effort
            }

            try {
                view?.stopLoading()
                view?.loadUrl("about:blank")
                view?.clearHistory()
                view?.removeAllViews()
                view?.destroy()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to cleanup WebView after renderer crash", t)
            }
        }

        // We handled it — do not let the system kill our app process.
        return true
    }

    fun getFormAction(): String {
        // Ensure WebView access on main thread
        return try {
            var result = "login"
            if (Looper.myLooper() == Looper.getMainLooper()) {
                val url = currentWebView?.url
                result = if (url?.contains("/oauth/account/sign-up") == true) "signUp" else "login"
            } else {
                val latch = java.util.concurrent.CountDownLatch(1)
                Handler(Looper.getMainLooper()).post {
                    try {
                        val url = currentWebView?.url
                        result = if (url?.contains("/oauth/account/sign-up") == true) "signUp" else "login"
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to get formAction from URL on main thread, using default", t)
                    } finally {
                        latch.countDown()
                    }
                }
                latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get formAction from URL, using default", e)
            "login"
        }
    }

    /** Main-thread-safe read of the current page URL (used by the getTokens bridge). */
    internal fun currentUrlMainSafe(): String? {
        return try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                currentWebView?.url
            } else {
                var result: String? = null
                val latch = java.util.concurrent.CountDownLatch(1)
                Handler(Looper.getMainLooper()).post {
                    try {
                        result = currentWebView?.url
                    } finally {
                        latch.countDown()
                    }
                }
                latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                result
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Evaluate JS on the embedded WebView on the main thread (used for bridge callbacks). */
    internal fun evaluateJavascriptOnMain(js: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                currentWebView?.evaluateJavascript(js, null)
            } catch (_: Throwable) {
            }
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        
        FronteggState.isLoading.value = true

        // Store reference to current WebView
        currentWebView = view

        passkeyWebListener.onPageStarted()
        view?.evaluateJavascript(PasskeyWebListener.INJECTED_VAL, null)
    }

    private var lastTimer: TimerTask? = null
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        

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

        // Handle social login success redirect (like iOS)
        if (url?.contains("/oauth/account/social/success") == true) {
            handleSocialLoginSuccessRedirect(view, url)
            return
        }



        if (webViewStatusCode >= 400) {
            checkIfFronteggError(view, url, lastErrorResponse)
            webViewStatusCode = 200
            lastErrorResponse = null
        } else {

            val jsObject = buildNativeBridgeFunctions(storage).toString()
            view?.evaluateJavascript("window.FronteggNativeBridgeFunctions = ${jsObject};", null)
        }

    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {

        Log.e(TAG, "onReceivedError: ${error?.description} url=${request?.url} mainFrame=${request?.isForMainFrame}")
        if (error == null) {
            super.onReceivedError(view, request, error)
            return
        }

        // Check if the error code matches the no network error code.
        if (error.errorCode == ERROR_HOST_LOOKUP || error.errorCode == ERROR_CONNECT || error.errorCode == ERROR_TIMEOUT) {
            // Handle the no network connection error
            
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
            
            webViewStatusCode = errorResponse?.statusCode ?: 200
            lastErrorResponse = errorResponse
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
        val hostedLoginCallback = Constants.oauthCallbackUrl(storage.baseUrl)

        if (url.toString().startsWith(hostedLoginCallback)) {
            return OverrideUrlType.HostedLoginCallback
        }
        if (urlPath != null && url.toString().startsWith(storage.baseUrl)) {

            if (isSocialLoginPath(urlPath)) {
                return OverrideUrlType.SocialOauthPreLogin
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

            // An `http(s)` link the host put in the login box footer — typically the
            // Privacy Policy / Terms attribution Google's terms require when the
            // reCAPTCHA badge is hidden. This WebView has no navigation chrome, so
            // loading it in place would strand the user with no way back.
            //
            // Explicit rather than relying on the "terms"/"privacy" substring check
            // above: that one is a coincidence for these URLs (it matches anywhere in
            // the string, including a tenant name), and a footer link must keep working
            // if it is ever narrowed. Unlike the hand-off below, this does NOT dismiss
            // the box — the user reads the policy and comes back to a login screen
            // still in place.
            if (isFooterExternalUrl(url)) {
                openExternalBrowser(url)
                return true
            }

            // A footer link on the host's own scheme is a hand-off: dismissed the same
            // way as the OAuth callback, handing the URL to the OS so the host app can
            // present the flow.
            //
            // Needed as a separate condition because `deepLinkScheme` is optional —
            // an app that never configures one leaves it null, and then WebView has no
            // handler for a custom scheme at all: the navigation fails with
            // ERR_UNKNOWN_URL_SCHEME and the box just sits there. Unlike iOS, whose
            // WKWebView navigation delegate routes any non-http scheme out to the OS,
            // Android only does what this method says.
            val isFooterHandoff = isFooterHandoffUrl(url)
            if (url.scheme.equals(storage.deepLinkScheme, ignoreCase = true) ||
                isFooterHandoff
            ) {
                val intent = Intent(Intent.ACTION_VIEW, url)
                if (isFooterHandoff) {
                    // Scoped to this app. A custom scheme is not exclusive: any app can
                    // register it, and several build variants of the same app usually do
                    // (observed as an "Open with" chooser between two installed
                    // flavours). Without this the hand-off is both a UX dead end and a
                    // hijacking surface.
                    intent.setPackage(context.packageName)
                }
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

    /**
     * Whether [url] is a login box footer link on one of the host app's own schemes,
     * i.e. a hand-off that should dismiss the box.
     *
     * Compared by scheme rather than by exact string because the WebView normalises the
     * URI on its way here — an `<a href="myapp://sign-up">` arrives as
     * `myapp://sign-up/` — so an exact match would miss. Restricted to non-`http(s)`
     * schemes; an `http(s)` footer link is handled by [isFooterExternalUrl] instead,
     * which keeps the box mounted.
     *
     * Schemes reaching here have already been checked against the host's own declared
     * intent filters by `LoginBoxFooter.sanitizedFooter`.
     */
    private fun isFooterHandoffUrl(url: Uri): Boolean {
        val scheme = url.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") return false

        return footerLinkSchemes().contains(scheme)
    }

    /** Non-`http(s)` schemes used by the host's footer links. */
    private fun footerLinkSchemes(): Set<String> {
        val footer = storage.loginBoxFooter ?: return emptySet()
        val sanitized = LoginBoxFooter.sanitizedFooter(footer) { candidate ->
            LoginBoxFooter.hostAppHandles(context, candidate)
        } ?: return emptySet()

        val rows = sanitized.optJSONArray("rows") ?: return emptySet()
        val schemes = mutableSetOf<String>()
        for (i in 0 until rows.length()) {
            val segments = rows.optJSONObject(i)?.optJSONArray("segments") ?: continue
            for (j in 0 until segments.length()) {
                val link = segments.optJSONObject(j)?.optString("url").orEmpty()
                if (link.isEmpty()) continue
                val scheme = Uri.parse(link).scheme?.lowercase() ?: continue
                if (scheme != "http" && scheme != "https") schemes.add(scheme)
            }
        }
        return schemes
    }

    /**
     * Whether [url] is one of the `http(s)` links the host put in the login box footer.
     *
     * An exact allowlist rather than a general "host differs from the auth origin" rule,
     * because the box legitimately navigates off-origin to social identity providers.
     * A trailing slash is ignored on both sides, since the WebView adds one to a bare
     * origin.
     */
    private fun isFooterExternalUrl(url: Uri): Boolean {
        val configured = LoginBoxFooter.footerExternalUrls(storage.loginBoxFooter)
        if (configured.isEmpty()) return false

        fun normalize(value: String) = value.trimEnd('/').lowercase()
        val candidate = normalize(url.toString())

        return configured.any { normalize(it) == candidate }
    }

    private val cache = WebResourceCache.getInstance(context)

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {

        if (storage.useDiskCacheWebview) {
            request?.let {
                val url = it.url.toString()
                val sanitizedUrl = LogUrlSanitizer.sanitize(url)
                Log.d(TAG, "shouldInterceptRequest: $sanitizedUrl")
                if (cache.shouldCache(url)) {
                    Log.d(TAG, "shouldInterceptRequest: cacheable $sanitizedUrl")
                    cache.get(url)?.let { response ->
                        return response
                    }
                } else {
                    Log.d(TAG, "shouldInterceptRequest: not cacheable $sanitizedUrl")
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
        

        if (uri.getQueryParameter("redirectUri") != null) {
            
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
        
        if (query == null || webView == null) {
            
            return false
        }

        val querySanitizer = UrlQuerySanitizer()
        querySanitizer.allowUnregisteredParamaters = true
        querySanitizer.parseQuery(query)
        val code = querySanitizer.getValue("code")

        if (code == null) {
            
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
        webView.loadUrl(url.first, AppIdHeaderHelper.getHeaders())
        return false
    }

    private fun handleSocialLoginCallback(webView: WebView?, url: Uri): Boolean {
        
        if (webView == null) {
            
            return false
        }

        val query = url.query
        if (query == null) {
            
            return false
        }

        val querySanitizer = UrlQuerySanitizer()
        querySanitizer.allowUnregisteredParamaters = true
        querySanitizer.parseQuery(query)

        val state = querySanitizer.getValue("state")
        @Suppress("UNUSED_VARIABLE")
        val code = querySanitizer.getValue("code")
        @Suppress("UNUSED_VARIABLE")
        val idToken = querySanitizer.getValue("id_token")

        if (state == null) {
            
            return false
        }

        // Parse state to get provider information
        try {
            val stateJson = JsonParser.parseString(state).asJsonObject
            val provider = stateJson.get("provider")?.asString
            val action = stateJson.get("action")?.asString

            if (provider == null || (action != "login" && action != "signup")) {
                Log.d(TAG, "handleSocialLoginCallback failed: invalid state - provider: $provider, action: $action")
                return false
            }

            

            // Call FronteggAuthService to handle the social login callback
            val redirectUrl = (context.fronteggAuth as FronteggAuthService).handleSocialLoginCallback(url.toString())
            if (redirectUrl != null) {
                currentWebView?.loadUrl(redirectUrl, AppIdHeaderHelper.getHeaders())
                return true
            }

        } catch (e: Exception) {
            Log.e(TAG, "handleSocialLoginCallback failed to parse state: $state", e)
        }

        return false
    }

    /**
     * Handle social login success redirect by extracting redirectUri parameter and redirecting to it (like iOS)
     */
    private fun handleSocialLoginSuccessRedirect(view: WebView?, url: String) {
        try {
            
            
            val uri = Uri.parse(url)
            val redirectUri = uri.getQueryParameter("redirectUri")
            val code = uri.getQueryParameter("code")
            
            if (!redirectUri.isNullOrEmpty()) {
                
                
                // Decode the redirect URI
                val decodedRedirectUri = Uri.decode(redirectUri)
                
                
                // If code present – exchange immediately (parity with iOS flow)
                if (!code.isNullOrEmpty()) {
                    
                    val auth = context.fronteggAuth as FronteggAuthService
                    Handler(Looper.getMainLooper()).post {
                        auth.handleHostedLoginCallback(
                            code = code,
                            webView = view,
                            activity = null,
                            callback = null,
                            redirectUrlOverride = decodedRedirectUri
                        )
                    }
                    return
                }

                // Otherwise, follow redirect
                view?.loadUrl(decodedRedirectUri, AppIdHeaderHelper.getHeaders())
            } else {
                Log.w(TAG, "No redirectUri parameter found in social login success URL")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle social login success redirect", e)
        }
    }

}