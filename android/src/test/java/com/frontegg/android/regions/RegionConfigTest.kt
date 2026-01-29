package com.frontegg.android.regions

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionConfigTest {

    @Test
    fun `baseUrl adds https prefix when domain has no scheme`() {
        val config = RegionConfig(
            key = "us",
            baseUrl = "frontegg.example.com",
            clientId = "client-1",
            applicationId = "app-1"
        )
        assertEquals("https://frontegg.example.com", config.baseUrl)
    }

    @Test
    fun `baseUrl keeps https when already present`() {
        val config = RegionConfig(
            key = "eu",
            baseUrl = "https://eu.frontegg.com",
            clientId = "client-2"
        )
        assertEquals("https://eu.frontegg.com", config.baseUrl)
    }

    @Test
    fun `baseUrl keeps http when present`() {
        val config = RegionConfig(
            key = "local",
            baseUrl = "http://localhost:8080",
            clientId = "client-3"
        )
        assertEquals("http://localhost:8080", config.baseUrl)
    }

    @Test
    fun `constructor stores key clientId and applicationId`() {
        val config = RegionConfig(
            key = "key1",
            baseUrl = "https://example.com",
            clientId = "cid",
            applicationId = "aid"
        )
        assertEquals("key1", config.key)
        assertEquals("cid", config.clientId)
        assertEquals("aid", config.applicationId)
    }

    @Test
    fun `applicationId defaults to null`() {
        val config = RegionConfig(
            key = "k",
            baseUrl = "https://example.com",
            clientId = "c"
        )
        assertEquals(null, config.applicationId)
    }
}
