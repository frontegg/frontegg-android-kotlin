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
        )
    }
}