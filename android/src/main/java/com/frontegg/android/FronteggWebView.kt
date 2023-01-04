package com.frontegg.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.frontegg.android.utils.FronteggJSModule
import java.security.MessageDigest
import java.util.*



open class FronteggWebView : WebView {




    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initView(context: Context) {
        settings.javaScriptEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.domStorageEnabled = true

        this.addJavascriptInterface(FronteggJSModule(), "FronteggNative")
        webViewClient = FronteggWebClient(context)

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

