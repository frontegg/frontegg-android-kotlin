package com.frontegg.android

import com.frontegg.android.models.SocialLoginProvider
import org.junit.Test
import org.junit.Assert.*

class SocialLoginProviderTest {

    @Test
    fun testProviderValues() {
        assertEquals("google", SocialLoginProvider.GOOGLE.value)
        assertEquals("github", SocialLoginProvider.GITHUB.value)
        assertEquals("microsoft", SocialLoginProvider.MICROSOFT.value)
        assertEquals("facebook", SocialLoginProvider.FACEBOOK.value)
        assertEquals("slack", SocialLoginProvider.SLACK.value)
        assertEquals("linkedin", SocialLoginProvider.LINKEDIN.value)
        assertEquals("apple", SocialLoginProvider.APPLE.value)
    }

    @Test
    fun testFromValue() {
        assertEquals(SocialLoginProvider.GOOGLE, SocialLoginProvider.fromValue("google"))
        assertEquals(SocialLoginProvider.GITHUB, SocialLoginProvider.fromValue("github"))
        assertEquals(SocialLoginProvider.MICROSOFT, SocialLoginProvider.fromValue("microsoft"))
        assertEquals(SocialLoginProvider.FACEBOOK, SocialLoginProvider.fromValue("facebook"))
        assertEquals(SocialLoginProvider.SLACK, SocialLoginProvider.fromValue("slack"))
        assertEquals(SocialLoginProvider.LINKEDIN, SocialLoginProvider.fromValue("linkedin"))
        assertEquals(SocialLoginProvider.APPLE, SocialLoginProvider.fromValue("apple"))
        assertNull(SocialLoginProvider.fromValue("unknown"))
        assertNull(SocialLoginProvider.fromValue(""))
    }

    @Test
    fun testGetAllValues() {
        val allValues = SocialLoginProvider.getAllValues()
        assertEquals(7, allValues.size)
        assertTrue(allValues.contains("google"))
        assertTrue(allValues.contains("github"))
        assertTrue(allValues.contains("microsoft"))
        assertTrue(allValues.contains("facebook"))
        assertTrue(allValues.contains("slack"))
        assertTrue(allValues.contains("linkedin"))
        assertTrue(allValues.contains("apple"))
    }

    @Test
    fun testEnumValues() {
        val values = SocialLoginProvider.values()
        assertEquals(7, values.size)
        assertTrue(values.contains(SocialLoginProvider.GOOGLE))
        assertTrue(values.contains(SocialLoginProvider.GITHUB))
        assertTrue(values.contains(SocialLoginProvider.MICROSOFT))
        assertTrue(values.contains(SocialLoginProvider.FACEBOOK))
        assertTrue(values.contains(SocialLoginProvider.SLACK))
        assertTrue(values.contains(SocialLoginProvider.LINKEDIN))
        assertTrue(values.contains(SocialLoginProvider.APPLE))
    }
}

