package com.frontegg.demo

import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.Button
import android.widget.ProgressBar
import com.frontegg.android.FronteggApp
import com.frontegg.android.ui.DefaultLoader

class App : Application() {

    companion object {
        public lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        DefaultLoader.setLoaderProvider {
           val progressBar = ProgressBar(it)
            val colorStateList = ColorStateList.valueOf(Color.RED)
            progressBar.indeterminateTintList = colorStateList

            progressBar
        }

        Button(applicationContext).setOnClickListener {  }

        FronteggApp.init(
            BuildConfig.FRONTEGG_DOMAIN,
            BuildConfig.FRONTEGG_CLIENT_ID,
            this,
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            mainActivityClass = NavigationActivity::class.java

        )
    }
}