package com.frontegg.android.utils

class ApiConstants {
    companion object {
        const val me: String = "identity/resources/users/v2/me"
        const val refreshToken: String = "oauth/token"
        const val exchangeToken: String = "oauth/token"
        const val logout: String = "identity/resources/auth/v1/logout"
    }
}

class Constants {

    companion object {
        val oauthUrls = listOf(
            "https://www.facebook.com",
            "https://accounts.google.com",
            "https://github.com/login/oauth/authorize",
            "https://login.microsoftonline.com",
            "https://slack.com/openid/connect/authorize",
            "https://appleid.apple.com",
            "https://www.linkedin.com/oauth/"
        )


        val successLoginRoutes = listOf(
            "/oauth/account/social/success",
        )
        val loginRoutes = listOf(
            "/oauth/account/",
        )

        fun oauthCallbackUrl(baseUrl: String): String {
            return "$baseUrl/mobile/oauth/callback"
        }

        fun socialLoginRedirectUrl(baseUrl: String): String {
            return "$baseUrl/oauth/account/social/success"
        }
    }
}