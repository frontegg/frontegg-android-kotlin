package com.frontegg.android.embedded

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.UrlQuerySanitizer
import android.util.Log
import android.webkit.*
import com.frontegg.android.FronteggApp
import com.frontegg.android.FronteggAuth
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.Constants
import com.frontegg.android.utils.Constants.Companion.loginRoutes
import com.frontegg.android.utils.Constants.Companion.oauthUrls
import com.frontegg.android.utils.Constants.Companion.socialLoginRedirectUrl
import com.frontegg.android.utils.Constants.Companion.successLoginRoutes


class FronteggWebClient(val context: Context) : WebViewClient() {
    companion object {
        private val TAG = FronteggWebClient::class.java.simpleName
    }

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
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        if (view!!.url == request?.url.toString()) {
            Log.d(TAG, "onReceivedHttpError: Direct load url, ${request?.url?.path}")
        } else {
            Log.d(TAG, "onReceivedHttpError: HTTP api call, ${request?.url?.path}")
        }
        super.onReceivedHttpError(view, request, errorResponse)
    }

    private fun setUriParameter(uri: Uri, key: String, newValue: String): Uri? {
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
        SocialLoginRedirectToBrowser,
        SocialOauthPreLogin,
        loginRoutes,
        internalRoutes,
        Unknown
    }

    private fun getOverrideUrlType(url: Uri): OverrideUrlType {
        val urlPath = url.path
        val hostedLoginCallback = Constants.oauthCallbackUrl(FronteggApp.getInstance().baseUrl);

        if (url.toString().startsWith(hostedLoginCallback)) {
            return OverrideUrlType.HostedLoginCallback
        }
        if (urlPath != null && url.toString().startsWith(FronteggApp.getInstance().baseUrl)) {

            if (urlPath.startsWith("/frontegg/identity/resources/auth/v2/user/sso/default")
                && urlPath.endsWith("/prelogin")
            ) {
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

        if (oauthUrls.find { u -> url.toString().startsWith(u) } != null) {
            return OverrideUrlType.SocialLoginRedirectToBrowser
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

                OverrideUrlType.SocialLoginRedirectToBrowser -> {
                    FronteggAuth.instance.isLoading.value = true
                    val browserIntent = Intent(Intent.ACTION_VIEW, url)
                    context.startActivity(browserIntent)
                    return true
                }

                else -> {
                    return super.shouldOverrideUrlLoading(view, request)
                }
            }
        } else {
            return super.shouldOverrideUrlLoading(view, request)
        }
    }


    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        return super.shouldInterceptRequest(view, request)
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

    private fun setSocialLoginRedirectUri(webView: WebView, uri: Uri): Boolean {
        Log.d(TAG, "setSocialLoginRedirectUri setting redirect uri for social login")

        if (uri.getQueryParameter("redirectUri") != null) {
            Log.d(TAG, "redirectUri exist, forward navigation to webView")
            return false
        }
        val baseUrl = FronteggApp.getInstance().baseUrl
        val oauthRedirectUri = socialLoginRedirectUrl(baseUrl)
        val newUri = setUriParameter(uri, "redirectUri", oauthRedirectUri)
        webView.loadUrl(newUri.toString())
        return true
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

        if(FronteggAuth.instance.handleHostedLoginCallback(code)){
            return true;
        }

        val authorizeUrl = AuthorizeUrlGenerator()
        val url = authorizeUrl.generate()
        webView.loadUrl(url.first)
        return false
    }

}