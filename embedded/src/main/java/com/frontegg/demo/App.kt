package com.frontegg.demo

import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ProgressBar
import com.frontegg.android.ui.DefaultLoader

class App : Application() {

    companion object {
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize FronteggApp. Necessary for working with Frontegg SDK.
        // Optional parameters: useAssetsLinks, useChromeCustomTabs, mainActivityClass
        DefaultLoader.setLoaderProvider {
            val progressBar = ProgressBar(it)
            val colorStateList = ColorStateList.valueOf(Color.RED)
            progressBar.indeterminateTintList = colorStateList

            progressBar
        }
    }
}