package com.frontegg.android.utils

import com.frontegg.android.FronteggApp


class ApiConstants {
    companion object {
        const val me: String = "identity/resources/users/v2/me"
        const val tenants: String = "identity/resources/users/v3/me/tenants"
        const val refreshToken: String = "oauth/token"
        const val exchangeToken: String = "oauth/token"
        const val logout: String = "frontegg/identity/resources/auth/v1/logout"
        const val switchTenant: String = "identity/resources/users/v1/tenant"


    }
}

class Constants {

    companion object {
        val oauthUrls = listOf(
            "https://www.facebook.com/v10.0/dialog/oauth",
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://github.com/login/oauth/authorize",
            "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
            "https://slack.com/openid/connect/authorize",
            "https://appleid.apple.com/auth/authorize",
            "https://www.linkedin.com/oauth/v2/authorization"
        )

        val successLoginRoutes = listOf(
            "/oauth/account/social/success",
        )
        val loginRoutes = listOf(
            "/oauth/account/",
        )

        fun oauthCallbackUrl(baseUrl: String): String {

            val host = baseUrl.substring("https://".length)
            val packageName = FronteggApp.getInstance().packageName

            return "${packageName}://${host}/android/oauth/callback"
        }

        fun socialLoginRedirectUrl(baseUrl: String): String {
            return "$baseUrl/oauth/account/social/success"
        }

    }
}