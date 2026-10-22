package com.frontegg.android.utils

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Identifies Frontegg endpoints in outgoing requests, for the interceptors that decorate them.
 * Paths are compared relative to the base URL, so a vendor's public path prefix still matches.
 */
internal object FronteggEndpoints {
    const val TOKEN_PATH = "/${ApiConstants.exchangeToken}"
    const val SILENT_AUTHORIZE_PATH = "/${ApiConstants.silentRefreshToken}"
    const val PASSKEY_POST_LOGIN_PATH = "/${ApiConstants.webauthnPostlogin}"

    private const val PROVIDER_PLACEHOLDER = "{provider}"
    private val socialPostLoginPrefix = "/${ApiConstants.socialLoginPostLogin}".substringBefore(PROVIDER_PLACEHOLDER)
    private val socialPostLoginSuffix = "/${ApiConstants.socialLoginPostLogin}".substringAfter(PROVIDER_PLACEHOLDER)
    private val repeatedSlashes = Regex("/+")

    fun isFronteggHost(url: HttpUrl, baseUrl: String): Boolean {
        val fronteggHost = baseUrl.toHttpUrlOrNull()?.host ?: return false
        return url.host.equals(fronteggHost, ignoreCase = true)
    }

    /** The request path without the base URL's path prefix, e.g. `/oauth/token`. */
    fun relativePath(url: HttpUrl, baseUrl: String): String {
        val path = url.encodedPath.replace(repeatedSlashes, "/").trimEnd('/')
        val basePath = Constants.normalizedBasePath(baseUrl)
        return if (basePath.isNotEmpty() && path.startsWith("$basePath/")) path.removePrefix(basePath) else path
    }

    fun isSocialLoginPostLogin(relativePath: String): Boolean {
        if (relativePath.length <= socialPostLoginPrefix.length + socialPostLoginSuffix.length) return false
        if (!relativePath.startsWith(socialPostLoginPrefix) || !relativePath.endsWith(socialPostLoginSuffix)) {
            return false
        }
        val provider = relativePath.substring(
            socialPostLoginPrefix.length,
            relativePath.length - socialPostLoginSuffix.length
        )
        return !provider.contains('/')
    }
}
