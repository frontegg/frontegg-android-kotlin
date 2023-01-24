package com.frontegg.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.webkit.WebView
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.FronteggJSModule
import okhttp3.internal.userAgent
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
    }

    fun loadOauthAuthorize() {

        val authorizeUrl = AuthorizeUrlGenerator()
        val url = authorizeUrl.generate()
        this.loadUrl(url)
    }
}



