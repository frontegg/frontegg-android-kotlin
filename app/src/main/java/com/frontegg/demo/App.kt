package com.frontegg.demo

import android.app.Application
import com.frontegg.android.FronteggApp

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        FronteggApp.init(
            BuildConfig.FRONTEGG_DOMAIN,
            BuildConfig.FRONTEGG_CLIENT_ID,
            this,

        )
    }
}