package com.frontegg.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.UrlQuerySanitizer
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.frontegg.android.utils.Constants.Companion.loginRoutes
import com.frontegg.android.utils.Constants.Companion.oauthUrls
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.Constants.Companion.successLoginRoutes
import io.reactivex.rxjava3.disposables.Disposable


class FronteggWebClient(val context: Context) : WebViewClient() {
    companion object {
        private val TAG = FronteggWebClient::class.java.simpleName
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        FronteggAuth.instance.isLoading.value = true

    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        when (getOverrideUrlType(Uri.parse(url))) {
            OverrideUrlType.SocialLoginCallback,
            OverrideUrlType.HostedLoginCallback ->
                FronteggAuth.instance.isLoading.value = true
            OverrideUrlType.Unknown,
            OverrideUrlType.loginRoutes ->
                FronteggAuth.instance.isLoading.value = false
            else ->
                FronteggAuth.instance.isLoading.value = true
        }
    }

    private fun replaceUriParameter(uri: Uri, key: String, newValue: String): Uri? {
        val params: Set<String> = uri.queryParameterNames
        val newUri: Uri.Builder = uri.buildUpon().clearQuery()
        for (param in params) {
            newUri.appendQueryParameter(
                param,
                if (param == key) newValue else uri.getQueryParameter(param)
            )
        }
        return newUri.build()
    }

    enum class OverrideUrlType {
        HostedLoginCallback,
        SocialLoginCallback,
        SocialLoginRedirectToBrowser,
        SamlCallback,
        loginRoutes,
        internalRoutes,
        Unknown,
    }

    private fun getOverrideUrlType(url: Uri): OverrideUrlType {
        if (url.toString().startsWith(FronteggApp.getInstance().baseUrl)) {
            return when (url.path) {
                "/mobile/oauth/callback" -> OverrideUrlType.HostedLoginCallback
                "/mobile/sso/callback" -> OverrideUrlType.SocialLoginCallback
                "/auth/saml/callback" -> OverrideUrlType.SamlCallback
                else -> {
                    if (successLoginRoutes.find { u -> url.path.toString().startsWith(u)} != null) {
                        OverrideUrlType.internalRoutes
                    }else if (loginRoutes.find { u -> url.path.toString().startsWith(u)} != null) {
                        OverrideUrlType.loginRoutes
                    }else {
                        OverrideUrlType.internalRoutes
                    }
                }
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

        when (getOverrideUrlType(request!!.url)) {
            OverrideUrlType.HostedLoginCallback -> {
                return handleHostedLoginCallback(view, request.url?.query)
            }
            OverrideUrlType.SocialLoginCallback -> {
                return handleSocialLoginCallback(view, request.url?.query)
            }
            OverrideUrlType.SamlCallback -> {
                val query = request.url?.query
                Log.d(TAG, "SsoCallback: ${query}")
                return true
            }
            OverrideUrlType.SocialLoginRedirectToBrowser -> {
                val newUrl = replaceUriParameter(
                    request.url,
                    "redirectUri",
                    Uri.Builder().encodedAuthority(FronteggApp.getInstance().baseUrl)
                        .path("/mobile/sso/callback").toString()
                )
                FronteggAuth.instance.isLoading.value = false
                val browserIntent = Intent(Intent.ACTION_VIEW, newUrl)
                context.startActivity(browserIntent);
                return true
            }
            else -> {
                return super.shouldOverrideUrlLoading(view, request)
            }
        }

    }


    override fun onLoadResource(view: WebView?, url: String?) {
        val urlType = getOverrideUrlType(Uri.parse(url))

        val uri = Uri.parse(url);
        val query = uri.query
        when (urlType) {
            OverrideUrlType.SocialLoginCallback -> {
                if (handleSocialLoginCallback(view, query)) {
                    super.onLoadResource(view, url)
                }
            }
            OverrideUrlType.HostedLoginCallback -> {
                if (!handleHostedLoginCallback(view, query)) {
                    super.onLoadResource(view, url)
                }
            }
            else -> {
                super.onLoadResource(view, url)
            }
        }
    }

    private fun handleSocialLoginCallback(webView: WebView?, query: String?): Boolean {
        Log.d(TAG, "handleSocialLoginCallback received query: $query")
        if (query == null || webView == null) {
            Log.d(
                TAG,
                "handleSocialLoginCallback failed of nullable value, query: $query, webView: $webView"
            )
            return false
        }
        val baseUrl = FronteggApp.getInstance().baseUrl
        val successUrl = "$baseUrl/oauth/account/social/success?$query"
        webView.loadUrl(successUrl)
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

        if (FronteggAuth.instance.handleHostedLoginCallback(webView as FronteggWebView, code)) {
            return true
        }

        val authorizeUrl = AuthorizeUrlGenerator()
        val url = authorizeUrl.generate()
        webView.loadUrl(url)
        return false
    }

}