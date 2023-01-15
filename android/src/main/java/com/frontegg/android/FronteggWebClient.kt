package com.frontegg.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.frontegg.android.Constants.Companion.oauthUrls
import io.reactivex.rxjava3.disposables.Disposable


class FronteggWebClient(val context: Context) : WebViewClient() {
    companion object {
        private val TAG = FronteggWebClient::class.java.simpleName
    }

    private var disposable: Disposable

    init {
        disposable = FronteggAuth.instance.accessToken.subscribe {
            Log.d(TAG, "ACCESS_TOKEN changes: $it")
        }
        FronteggAuth.instance.initializing.value = false


    }


    fun destroy() {
        disposable.dispose()
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
            else ->
                FronteggAuth.instance.isLoading.value = false
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
        SsoCallback,
        Forward
    }

    private fun getOverrideUrlType(url: Uri): OverrideUrlType {

        if (url.toString().startsWith(FronteggApp.getInstance().baseUrl)) {
            when (url.path) {
                "/mobile/oauth/callback" -> return OverrideUrlType.HostedLoginCallback
                "/mobile/social-login/callback" -> return OverrideUrlType.SocialLoginCallback
                "/mobile/sso/callback" -> return OverrideUrlType.SsoCallback
            }
        } else if (oauthUrls.find { u -> url.toString().startsWith(u) } != null) {
            return OverrideUrlType.SocialLoginRedirectToBrowser
        }


        return OverrideUrlType.Forward
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {

        val urlType = getOverrideUrlType(request!!.url)


        Log.d(
            "FronteggWebView.shouldOverrideUrlLoading",
            "type: $urlType, url: ${request.url}"
        )

        when (urlType) {
            OverrideUrlType.HostedLoginCallback -> {
                val query = request.url?.query
                Log.d(TAG, "HostedLoginCallback: ${query}")
                return true
            }
            OverrideUrlType.SocialLoginCallback -> {
                val query = request.url?.query
                Log.d(TAG, "SocialLoginCallback: ${query}")
                return true
            }
            OverrideUrlType.SsoCallback -> {
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
            OverrideUrlType.Forward -> {
                return super.shouldOverrideUrlLoading(view, request)
            }
        }

    }


    override fun onLoadResource(view: WebView?, url: String?) {
        val urlType = getOverrideUrlType(Uri.parse(url))
        val fronteggAuth = FronteggApp.getInstance().auth
        Log.d(
            "FronteggWebView.onLoadResource",
            "type: $urlType, url: $url"
        )

        when (urlType) {
            OverrideUrlType.SocialLoginCallback,
            OverrideUrlType.HostedLoginCallback -> {

                Log.d(TAG, "ACCESS_TOKEN will change")
            }

            else -> {
                super.onLoadResource(view, url)
            }
        }

    }

}