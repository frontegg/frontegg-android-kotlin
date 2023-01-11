package com.frontegg.android

class Constants {

    companion object {
        const val APP_SCHEME = "frontegg:"

        val oauthUrls = listOf(
            "https://www.facebook.com",
            "https://accounts.google.com",
            "https://github.com/login/oauth/authorize",
            "https://login.microsoftonline.com",
            "https://slack.com/openid/connect/authorize",
            "https://appleid.apple.com"
        )
    }
}