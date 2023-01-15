package com.frontegg.android

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.frontegg.android.utils.AuthorizeUrlGenerator
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import java.net.URL

class FronteggLoginPage : Activity() {

    companion object {
        private val TAG = FronteggLoginPage::class.java.simpleName
    }

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


        loaderLayout = findViewById(R.id.loaderView)
    }

    private var disposeShowLoader: Disposable? = null
    private var loaderLayout: View? = null

    private val showLoader: Consumer<Boolean> = Consumer {
        loaderLayout?.visibility = if (it) View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        disposeShowLoader?.dispose()
    }


    override fun onResume() {
        super.onResume()
        disposeShowLoader = FronteggAuth.instance.showLoader.subscribe(this.showLoader)
    }


}