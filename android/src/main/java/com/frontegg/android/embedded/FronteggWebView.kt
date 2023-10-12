package com.frontegg.android.embedded

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView
import com.frontegg.android.utils.AuthorizeUrlGenerator
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
        settings.safeBrowsingEnabled = true

        webViewClient = FronteggWebClient(context)
    }

    fun loadOauthAuthorize() {
        val authorizeUrl = AuthorizeUrlGenerator()
        val url = authorizeUrl.generate()
        this.loadUrl(url.first)
    }
}

