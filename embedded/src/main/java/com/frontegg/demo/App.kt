package com.frontegg.demo

import android.app.Application
import com.frontegg.android.FronteggApp

class App : Application() {

    companion object {
        public lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        FronteggApp.init(
            BuildConfig.FRONTEGG_DOMAIN,
            BuildConfig.FRONTEGG_CLIENT_ID,
            this,
            useAssetsLinks = true,
        )
//        FronteggApp.getInstance().customUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
//        FronteggApp.getInstance().handleLoginWithSocialLogin = true
    }
}