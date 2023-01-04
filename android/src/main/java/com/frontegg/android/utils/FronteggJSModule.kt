package com.frontegg.android.utils

import android.webkit.JavascriptInterface
import java.security.MessageDigest
import java.util.*

class FronteggJSModule() {
    @JavascriptInterface
    fun digest(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = data.toByteArray()
        val bytes = md.digest(input)
        return Base64.getEncoder().encodeToString(bytes)
    }

    @JavascriptInterface
    fun getBaseUrl(): String {
        return "https://davidantoon.stg.frontegg.com"
    }

    @JavascriptInterface
    fun getClientId(): String {
        return "f7a3f13d-4cb5-4078-b785-e0189c287d4b"
    }
}
