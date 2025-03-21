package com.frontegg.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.annotation.VisibleForTesting

/**
 * Functional interface for providing a custom loader view.
 */
@FunctionalInterface
fun interface LoaderProvider {
    /**
     * Provides a custom loader view.
     *
     * @param context The context used to create the view.
     * @return A View instance representing the loader.
     */
    fun provide(context: Context): View
}

/**
 * Object responsible for creating and providing a loader view.
 * Allows setting a custom loader provider or falls back to a default ProgressBar.
 */
object DefaultLoader {
    private var loaderProvider: LoaderProvider? = null

    /**
     * Sets a custom loader provider.
     *
     * @param loaderProvider The custom loader provider implementation.
     */
    fun setLoaderProvider(loaderProvider: LoaderProvider) {
        this.loaderProvider = loaderProvider
    }

    /**
     * Creates a loader view. If a custom loader provider is set, it will be used;
     * otherwise, a default ProgressBar will be created.
     *
     * @param context The context used to create the view.
     * @return A View instance representing the loader.
     */
    fun create(context: Context): View {
        val view: View =
            if (loaderProvider != null) {
                loaderProvider!!.provide(context)
            } else {
                createDefault(context)
            }

        setUpLoader(view)
        return view
    }

    @VisibleForTesting
    internal fun setUpLoader(view: View) {
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (view.layoutParams is LinearLayout.LayoutParams) {
            (view.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER
        }
    }

    @VisibleForTesting
    internal fun createDefault(context: Context): View {
        val progressBar = ProgressBar(context)
        val colorStateList = ColorStateList.valueOf(Color.GRAY)
        progressBar.indeterminateTintList = colorStateList
        return progressBar
    }
}
