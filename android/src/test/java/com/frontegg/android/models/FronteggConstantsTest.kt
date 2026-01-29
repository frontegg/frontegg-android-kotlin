package com.frontegg.android.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FronteggConstantsTest {

    @Test
    fun `toMap returns all properties`() {
        val constants = FronteggConstants(
            baseUrl = "https://api.example.com",
            clientId = "client-123",
            applicationId = "app-456",
            useAssetsLinks = true,
            useChromeCustomTabs = false,
            deepLinkScheme = "myapp",
            useDiskCacheWebview = true,
            mainActivityClass = "com.example.MainActivity",
            disableAutoRefresh = false,
            enableSessionPerTenant = true,
            enableSentryLogging = true,
            sentryMaxQueueSize = 100
        )
        val map = constants.toMap()
        assertEquals("https://api.example.com", map["baseUrl"])
        assertEquals("client-123", map["clientId"])
        assertEquals("app-456", map["applicationId"])
        assertEquals(true, map["useAssetsLinks"])
        assertEquals(false, map["useChromeCustomTabs"])
        assertEquals("myapp", map["deepLinkScheme"])
        assertEquals(true, map["useDiskCacheWebview"])
        assertEquals("com.example.MainActivity", map["mainActivityClass"])
        assertEquals(false, map["disableAutoRefresh"])
        assertEquals(true, map["enableSessionPerTenant"])
        assertEquals(true, map["enableSentryLogging"])
        assertEquals(100, map["sentryMaxQueueSize"])
        assertEquals(12, map.size)
    }

    @Test
    fun `toMap handles null optional fields`() {
        val constants = FronteggConstants(
            baseUrl = "https://api.example.com",
            clientId = "client",
            applicationId = null,
            useAssetsLinks = false,
            useChromeCustomTabs = false,
            deepLinkScheme = null,
            useDiskCacheWebview = false,
            mainActivityClass = null,
            disableAutoRefresh = false,
            enableSessionPerTenant = false,
            enableSentryLogging = false,
            sentryMaxQueueSize = 50
        )
        val map = constants.toMap()
        assertNull(map["applicationId"])
        assertNull(map["deepLinkScheme"])
        assertNull(map["mainActivityClass"])
    }
}
