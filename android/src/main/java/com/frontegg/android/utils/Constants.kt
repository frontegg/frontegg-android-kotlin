package com.frontegg.android.utils

import com.frontegg.android.FronteggApp


class ApiConstants {
    companion object {
        const val me: String = "identity/resources/users/v2/me"
        const val tenants: String = "identity/resources/users/v3/me/tenants"
        const val refreshToken: String = "oauth/token"
        const val exchangeToken: String = "oauth/token"
        const val logout: String = "identity/resources/auth/v1/logout"
        const val switchTenant: String = "identity/resources/users/v1/tenant"
    }
}

class Constants {

    companion object {

        fun oauthCallbackUrl(baseUrl: String): String {

            val host = baseUrl.substring("https://".length)
            val packageName = FronteggApp.getInstance().packageName

            val protocol = packageName.replace(Regex("\\."), "")
            return "${protocol}://${host}/android/oauth/callback"
        }

    }
}