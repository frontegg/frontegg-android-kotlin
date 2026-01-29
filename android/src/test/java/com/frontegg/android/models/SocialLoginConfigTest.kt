package com.frontegg.android.models

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SocialLoginConfigTest {

    private val gson = Gson()

    @Test
    fun `SocialLoginConfig deserializes from JSON with all providers`() {
        val json = """
            {
                "facebook": {"active": true, "clientId": "fb-client"},
                "google": {"active": true, "clientId": "google-client", "additionalScopes": ["scope1"]},
                "microsoft": null,
                "github": {"active": false},
                "slack": {},
                "apple": null,
                "linkedin": {"active": true}
            }
        """.trimIndent()
        val config = gson.fromJson(json, SocialLoginConfig::class.java)
        assertNotNull(config.facebook)
        assertEquals("fb-client", config.facebook?.clientId)
        assertNotNull(config.google)
        assertEquals("google-client", config.google?.clientId)
        assertEquals(listOf("scope1"), config.google?.additionalScopes)
        assertNull(config.microsoft)
        assertNotNull(config.github)
        assertFalse(config.github?.active!!)
    }

    @Test
    fun `SocialLoginOption deserializes with default values`() {
        val json = "{}"
        val option = gson.fromJson(json, SocialLoginOption::class.java)
        assertEquals(false, option.active)
        assertNull(option.clientId)
        assertNull(option.authorizationUrl)
        assertEquals(emptyList<String>(), option.additionalScopes)
    }

    @Test
    fun `SocialLoginOption deserializes full JSON`() {
        val json = """
            {
                "active": true,
                "clientId": "test-client",
                "authorizationUrl": "https://auth.example.com",
                "additionalScopes": ["email", "profile"]
            }
        """.trimIndent()
        val option = gson.fromJson(json, SocialLoginOption::class.java)
        assertEquals(true, option.active)
        assertEquals("test-client", option.clientId)
        assertEquals("https://auth.example.com", option.authorizationUrl)
        assertEquals(listOf("email", "profile"), option.additionalScopes)
    }

    @Test
    fun `SocialLoginConfig with null providers deserializes`() {
        val json = "{}"
        val config = gson.fromJson(json, SocialLoginConfig::class.java)
        assertNull(config.facebook)
        assertNull(config.google)
    }
}
