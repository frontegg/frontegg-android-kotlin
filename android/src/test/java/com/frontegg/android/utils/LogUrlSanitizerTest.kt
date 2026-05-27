package com.frontegg.android.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LogUrlSanitizerTest {

    @Test
    fun `null url returns literal null string`() {
        assertEquals("null", LogUrlSanitizer.sanitize(null))
    }

    @Test
    fun `url without query is returned unchanged`() {
        val url = "https://example.frontegg.com/oauth/account/login"
        assertEquals(url, LogUrlSanitizer.sanitize(url))
    }

    @Test
    fun `oauth callback code is redacted`() {
        val url = "frontegg://callback?code=abc123&state=xyz"
        assertEquals(
            "frontegg://callback?code=[redacted]&state=[redacted]",
            LogUrlSanitizer.sanitize(url),
        )
    }

    @Test
    fun `non sensitive params are preserved alongside sensitive ones`() {
        val url = "https://example.frontegg.com/cb?prompt=consent&code=abc&tenant=acme"
        assertEquals(
            "https://example.frontegg.com/cb?prompt=consent&code=[redacted]&tenant=acme",
            LogUrlSanitizer.sanitize(url),
        )
    }

    @Test
    fun `pkce code_verifier and code_challenge are redacted`() {
        val url = "https://example/authorize?code_verifier=AAA&code_challenge=BBB&nonce=CCC"
        assertEquals(
            "https://example/authorize?code_verifier=[redacted]&code_challenge=[redacted]&nonce=[redacted]",
            LogUrlSanitizer.sanitize(url),
        )
    }

    @Test
    fun `tokens are redacted via substring match`() {
        val url = "https://example/cb?access_token=A&refresh_token=B&id_token=C&device_token=D"
        assertEquals(
            "https://example/cb?access_token=[redacted]&refresh_token=[redacted]&id_token=[redacted]&device_token=[redacted]",
            LogUrlSanitizer.sanitize(url),
        )
    }

    @Test
    fun `case insensitive match`() {
        val url = "https://example/cb?Code=abc&STATE=xyz"
        assertEquals(
            "https://example/cb?Code=[redacted]&STATE=[redacted]",
            LogUrlSanitizer.sanitize(url),
        )
    }

    @Test
    fun `fragment is preserved`() {
        val url = "https://example/cb?code=abc#section"
        assertEquals(
            "https://example/cb?code=[redacted]#section",
            LogUrlSanitizer.sanitize(url),
        )
    }

    @Test
    fun `query keys without values are passed through`() {
        val url = "https://example/cb?flag&code=abc"
        assertEquals(
            "https://example/cb?flag&code=[redacted]",
            LogUrlSanitizer.sanitize(url),
        )
    }

    @Test
    fun `empty query is returned unchanged`() {
        val url = "https://example/cb?"
        assertEquals(url, LogUrlSanitizer.sanitize(url))
    }

    @Test
    fun `blank string is returned unchanged`() {
        assertEquals("", LogUrlSanitizer.sanitize(""))
    }
}
