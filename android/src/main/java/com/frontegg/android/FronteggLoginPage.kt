package com.frontegg.android

import android.app.Activity
import android.os.Bundle
import com.frontegg.android.utils.AuthorizeUrlGenerator
import java.net.URL

class FronteggLoginPage : Activity() {

    lateinit var webView: FronteggWebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_frontegg_login_page)
        webView = findViewById(R.id.custom_webview)

        val data: String? = intent.data?.toString()


        val authorizeUrl = AuthorizeUrlGenerator()

        val url = authorizeUrl.generate()

        if (data != null) {
            webView.loadUrl(data)
        } else {
            webView.loadUrl(url)
        }

    }


    override fun onResume() {
        super.onResume()

//        webView.loadUrl("https://portal.frontegg.com/")
    }


}