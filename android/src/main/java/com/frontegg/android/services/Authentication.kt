package com.frontegg.android.services

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.frontegg.android.FronteggApp
import com.frontegg.android.utils.AuthorizeUrlGenerator


class Authentication {
    private val context: Context = FronteggApp.getInstance().context

    public fun start() {
        val url = AuthorizeUrlGenerator().generate().first
        val builder = CustomTabsIntent.Builder()
        builder.setShowTitle(true)
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(this.context, Uri.parse(url))
    }
}