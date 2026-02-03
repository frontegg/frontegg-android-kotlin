package com.frontegg.android.models

import org.junit.Test

class FronteggConstantsTest {

    @Test
    fun `FronteggConstants stores all properties correctly`() {
        val constants = FronteggConstants(
            baseUrl = "https://test.frontegg.com",
            clientId = "test-client-id",
            applicationId = "test-app-id",
            useAssetsLinks = true,
            useChromeCustomTabs = false,
            deepLinkScheme = "myapp",
            useDiskCacheWebview = true,
            mainActivityClass = "com.test.MainActivity",
            disableAutoRefresh = false,
            enableSessionPerTenant = true,
            sentryMaxQueueSize = 100
        )
        
        assert(constants.baseUrl == "https://test.frontegg.com")
        assert(constants.clientId == "test-client-id")
        assert(constants.applicationId == "test-app-id")
        assert(constants.useAssetsLinks)
        assert(!constants.useChromeCustomTabs)
        assert(constants.deepLinkScheme == "myapp")
        assert(constants.useDiskCacheWebview)
        assert(constants.mainActivityClass == "com.test.MainActivity")
        assert(!constants.disableAutoRefresh)
        assert(constants.enableSessionPerTenant)
        assert(constants.sentryMaxQueueSize == 100)
    }

    @Test
    fun `FronteggConstants handles null applicationId`() {
        val constants = FronteggConstants(
            baseUrl = "https://test.frontegg.com",
            clientId = "test-client-id",
            applicationId = null,
            useAssetsLinks = false,
            useChromeCustomTabs = false,
            deepLinkScheme = null,
            useDiskCacheWebview = false,
            mainActivityClass = null,
            disableAutoRefresh = false,
            enableSessionPerTenant = false,
            sentryMaxQueueSize = 50
        )
        
        assert(constants.applicationId == null)
        assert(constants.deepLinkScheme == null)
        assert(constants.mainActivityClass == null)
    }

    @Test
    fun `toMap returns all properties`() {
        val constants = FronteggConstants(
            baseUrl = "https://test.frontegg.com",
            clientId = "client-123",
            applicationId = "app-456",
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            deepLinkScheme = "scheme",
            useDiskCacheWebview = true,
            mainActivityClass = "MainActivity",
            disableAutoRefresh = true,
            enableSessionPerTenant = true,
            sentryMaxQueueSize = 200
        )
        
        val map = constants.toMap()
        
        assert(map["baseUrl"] == "https://test.frontegg.com")
        assert(map["clientId"] == "client-123")
        assert(map["applicationId"] == "app-456")
        assert(map["useAssetsLinks"] == true)
        assert(map["useChromeCustomTabs"] == true)
        assert(map["deepLinkScheme"] == "scheme")
        assert(map["useDiskCacheWebview"] == true)
        assert(map["mainActivityClass"] == "MainActivity")
        assert(map["disableAutoRefresh"] == true)
        assert(map["enableSessionPerTenant"] == true)
        assert(map["sentryMaxQueueSize"] == 200)
    }

    @Test
    fun `toMap includes null values`() {
        val constants = FronteggConstants(
            baseUrl = "https://test.frontegg.com",
            clientId = "client-123",
            applicationId = null,
            useAssetsLinks = false,
            useChromeCustomTabs = false,
            deepLinkScheme = null,
            useDiskCacheWebview = false,
            mainActivityClass = null,
            disableAutoRefresh = false,
            enableSessionPerTenant = false,
            sentryMaxQueueSize = 50
        )
        
        val map = constants.toMap()
        
        assert(map.containsKey("applicationId"))
        assert(map["applicationId"] == null)
        assert(map.containsKey("deepLinkScheme"))
        assert(map["deepLinkScheme"] == null)
        assert(map.containsKey("mainActivityClass"))
        assert(map["mainActivityClass"] == null)
    }

    @Test
    fun `toMap returns correct size`() {
        val constants = FronteggConstants(
            baseUrl = "https://test.frontegg.com",
            clientId = "client-123",
            applicationId = "app-456",
            useAssetsLinks = false,
            useChromeCustomTabs = false,
            deepLinkScheme = null,
            useDiskCacheWebview = false,
            mainActivityClass = null,
            disableAutoRefresh = false,
            enableSessionPerTenant = false,
            sentryMaxQueueSize = 50
        )
        
        val map = constants.toMap()
        
        assert(map.size == 11)
    }

    @Test
    fun `data class equality works correctly`() {
        val constants1 = FronteggConstants(
            baseUrl = "https://test.frontegg.com",
            clientId = "client-123",
            applicationId = "app-456",
            useAssetsLinks = true,
            useChromeCustomTabs = false,
            deepLinkScheme = "myapp",
            useDiskCacheWebview = true,
            mainActivityClass = "MainActivity",
            disableAutoRefresh = false,
            enableSessionPerTenant = true,
            sentryMaxQueueSize = 100
        )
        
        val constants2 = FronteggConstants(
            baseUrl = "https://test.frontegg.com",
            clientId = "client-123",
            applicationId = "app-456",
            useAssetsLinks = true,
            useChromeCustomTabs = false,
            deepLinkScheme = "myapp",
            useDiskCacheWebview = true,
            mainActivityClass = "MainActivity",
            disableAutoRefresh = false,
            enableSessionPerTenant = true,
            sentryMaxQueueSize = 100
        )
        
        assert(constants1 == constants2)
        assert(constants1.hashCode() == constants2.hashCode())
    }

    @Test
    fun `data class inequality when values differ`() {
        val constants1 = FronteggConstants(
            baseUrl = "https://test1.frontegg.com",
            clientId = "client-123",
            applicationId = "app-456",
            useAssetsLinks = true,
            useChromeCustomTabs = false,
            deepLinkScheme = "myapp",
            useDiskCacheWebview = true,
            mainActivityClass = "MainActivity",
            disableAutoRefresh = false,
            enableSessionPerTenant = true,
            sentryMaxQueueSize = 100
        )
        
        val constants2 = FronteggConstants(
            baseUrl = "https://test2.frontegg.com",
            clientId = "client-123",
            applicationId = "app-456",
            useAssetsLinks = true,
            useChromeCustomTabs = false,
            deepLinkScheme = "myapp",
            useDiskCacheWebview = true,
            mainActivityClass = "MainActivity",
            disableAutoRefresh = false,
            enableSessionPerTenant = true,
            sentryMaxQueueSize = 100
        )
        
        assert(constants1 != constants2)
    }

    @Test
    fun `copy function works correctly`() {
        val original = FronteggConstants(
            baseUrl = "https://test.frontegg.com",
            clientId = "client-123",
            applicationId = "app-456",
            useAssetsLinks = true,
            useChromeCustomTabs = false,
            deepLinkScheme = "myapp",
            useDiskCacheWebview = true,
            mainActivityClass = "MainActivity",
            disableAutoRefresh = false,
            enableSessionPerTenant = true,
            sentryMaxQueueSize = 100
        )
        
        val copied = original.copy(baseUrl = "https://new.frontegg.com")
        
        assert(copied.baseUrl == "https://new.frontegg.com")
        assert(copied.clientId == original.clientId)
        assert(copied.applicationId == original.applicationId)
    }
}
