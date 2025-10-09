package com.frontegg.android.models

data class SocialLoginPostLoginRequest(
    val code: String? = null,
    val idToken: String? = null,
    val state: String? = null,
    val redirectUri: String? = null
)
