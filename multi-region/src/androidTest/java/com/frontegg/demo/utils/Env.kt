package com.frontegg.demo.utils

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

object Env {
    val loginEmail: String
        get() = getStringByName("LOGIN_EMAIL")

    val loginPassword: String
        get() = getStringByName("LOGIN_PASSWORD")

    val fronteggDomainRegion1: String
        get() = getStringByName("FRONTEGG_DOMAIN_1")

    val fronteggDomainRegion2: String
        get() = getStringByName("FRONTEGG_DOMAIN_2")

    private fun getStringByName(name: String) =
        InstrumentationRegistry.getArguments().getString(name) ?: ""
}