package com.frontegg.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.frontegg.android.utils.FronteggJSModule
import java.net.URL

class FronteggWebClient(val context: Context) : WebViewClient() {

    private fun replaceUriParameter(uri: Uri, key: String, newValue: String): Uri? {
        val params: Set<String> = uri.getQueryParameterNames()
        val newUri: Uri.Builder = uri.buildUpon().clearQuery()
        for (param in params) {
            newUri.appendQueryParameter(
                param,
                if (param == key) newValue else uri.getQueryParameter(param)
            )
        }
        return newUri.build()
    }
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        Log.d(
            "FronteggWebView.shouldOverrideUrlLoading",
            "request.url.path: ${request?.url}"
        )

        if (!request?.url.toString().contains("auth.davidantoon.me")) {
            if (request?.url.toString().startsWith(Constants.APP_SCHEME)) {

                if (request?.url.toString().startsWith("frontegg://oauth/callback")) {
                    val code = request?.url?.getQueryParameter("code")
                    Log.d("CALLBACK CODE", "${code}")

                }
                return true
            }
            if (request?.url.toString().contains("google")) {
                Log.d(
                    "FronteggWebView.shouldOverrideUrlLoading",
                    "going out: ${request?.url}"
                )

                val browserIntent = Intent(Intent.ACTION_VIEW, request!!.url)
                context.startActivity(browserIntent);
                return true
            }
        }

        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun onLoadResource(view: WebView?, url: String?) {
        Log.d("FronteggWebView.onLoadResource", "request.url.path: ${url}")
        if (url?.contains("google") == true) {
//                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
//                    context.startActivity(browserIntent);
//                    return
        }
        super.onLoadResource(view, url)
    }


}