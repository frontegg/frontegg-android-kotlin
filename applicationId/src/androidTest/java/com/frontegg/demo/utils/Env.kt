package com.frontegg.demo.utils

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

object Env {
    val loginEmail: String
        get() = getStringByName("LOGIN_EMAIL")

    val loginPassword: String
        get() = getStringByName("LOGIN_PASSWORD")

    val applicationId: String
        get() = getStringByName("FRONTEGG_APPLICATION_ID")



    private fun getStringByName(name: String) =
        InstrumentationRegistry.getArguments().getString(name) ?: ""
}