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
        val successLoginRoutes = listOf(
            "/oauth/account/social/success",
        )
        val loginRoutes = listOf(
            "/oauth/account/",
        )

        fun oauthCallbackUrl(baseUrl: String): String {

            val host = baseUrl.substring("https://".length)
            val app = FronteggApp.getInstance();
            val packageName = app.packageName
            val useAssetsLinks = app.useAssetsLinks
            return if (useAssetsLinks) {
                "https://${host}/oauth/account/redirect/android/${packageName}"
            } else {
                "${packageName}://${host}/android/oauth/callback"
            }
        }

        fun socialLoginRedirectUrl(baseUrl: String): String {
            return "$baseUrl/oauth/account/social/success"
        }

    }
}