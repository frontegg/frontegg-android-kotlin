package com.frontegg.android.embedded

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
import com.frontegg.android.FronteggApp
import com.frontegg.android.FronteggAuth
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.Constants
import com.frontegg.android.utils.Constants.Companion.loginRoutes
import com.frontegg.android.utils.Constants.Companion.socialLoginRedirectUrl
import com.frontegg.android.utils.Constants.Companion.successLoginRoutes
import com.frontegg.android.utils.generateErrorPage
import com.google.gson.JsonParser
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


class FronteggWebClient(val context: Context) : WebViewClient() {
    companion object {
        private val TAG = FronteggWebClient::class.java.simpleName
    }

    var webViewStatusCode: Int = 200
    var lastErrorResponse: WebResourceResponse? = null

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "onPageStarted $url")
        FronteggAuth.instance.isLoading.value = true
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "onPageFinished $url")
        when (getOverrideUrlType(Uri.parse(url))) {
            OverrideUrlType.HostedLoginCallback ->
                FronteggAuth.instance.isLoading.value = true

            OverrideUrlType.Unknown,
            OverrideUrlType.loginRoutes ->
                FronteggAuth.instance.isLoading.value = false

            else ->
                FronteggAuth.instance.isLoading.value = true
        }

        if (url?.startsWith("data:text/html,") == true) {
            FronteggAuth.instance.isLoading.value = false
            return
        }


        if (webViewStatusCode >= 400) {
            checkIfFronteggError(view, url, lastErrorResponse)
            webViewStatusCode = 200
            lastErrorResponse = null
        } else {
            val fronteggApp = FronteggApp.getInstance()
            val nativeModuleFunctions = JSONObject()
            nativeModuleFunctions.put(
                "loginWithSocialLogin",
                fronteggApp.handleLoginWithSocialLogin
            )
            nativeModuleFunctions.put("loginWithSSO", fronteggApp.handleLoginWithSSO)
            nativeModuleFunctions.put(
                "shouldPromptSocialLoginConsent",
                fronteggApp.shouldPromptSocialLoginConsent
            )
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
                val errorPage = generateErrorPage(
                    htmlError,
                    error = error.description?.toString(),
                    url = request?.url?.toString()
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

        if (url.startsWith("${FronteggAuth.instance.baseUrl}/oauth/authorize")) {
            val reloadScript =
                "setTimeout(()=>window.location.href=\"${url.replace("\"", "\\\"")}\", 4000)"
            val jsCode = "(function(){\n" +
                    "                var script = document.createElement('script');\n" +
                    "                script.innerHTML=`$reloadScript`;" +
                    "                document.body.appendChild(script)\n" +
                    "            })()"
            view.evaluateJavascript(jsCode, null)
            FronteggAuth.instance.isLoading.value = false
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
        val authorizeUrlPrefix = "${FronteggAuth.instance.baseUrl}/oauth/authorize"
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
        val hostedLoginCallback = Constants.oauthCallbackUrl(FronteggApp.getInstance().baseUrl);

        if (url.toString().startsWith(hostedLoginCallback)) {
            return OverrideUrlType.HostedLoginCallback
        }
        if (urlPath != null && url.toString().startsWith(FronteggApp.getInstance().baseUrl)) {

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
            when (getOverrideUrlType(request.url)) {
                OverrideUrlType.HostedLoginCallback -> {
                    return handleHostedLoginCallback(view, request.url?.query)
                }

                OverrideUrlType.SocialOauthPreLogin -> {
                    if (setSocialLoginRedirectUri(view!!, request.url)) {
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
        } else {
            return super.shouldOverrideUrlLoading(view, request)
        }
    }


    override fun onLoadResource(view: WebView?, url: String?) {
        if (url == null || view == null) {
            super.onLoadResource(view, url)
            return
        }

        val uri = Uri.parse(url)
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
        val baseUrl = FronteggApp.getInstance().baseUrl
        val oauthRedirectUri = socialLoginRedirectUrl(baseUrl)
        val newUri = setUriParameter(uri, "redirectUri", oauthRedirectUri)

        FronteggAuth.instance.isLoading.value = true
        try {
            GlobalScope.launch(Dispatchers.IO) {
                val requestBuilder = Request.Builder()
                requestBuilder.method("GET", null)
                requestBuilder.url(newUri.toString())

                val client = OkHttpClient().newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build();
                val response = client.newCall(requestBuilder.build()).execute()

                val redirect = Uri.parse(response.headers["Location"])
                val socialLoginUrl = setUriParameter(redirect, "external", "true")

                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    val browserIntent = Intent(Intent.ACTION_VIEW, socialLoginUrl)
                    context.startActivity(browserIntent)
                    FronteggAuth.instance.isLoading.value = false
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

        if (FronteggAuth.instance.handleHostedLoginCallback(code, webView)) {
            return true;
        }

        val authorizeUrl = AuthorizeUrlGenerator()
        val url = authorizeUrl.generate()
        webView.loadUrl(url.first)
        return false
    }

}