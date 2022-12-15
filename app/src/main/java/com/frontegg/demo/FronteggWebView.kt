package com.frontegg.demo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat.startActivity
import java.security.MessageDigest
import java.util.*


class FronteggJSModule() {
    @JavascriptInterface
    fun digest(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = data.toByteArray()
        val bytes = md.digest(input)
        return Base64.getEncoder().encodeToString(bytes)
    }

    @JavascriptInterface
    fun getBaseUrl(): String {
        return "https://davidantoon.stg.frontegg.com"
    }

    @JavascriptInterface
    fun getClientId(): String {
        return "f7a3f13d-4cb5-4078-b785-e0189c287d4b"
    }
}

open class FronteggWebView : WebView {

    private val APP_SCHEME = "frontegg:"


    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
    }

    fun initView(context: Context) {


        settings.javaScriptEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.domStorageEnabled = true

        this.addJavascriptInterface(FronteggJSModule(), "FronteggNative")

        webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                Log.d(
                    "FronteggWebView.shouldOverrideUrlLoading",
                    "request.url.path: ${request?.url}"
                )

                if (!request?.url.toString().contains("davidantoon.stg.frontegg.com")) {
                    if (request?.url.toString().startsWith(APP_SCHEME)) {
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


//            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
//                if (url.startsWith(APP_SCHEME)) {
//                    val urlData = URLDecoder.decode(url.substring(APP_SCHEME.length), "UTF-8")
////                    respondToData(urlData)
//                    return true
//                }
//                return false
//            }
        }

////        // i am not sure with these inflater lines
////        LayoutInflater inflater = (LayoutInflater) context
////                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
////        // you should not use a new instance of MyWebView here
////        // MyWebView view = (MyWebView) inflater.inflate(R.layout.custom_webview, this);
//        this.getSettings().setJavaScriptEnabled(true)
//        this.getSettings().setUseWideViewPort(true)
//        this.getSettings().setLoadWithOverviewMode(true)
//        this.getSettings().setDomStorageEnabled(true)
//        this.setWebViewClient(object : WebViewClient() {
//            override fun shouldOverrideUrlLoading(
//                view: WebView,
//                request: WebResourceRequest
//            ): Boolean {
//                return super.shouldOverrideUrlLoading(view, request)
//            } //            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
//            //                view?.loadUrl(url)
//            //                return true
//            //            }
//        })
    }
}

