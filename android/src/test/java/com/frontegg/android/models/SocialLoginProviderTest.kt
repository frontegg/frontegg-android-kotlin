package com.frontegg.android.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialLoginProviderTest {

    @Test
    fun `fromString returns correct provider for each value`() {
        assertEquals(SocialLoginProvider.FACEBOOK, SocialLoginProvider.fromString("facebook"))
        assertEquals(SocialLoginProvider.GOOGLE, SocialLoginProvider.fromString("google"))
        assertEquals(SocialLoginProvider.MICROSOFT, SocialLoginProvider.fromString("microsoft"))
        assertEquals(SocialLoginProvider.GITHUB, SocialLoginProvider.fromString("github"))
        assertEquals(SocialLoginProvider.SLACK, SocialLoginProvider.fromString("slack"))
        assertEquals(SocialLoginProvider.APPLE, SocialLoginProvider.fromString("apple"))
        assertEquals(SocialLoginProvider.LINKEDIN, SocialLoginProvider.fromString("linkedin"))
    }

    @Test
    fun `fromString returns null for unknown value`() {
        assertNull(SocialLoginProvider.fromString("unknown"))
        assertNull(SocialLoginProvider.fromString(""))
        assertNull(SocialLoginProvider.fromString("GOOGLE"))
    }

    @Test
    fun `value property matches enum name`() {
        assertEquals("facebook", SocialLoginProvider.FACEBOOK.value)
        assertEquals("google", SocialLoginProvider.GOOGLE.value)
        assertEquals("linkedin", SocialLoginProvider.LINKEDIN.value)
    }

    @Test
    fun `all enum values are present`() {
        val values = SocialLoginProvider.values()
        assertEquals(7, values.size)
    }
}
