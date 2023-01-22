package com.frontegg.demo

import android.app.Application
import com.frontegg.android.FronteggApp

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        FronteggApp.init(this, R.layout.loader)
    }

}