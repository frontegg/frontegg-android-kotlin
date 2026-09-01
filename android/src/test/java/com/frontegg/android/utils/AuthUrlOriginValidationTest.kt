package com.frontegg.android.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUrlOriginValidationTest {

    private val allowed = setOf("base.url.com", "eu.frontegg.com")


    @Test
    fun `attacker host carrying a legitimate route is rejected`() {
        assertFalse(
            Constants.isAllowedAuthHost(
                "https://attacker.example/oauth/account/reset-password",
                allowed
            )
        )
    }

    @Test
    fun `configured host is allowed`() {
        assertTrue(
            Constants.isAllowedAuthHost(
                "https://base.url.com/oauth/account/reset-password?token=abc",
                allowed
            )
        )
    }

    @Test
    fun `a configured region host is allowed`() {
        assertTrue(Constants.isAllowedAuthHost("https://eu.frontegg.com/oauth/account/unlock", allowed))
    }

    @Test
    fun `host comparison is case insensitive`() {
        assertTrue(Constants.isAllowedAuthHost("https://BASE.URL.COM/oauth/account/activate", allowed))
    }

    @Test
    fun `a host that merely ends with an allowed host is rejected`() {
        assertFalse(Constants.isAllowedAuthHost("https://base.url.com.attacker.io/oauth", allowed))
    }

    @Test
    fun `a host that merely contains an allowed host is rejected`() {
        assertFalse(Constants.isAllowedAuthHost("https://evil.com/?x=base.url.com", allowed))
    }

    @Test
    fun `custom scheme callback on a configured host is allowed`() {
        assertTrue(
            Constants.isAllowedAuthHost(
                "com.example.app://base.url.com/android/oauth/callback?error=access_denied",
                allowed
            )
        )
    }

    @Test
    fun `javascript uri is rejected`() {
        assertFalse(Constants.isAllowedAuthHost("javascript:alert(1)", allowed))
    }

    @Test
    fun `file uri is rejected`() {
        assertFalse(Constants.isAllowedAuthHost("file:///data/data/com.example/databases", allowed))
    }

    @Test
    fun `data uri is rejected`() {
        assertFalse(Constants.isAllowedAuthHost("data:text/html,<script>alert(1)</script>", allowed))
    }

    @Test
    fun `null and blank urls are rejected`() {
        assertFalse(Constants.isAllowedAuthHost(null, allowed))
        assertFalse(Constants.isAllowedAuthHost("", allowed))
    }

    @Test
    fun `no url is allowed when no hosts are configured`() {
        assertFalse(Constants.isAllowedAuthHost("https://base.url.com/oauth/account/unlock", emptySet()))
    }


    @Test
    fun `route in the query string is not an account action`() {
        assertFalse(
            Constants.isAccountActionUrl("https://attacker.example/?next=/oauth/account/reset-password")
        )
    }

    @Test
    fun `route in the fragment is not an account action`() {
        assertFalse(
            Constants.isAccountActionUrl("https://attacker.example/#/oauth/account/reset-password")
        )
    }

    @Test
    fun `route must end on a segment boundary`() {
        assertFalse(Constants.isAccountActionUrl("https://base.url.com/oauth/account/activateEvil"))
    }

    @Test
    fun `vendor base path prefix still matches`() {
        assertTrue(Constants.isAccountActionUrl("https://base.url.com/fe-auth/oauth/account/activate"))
    }

    @Test
    fun `social login success is detected on the path`() {
        assertTrue(Constants.isSocialLoginSuccessUrl("https://base.url.com/oauth/account/social/success?code=x"))
    }

    @Test
    fun `social login success in the query string is not detected`() {
        assertFalse(Constants.isSocialLoginSuccessUrl("https://attacker.example/?x=/oauth/account/social/success"))
    }

    @Test
    fun `hostOf extracts and lowercases the host`() {
        assertTrue(Constants.hostOf("https://Base.Url.COM/x") == "base.url.com")
    }
}
