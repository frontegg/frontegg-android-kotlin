package com.frontegg.demo

import android.app.Application
import com.frontegg.android.FronteggApp

class App : Application() {
    companion object {
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize FronteggApp. Necessary for working with Frontegg SDK.
        // Optional parameters: applicationId
        FronteggApp.init(
            BuildConfig.FRONTEGG_DOMAIN,
            BuildConfig.FRONTEGG_CLIENT_ID,
            this,
            BuildConfig.FRONTEGG_APPLICATION_ID
        )
    }
}