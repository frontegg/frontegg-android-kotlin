package com.frontegg.demo.utils

import androidx.test.platform.app.InstrumentationRegistry

object Env {
    val loginEmail: String
        get() = getStringByName("LOGIN_EMAIL")

    val loginPassword: String
        get() = getStringByName("LOGIN_PASSWORD")

    val loginWrongEmail: String
        get() = getStringByName("LOGIN_WRONG_EMAIL")

    val loginWrongPassword: String
        get() = getStringByName("LOGIN_WRONG_PASSWORD")

    val signUpTemplate: String
        get() = getStringByName("SIGN_UP_TEMPLATE")

    val signUpName: String
        get() = getStringByName("SIGN_UP_NAME")

    val signUpOrganization: String
        get() = getStringByName("SIGN_UP_ORGANIZATION")

    val tenantName1: String
        get() = getStringByName("TENANT_NAME_1")

    val tenantName2: String
        get() = getStringByName("TENANT_NAME_2")

    val googleEmail: String
        get() = getStringByName("GOOGLE_EMAIL")
    val googlePassword: String
        get() = getStringByName("GOOGLE_PASSWORD")

    val appleEmail: String
        get() = getStringByName("APPLE_EMAIL")

    val applePassword: String
        get() = getStringByName("APPLE_PASSWORD")

    private fun getStringByName(name: String) =
        InstrumentationRegistry.getArguments().getString(name) ?: ""
}