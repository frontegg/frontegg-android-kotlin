package com.frontegg.android.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-26330: the unlock-account deep link was missing from the account-action routes, so even
 * once the link reaches the app the URL is withheld whenever the SDK is still initializing or
 * the user is already authenticated.
 */
class AccountActionRoutesTest {
    private val baseUrl = "https://base.url.com"

    @Test
    fun `unlock account url is an account action`() {
        assertTrue(Constants.isAccountActionUrl("$baseUrl/oauth/account/unlock?token=abc&userId=123"))
    }

    @Test
    fun `existing account actions are unchanged`() {
        listOf(
            "/oauth/account/reset-password",
            "/oauth/account/verify-email",
            "/oauth/account/verify-phone",
            "/oauth/account/accept-invitation",
            "/oauth/account/activate",
            "/oauth/account/invitation/accept",
        ).forEach { path ->
            assertTrue(path, Constants.isAccountActionUrl("$baseUrl$path?token=abc"))
        }
    }

    @Test
    fun `social login success is not an account action`() {
        // Social redirects are gated separately by the caller; folding them in here would
        // change which branch handles them.
        assertFalse(Constants.isAccountActionUrl("$baseUrl/oauth/account/social/success?code=abc"))
    }

    @Test
    fun `ordinary login page is not an account action`() {
        assertFalse(Constants.isAccountActionUrl("$baseUrl/oauth/account/login"))
    }

    @Test
    fun `null url is not an account action`() {
        assertFalse(Constants.isAccountActionUrl(null))
    }
}
