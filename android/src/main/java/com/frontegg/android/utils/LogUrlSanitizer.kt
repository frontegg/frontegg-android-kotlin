package com.frontegg.android.utils

import java.util.Locale

/**
 * Strips OAuth-sensitive query parameters from a URL before it is written to
 * logcat. The SDK historically logged full callback URIs at `Log.d` level, which
 * exposed the OAuth `code`, `state`, PKCE `code_verifier` / `code_challenge`,
 * and bearer tokens to any process with logcat access (e.g. ADB on a debug
 * device, or a host app that piped logcat off-device).
 *
 * The sanitizer is intentionally string-based and has no Android framework
 * dependency so it stays cheap to run, safe to call before `Log` is initialised
 * during early SDK startup, and trivially unit-testable on the JVM.
 *
 * Keys kept in sync with [SentryHelper] breadcrumb sanitisation so logcat and
 * Sentry redact the same set.
 */
internal object LogUrlSanitizer {

    private const val REDACTED = "[redacted]"

    /** Exact match (case-insensitive) — every value gets dropped. */
    private val SENSITIVE_EXACT = setOf(
        "code",
        "state",
        "nonce",
        "jwt",
        "bearer",
        "verifier",
        "code_verifier",
        "code_challenge",
        "access_token",
        "refresh_token",
        "id_token",
        "device_token",
        "client_secret",
        "mfa_token",
        "authorization",
        "password",
        "passwd",
        "secret",
    )

    /** Substring match (case-insensitive) — catches things like `*_token`, `x-api-key`. */
    private val SENSITIVE_CONTAINS = listOf(
        "token",
        "secret",
        "password",
        "authorization",
        "credential",
        "signature",
        "apikey",
        "api_key",
        "access_key",
    )

    /**
     * Returns the URL with sensitive query-parameter values replaced by
     * `[redacted]`. Anything that is not a parseable URL (or is null / blank)
     * is returned unchanged so call sites can use this unconditionally without
     * losing diagnostic value.
     */
    fun sanitize(url: String?): String {
        if (url.isNullOrEmpty()) return url ?: "null"

        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url

        val fragmentStart = url.indexOf('#', startIndex = queryStart)
        val queryEnd = if (fragmentStart >= 0) fragmentStart else url.length

        val prefix = url.substring(0, queryStart + 1)
        val query = url.substring(queryStart + 1, queryEnd)
        val suffix = if (fragmentStart >= 0) url.substring(fragmentStart) else ""

        if (query.isEmpty()) return url

        val sanitisedQuery = query.split('&').joinToString("&") { pair ->
            val eq = pair.indexOf('=')
            if (eq < 0) return@joinToString pair
            val key = pair.substring(0, eq)
            if (isSensitive(key)) "$key=$REDACTED" else pair
        }

        return prefix + sanitisedQuery + suffix
    }

    private fun isSensitive(rawKey: String): Boolean {
        val key = rawKey.lowercase(Locale.US)
        if (key in SENSITIVE_EXACT) return true
        return SENSITIVE_CONTAINS.any { key.contains(it) }
    }
}
