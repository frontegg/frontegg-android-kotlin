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
                    "autheu.davidantoon.me",
                    "b6adfe4c-d695-4c04-b95f-3ec9fd0c6cca"
                ),
                RegionConfig(
                    "us",
                    "authus.davidantoon.me",
                    "6903cab0-9809-4a2e-97dd-b8c0f966c813"
                )
            ),
            this
        )
    }
}