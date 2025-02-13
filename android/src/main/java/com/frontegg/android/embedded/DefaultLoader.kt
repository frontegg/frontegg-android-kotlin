package com.frontegg.android.embedded

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar

@FunctionalInterface
fun interface LoaderProvider {
    fun provide(context: Context): View
}

object DefaultLoader {
    private var loaderProvider: LoaderProvider? = null

    fun setLoaderProvider(loaderProvider: LoaderProvider) {
        this.loaderProvider = loaderProvider
    }

    fun create(context: Context): View {
        val view: View =
            if (loaderProvider != null) {
                loaderProvider!!.provide(context)
            } else {
                val progressBar = ProgressBar(context)
                val colorStateList = ColorStateList.valueOf(Color.GRAY)
                progressBar.indeterminateTintList = colorStateList
                progressBar
            }

        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (view.layoutParams is LinearLayout.LayoutParams) {
            (view.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER
        }

        return view
    }
}