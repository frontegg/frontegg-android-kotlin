package com.frontegg.android.embedded

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.WebView
import com.frontegg.android.FronteggApp
import com.frontegg.android.utils.AuthorizeUrlGenerator
import okhttp3.internal.userAgent
import java.util.*


open class FronteggWebView : WebView {

    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
    }

    private fun getChromeVersion(): String? {
        return try {
            val packageInfo = this.context.packageManager.getPackageInfo("com.android.chrome", 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun getChromeUserAgent(): String {
        // Get Chrome version
        val chromeVersion = getChromeVersion() ?: "Unknown"
        // Construct User-Agent string
        return "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}; ${android.os.Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
    }


    @SuppressLint("SetJavaScriptEnabled")
    fun initView(context: Context) {
        settings.javaScriptEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.domStorageEnabled = true
        settings.safeBrowsingEnabled = true

        if (!FronteggApp.getInstance().handleLoginWithSocialLogin) {
            // Note: Using a custom User-Agent to facilitate Google authentication within an
            //       in-app WebView is not generally recommended.
            // This approach can lead to a segregated session that does not share
            // login state with the primary Chrome app.
            // It may be preferred in some cases for specific user experience
            // requirements or to maintain separate session states,
            // but be aware of potential authentication and session management issues.

            val userAgent = FronteggApp.getInstance().customUserAgent ?: getChromeUserAgent()
            settings.userAgentString = userAgent
        }

        webViewClient = FronteggWebClient(context)

        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        this.addJavascriptInterface(FronteggNativeBridge(context), "FronteggNativeBridge")
    }



    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearWebView()
    }

    private fun clearWebView() {
        clearHistory()
        loadUrl("about:blank")
        onPause()
        removeAllViews()
        destroy()
    }
}

