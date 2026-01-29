package com.frontegg.android.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDetailsTest {

    @Test
    fun `forProvider returns correct details for Facebook`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.FACEBOOK)
        assertTrue(details.authorizeEndpoint.contains("facebook.com"))
        assertEquals("code", details.responseType)
        assertNull(details.responseMode)
        assertFalse(details.requiresPKCE)
        assertEquals("reauthenticate", details.promptValueForConsent)
    }

    @Test
    fun `forProvider returns correct details for Google`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.GOOGLE)
        assertTrue(details.authorizeEndpoint.contains("google.com"))
        assertEquals("code", details.responseType)
        assertTrue(details.requiresPKCE)
        assertEquals("select_account", details.promptValueForConsent)
    }

    @Test
    fun `forProvider returns correct details for GitHub`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.GITHUB)
        assertTrue(details.authorizeEndpoint.contains("github.com"))
        assertFalse(details.requiresPKCE)
    }

    @Test
    fun `forProvider returns correct details for Microsoft`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.MICROSOFT)
        assertTrue(details.authorizeEndpoint.contains("microsoftonline.com"))
        assertEquals("query", details.responseMode)
        assertTrue(details.requiresPKCE)
    }

    @Test
    fun `forProvider returns correct details for Slack`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.SLACK)
        assertTrue(details.authorizeEndpoint.contains("slack.com"))
        assertNull(details.promptValueForConsent)
    }

    @Test
    fun `forProvider returns correct details for Apple`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.APPLE)
        assertTrue(details.authorizeEndpoint.contains("apple.com"))
        assertEquals("form_post", details.responseMode)
    }

    @Test
    fun `forProvider returns correct details for LinkedIn`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.LINKEDIN)
        assertTrue(details.authorizeEndpoint.contains("linkedin.com"))
        assertTrue(details.defaultScopes.contains("r_liteprofile"))
    }

}
