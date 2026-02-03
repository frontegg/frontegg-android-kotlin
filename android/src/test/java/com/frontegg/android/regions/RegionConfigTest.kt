package com.frontegg.android.regions

import org.junit.Test

class RegionConfigTest {

    @Test
    fun `constructor stores key correctly`() {
        val config = RegionConfig("us-east", "https://us.frontegg.com", "client-123")
        
        assert(config.key == "us-east")
    }

    @Test
    fun `constructor stores clientId correctly`() {
        val config = RegionConfig("us", "https://us.frontegg.com", "client-123")
        
        assert(config.clientId == "client-123")
    }

    @Test
    fun `constructor stores applicationId when provided`() {
        val config = RegionConfig("us", "https://us.frontegg.com", "client-123", "app-456")
        
        assert(config.applicationId == "app-456")
    }

    @Test
    fun `applicationId is null by default`() {
        val config = RegionConfig("us", "https://us.frontegg.com", "client-123")
        
        assert(config.applicationId == null)
    }

    @Test
    fun `baseUrl with https prefix is stored as-is`() {
        val config = RegionConfig("us", "https://us.frontegg.com", "client-123")
        
        assert(config.baseUrl == "https://us.frontegg.com")
    }

    @Test
    fun `baseUrl with http prefix is stored as-is`() {
        val config = RegionConfig("local", "http://localhost:8080", "client-123")
        
        assert(config.baseUrl == "http://localhost:8080")
    }

    @Test
    fun `baseUrl without protocol gets https prefix`() {
        val config = RegionConfig("us", "us.frontegg.com", "client-123")
        
        assert(config.baseUrl == "https://us.frontegg.com")
    }

    @Test
    fun `baseUrl with subdomain without protocol gets https prefix`() {
        val config = RegionConfig("eu", "api.eu.frontegg.com", "client-123")
        
        assert(config.baseUrl == "https://api.eu.frontegg.com")
    }

    @Test
    fun `baseUrl handles trailing slash with protocol`() {
        val config = RegionConfig("us", "https://us.frontegg.com/", "client-123")
        
        assert(config.baseUrl == "https://us.frontegg.com/")
    }

    @Test
    fun `baseUrl handles empty string`() {
        val config = RegionConfig("empty", "", "client-123")
        
        assert(config.baseUrl == "https://")
    }

    @Test
    fun `baseUrl handles HTTPS in uppercase`() {
        val config = RegionConfig("us", "HTTPS://us.frontegg.com", "client-123")
        
        // Should still be stored as-is since it starts with 'http'
        // Note: the check is case-sensitive for startsWith("http")
        assert(config.baseUrl == "https://HTTPS://us.frontegg.com")
    }

    @Test
    fun `multiple regions can be created with different configs`() {
        val usRegion = RegionConfig("us", "https://us.frontegg.com", "client-us", "app-us")
        val euRegion = RegionConfig("eu", "https://eu.frontegg.com", "client-eu", "app-eu")
        val apacRegion = RegionConfig("apac", "https://apac.frontegg.com", "client-apac")
        
        assert(usRegion.key == "us")
        assert(euRegion.key == "eu")
        assert(apacRegion.key == "apac")
        
        assert(usRegion.applicationId == "app-us")
        assert(euRegion.applicationId == "app-eu")
        assert(apacRegion.applicationId == null)
    }

    @Test
    fun `baseUrl with path is stored correctly`() {
        val config = RegionConfig("custom", "https://company.com/frontegg", "client-123")
        
        assert(config.baseUrl == "https://company.com/frontegg")
    }

    @Test
    fun `baseUrl with port is stored correctly`() {
        val config = RegionConfig("dev", "https://localhost:3000", "client-123")
        
        assert(config.baseUrl == "https://localhost:3000")
    }

    @Test
    fun `baseUrl without protocol with port gets https prefix`() {
        val config = RegionConfig("dev", "localhost:3000", "client-123")
        
        assert(config.baseUrl == "https://localhost:3000")
    }
}
