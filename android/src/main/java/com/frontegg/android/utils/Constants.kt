package com.frontegg.android.utils

import com.frontegg.android.services.StorageProvider
import java.net.URI


class ApiConstants {
    companion object {
        const val me: String = "identity/resources/users/v2/me"
        const val tenants: String = "identity/resources/users/v3/me/tenants"
        const val refreshToken: String = "oauth/token"
        const val exchangeToken: String = "oauth/token"
        const val logout: String = "oauth/logout/token"
        const val switchTenant: String = "identity/resources/users/v1/tenant"
        const val webauthnPrelogin: String = "identity/resources/auth/v1/webauthn/prelogin"
        const val webauthnPostlogin: String = "identity/resources/auth/v1/webauthn/postlogin"
        const val registerWebauthnDevice: String = "identity/resources/users/webauthn/v1/devices"
        const val verifyWebauthnDevice: String =
            "identity/resources/users/webauthn/v1/devices/verify"

        /**
         * used to get oauth accessToken/refreshToken without webview
         */
        const val silentRefreshToken: String = "oauth/authorize/silent"
        const val socialLoginPostLogin: String = "oauth/account/social/{provider}/post-login"
        const val userEntitlements: String = "frontegg/entitlements/api/v2/user-entitlements"
    }
}

object StepUpConstants {
    const val ACR_VALUE = "http://schemas.openid.net/pape/policies/2007/06/multi-factor"
    const val AMR_MFA_VALUE = "mfa"
    val AMR_ADDITIONAL_VALUE = listOf("otp", "sms", "hwk")
    const val STEP_UP_MAX_AGE_PARAM_NAME = "maxAge"
}

class Constants {

    companion object {
        val successLoginRoutes = listOf(
            "/oauth/account/social/success",
        )
        val loginRoutes = listOf(
            "/oauth/account/",
        )

        /**
         * Account actions that must load regardless of auth state.
         *
         * These arrive as deep links and are valid while the SDK is still initializing or
         * while a user is already authenticated, so they bypass the usual auth-state gate.
         * `unlock` was missing here (FR-26330), leaving the link to be dropped whenever the
         * activity opened in either of those states.
         *
         * Social-login redirects are deliberately not in this list — the caller gates those
         * separately.
         */
        val accountActionRoutes = listOf(
            "/oauth/account/reset-password",
            "/oauth/account/verify-email",
            "/oauth/account/verify-phone",
            "/oauth/account/accept-invitation",
            "/oauth/account/activate",
            "/oauth/account/invitation/accept",
            "/oauth/account/unlock",
        )

        fun isAccountActionUrl(url: String?): Boolean {
            if (url == null) return false
            return accountActionRoutes.any { url.contains(it) }
        }

        fun oauthCallbackUrl(baseUrl: String): String {
            // Use java.net.URI so JVM unit tests work; android.net.Uri.parse is often null under stubs.
            val uri = try {
                URI(baseUrl)
            } catch (_: Exception) {
                null
            }
            val scheme = uri?.scheme?.takeIf { it.isNotBlank() } ?: "https"
            val host = uri?.host.orEmpty()
            val port = uri?.port ?: -1
            val hostPart =
                if (port != -1 && port != 80 && port != 443) "$host:$port" else host
            val storage = StorageProvider.getInnerStorage()
            val packageName = storage.packageName
            val useAssetsLinks = storage.useAssetsLinks
            return if (useAssetsLinks) {
                "$scheme://$hostPart/oauth/account/redirect/android/$packageName"
            } else {
                "$packageName://$hostPart/android/oauth/callback"
            }
        }

        fun socialLoginRedirectUrl(baseUrl: String): String {
            return "$baseUrl/oauth/account/social/success"
        }

    }
}