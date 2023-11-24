package com.frontegg.demo

import android.app.Application
import com.frontegg.android.FronteggApp
import com.frontegg.android.regions.RegionConfig

class App : Application() {

    companion object {
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        FronteggApp.initWithRegions(
            listOf(
                RegionConfig(
                    "eu",
                    "auth.davidantoon.me",
                    "b6adfe4c-d695-4c04-b95f-3ec9fd0c6cca"
                ),
                RegionConfig(
                    "us",
                    "davidprod.frontegg.com",
                    "d7d07347-2c57-4450-8418-0ec7ee6e096b"
                )
            ),
            this
        )
    }
}