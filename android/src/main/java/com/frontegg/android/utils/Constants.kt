package com.frontegg.android.utils

import com.frontegg.android.FronteggApp

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

        fun oauthCallbackUrl(baseUrl: String): String {
            return "$baseUrl/android/${FronteggApp.getInstance().packageName}/callback"
        }

    }
}