package com.frontegg.android

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.databinding.Observable.OnPropertyChangedCallback
import com.frontegg.android.Constants.Companion.oauthUrls

class FronteggWebClient(val context: Context) : WebViewClient() {
    companion object {
        private val TAG = FronteggWebClient::class.java.simpleName
    }

    init {
        FronteggApp.getInstance().auth.accessToken.addOnPropertyChanged {
            Log.d(TAG, "accessToken changed: ${it}")
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
        OauthCallback,
        SsoCallback,
        SsoRedirectToBrowser,
        Forward
    }

    private fun getOverrideUrlType(url: Uri): OverrideUrlType {

        if (url.toString().startsWith(FronteggApp.getInstance().baseUrl)) {
            when (url.path) {
                "/mobile/oauth/callback" -> return OverrideUrlType.OauthCallback
                "/mobile/sso/callback" -> return OverrideUrlType.SsoCallback
            }
        }
        else if (oauthUrls.find { u -> url.toString().startsWith(u) } != null) {
            return OverrideUrlType.SsoRedirectToBrowser
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
            OverrideUrlType.OauthCallback -> {
                val code = request?.url?.getQueryParameter("code")
                Log.d("CALLBACK CODE", "${code}")
                return true
            }
            OverrideUrlType.SsoCallback -> {
                val code = request?.url?.getQueryParameter("code")
                Log.d("CALLBACK CODE", "${code}")
                return true
            }
            OverrideUrlType.SsoRedirectToBrowser -> {
                val newUrl = replaceUriParameter(
                    request.url,
                    "redirectUri",
                    Uri.Builder().encodedAuthority(FronteggApp.getInstance().baseUrl)
                        .path("/mobile/sso/callback").toString()
                )
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
            OverrideUrlType.SsoCallback,
            OverrideUrlType.OauthCallback -> {
                fronteggAuth.accessToken.set("tttt")
//                unsubscribe()
            }

            else -> {
                super.onLoadResource(view, url)
            }
        }

    }

}