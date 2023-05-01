package com.frontegg.android

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.frontegg.android.utils.AuthorizeUrlGenerator
import java.util.*

open class FronteggWebView : WebView {

    private val activePromise: MutableMap<String, (result: Any?, error: Any?) -> Unit> =
        mutableMapOf()

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

        webViewClient = FronteggWebClient(context)
        this.addJavascriptInterface(WebAppInterface(this), "FronteggHanlder")
    }



    private class WebAppInterface(private val webView: FronteggWebView) {
        @JavascriptInterface
        fun then(id: String, result: Any?) {
            webView.notifyThen(id, result)
        }

        @JavascriptInterface
        fun catch(id: String, error: Any?) {
            webView.notifyCatch(id, error)
        }
    }


    private fun notifyThen(id: String, result: Any?) {
        val callback = this.activePromise[id]
        if (callback != null) {
            callback(result, null)
            this.activePromise.remove(id)
        }
    }
    private fun notifyCatch(id: String, error: Any?) {
        val callback = this.activePromise[id]
        if (callback != null) {
            callback(null, error)
            this.activePromise.remove(id)
        }
    }

     fun loadOauthAuthorize() {
        val authorizeUrl = AuthorizeUrlGenerator()
        val url = authorizeUrl.generate()
        this.loadUrl(url)
    }

    fun addPromiseListener(id:String, completion:(result: Any?, error: Any?) -> Unit ){
        this.activePromise[id] = completion

    }
}



