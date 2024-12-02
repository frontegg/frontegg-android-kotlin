package com.frontegg.android.utils

import com.frontegg.android.services.FronteggInnerStorage


class ApiConstants {
    companion object {
        const val me: String = "identity/resources/users/v2/me"
        const val tenants: String = "identity/resources/users/v3/me/tenants"
        const val refreshToken: String = "oauth/token"
        const val exchangeToken: String = "oauth/token"
        const val logout: String = "frontegg/identity/resources/auth/v1/logout"
        const val switchTenant: String = "identity/resources/users/v1/tenant"
        const val webauthnPrelogin: String = "identity/resources/auth/v1/webauthn/prelogin"
        const val webauthnPostlogin: String = "identity/resources/auth/v1/webauthn/postlogin"
        const val registerWebauthnDevice: String = "identity/resources/users/webauthn/v1/devices"
        const val verifyWebauthnDevice: String = "identity/resources/users/webauthn/v1/devices/verify"

        /**
         * used to get oauth accessToken/refreshToken without webview
         */
        const val silentRefreshToken: String = "oauth/authorize/silent"

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
            val storage = FronteggInnerStorage();
            val packageName = storage.packageName
            val useAssetsLinks = storage.useAssetsLinks
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