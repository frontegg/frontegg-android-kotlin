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

        /**
         * The path component of the configured base URL, without a trailing slash.
         *
         * A vendor can expose Frontegg under a prefix on a shared domain, with an
         * edge worker translating `api.example.com/fe-auth/oauth/...` onto the real
         * Frontegg host. It serves the association files through the same prefix, so
         * every callback it publishes carries the prefix and the SDK has to send
         * that exact form.
         */
        fun normalizedBasePath(baseUrl: String): String {
            val path = try {
                URI(baseUrl).path.orEmpty()
            } catch (_: Exception) {
                ""
            }
            return path.trimEnd('/').takeIf { it != "/" }.orEmpty()
        }

        /**
         * Whether a callback path is one the SDK generates, allowing for the vendor's
         * public path prefix.
         *
         * The root form stays acceptable regardless: apps already in the field were
         * issued it and their allow-list entries still carry it.
         */
        fun isGeneratedCallbackPath(path: String, basePath: String = ""): Boolean {
            val suffixes = listOf(APP_LINK_CALLBACK_PATH, CUSTOM_SCHEME_CALLBACK_PATH)
            val prefixes = if (basePath.isEmpty()) listOf("") else listOf(basePath, "")

            return prefixes.any { prefix -> suffixes.any { path.startsWith("$prefix$it") } }
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
            val basePath = normalizedBasePath(baseUrl)
            return if (useAssetsLinks) {
                "$scheme://$hostPart$basePath$APP_LINK_CALLBACK_PATH/$packageName"
            } else {
                "$packageName://$hostPart$basePath$CUSTOM_SCHEME_CALLBACK_PATH"
            }
        }

        const val APP_LINK_CALLBACK_PATH = "/oauth/account/redirect/android"
        const val CUSTOM_SCHEME_CALLBACK_PATH = "/android/oauth/callback"

        fun socialLoginRedirectUrl(baseUrl: String): String {
            return "$baseUrl/oauth/account/social/success"
        }

    }
}