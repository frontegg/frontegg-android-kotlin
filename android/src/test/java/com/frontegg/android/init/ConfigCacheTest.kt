package com.frontegg.android.init

import android.content.Context
import android.content.SharedPreferences
import com.frontegg.android.regions.RegionConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class ConfigCacheTest {

    private val mockContext = mockk<Context>()
    private val mockSharedPreferences = mockk<SharedPreferences>()
    private val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    
    private val storedStrings = mutableMapOf<String, String?>()

    @Before
    fun setUp() {
        storedStrings.clear()
        
        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        
        // Capture put operations
        val keySlot = slot<String>()
        val valueSlot = slot<String>()
        every { mockEditor.putString(capture(keySlot), capture(valueSlot)) } answers {
            storedStrings[keySlot.captured] = valueSlot.captured
            mockEditor
        }
        every { mockEditor.apply() } returns Unit
        
        // Return stored values on get
        every { mockSharedPreferences.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String?>()
            storedStrings[key] ?: default
        }
    }

    @Test
    fun `saveLastRegionsInit stores regions correctly`() {
        val regions = listOf(
            RegionConfig("us", "https://us.frontegg.com", "client-us", "app-us"),
            RegionConfig("eu", "https://eu.frontegg.com", "client-eu", "app-eu")
        )
        val flags = ConfigCache.RegionsInitFlags(
            useAssetsLinks = true,
            useChromeCustomTabs = false,
            mainActivityClassName = "com.test.MainActivity",
            useDiskCacheWebview = true,
            useLegacySocialLoginFlow = false
        )
        
        ConfigCache.saveLastRegionsInit(mockContext, regions, flags)
        
        verify { mockEditor.putString("regions_json", any()) }
        verify { mockEditor.putString("regions_flags_json", any()) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `saveLastRegionsInit handles null applicationId`() {
        val regions = listOf(
            RegionConfig("us", "https://us.frontegg.com", "client-us", null)
        )
        val flags = ConfigCache.RegionsInitFlags(
            useAssetsLinks = false,
            useChromeCustomTabs = false,
            mainActivityClassName = null,
            useDiskCacheWebview = false
        )
        
        ConfigCache.saveLastRegionsInit(mockContext, regions, flags)
        
        // Should not throw
        verify { mockEditor.apply() }
    }

    @Test
    fun `loadLastRegionsInit returns null when no data`() {
        every { mockSharedPreferences.getString("regions_json", null) } returns null
        
        val result = ConfigCache.loadLastRegionsInit(mockContext)
        
        assert(result == null)
    }

    @Test
    fun `loadLastRegionsInit returns null when flags missing`() {
        storedStrings["regions_json"] = "[{\"key\":\"us\",\"baseUrl\":\"https://us.frontegg.com\",\"clientId\":\"client-us\"}]"
        every { mockSharedPreferences.getString("regions_flags_json", null) } returns null
        
        val result = ConfigCache.loadLastRegionsInit(mockContext)
        
        assert(result == null)
    }

    @Test
    fun `loadLastRegionsInit parses stored data correctly`() {
        storedStrings["regions_json"] = "[{\"key\":\"us\",\"baseUrl\":\"https://us.frontegg.com\",\"clientId\":\"client-us\",\"applicationId\":\"app-us\"}]"
        storedStrings["regions_flags_json"] = "{\"useAssetsLinks\":true,\"useChromeCustomTabs\":false,\"mainActivityClassName\":\"com.test.MainActivity\",\"useDiskCacheWebview\":true,\"useLegacySocialLoginFlow\":false}"
        
        val result = ConfigCache.loadLastRegionsInit(mockContext)
        
        assert(result != null)
        val (regions, flags) = result!!
        
        assert(regions.size == 1)
        assert(regions[0].key == "us")
        assert(regions[0].baseUrl == "https://us.frontegg.com")
        assert(regions[0].clientId == "client-us")
        assert(regions[0].applicationId == "app-us")
        
        assert(flags.useAssetsLinks)
        assert(!flags.useChromeCustomTabs)
        assert(flags.mainActivityClassName == "com.test.MainActivity")
        assert(flags.useDiskCacheWebview)
        assert(!flags.useLegacySocialLoginFlow)
    }

    @Test
    fun `loadLastRegionsInit handles null applicationId`() {
        storedStrings["regions_json"] = "[{\"key\":\"us\",\"baseUrl\":\"https://us.frontegg.com\",\"clientId\":\"client-us\"}]"
        storedStrings["regions_flags_json"] = "{\"useAssetsLinks\":false,\"useChromeCustomTabs\":false,\"useDiskCacheWebview\":false}"
        
        val result = ConfigCache.loadLastRegionsInit(mockContext)
        
        assert(result != null)
        val (regions, _) = result!!
        assert(regions[0].applicationId == null)
    }

    @Test
    fun `loadLastRegionsInit handles multiple regions`() {
        storedStrings["regions_json"] = "[{\"key\":\"us\",\"baseUrl\":\"https://us.frontegg.com\",\"clientId\":\"client-us\"},{\"key\":\"eu\",\"baseUrl\":\"https://eu.frontegg.com\",\"clientId\":\"client-eu\",\"applicationId\":\"app-eu\"}]"
        storedStrings["regions_flags_json"] = "{\"useAssetsLinks\":false,\"useChromeCustomTabs\":false,\"useDiskCacheWebview\":false}"
        
        val result = ConfigCache.loadLastRegionsInit(mockContext)
        
        assert(result != null)
        val (regions, _) = result!!
        assert(regions.size == 2)
        assert(regions[0].key == "us")
        assert(regions[1].key == "eu")
    }

    @Test
    fun `loadLastRegionsInit returns null for invalid JSON`() {
        storedStrings["regions_json"] = "invalid json"
        storedStrings["regions_flags_json"] = "also invalid"
        
        val result = ConfigCache.loadLastRegionsInit(mockContext)
        
        assert(result == null)
    }

    @Test
    fun `loadLastRegionsInit handles empty mainActivityClassName`() {
        storedStrings["regions_json"] = "[{\"key\":\"us\",\"baseUrl\":\"https://us.frontegg.com\",\"clientId\":\"client-us\"}]"
        storedStrings["regions_flags_json"] = "{\"useAssetsLinks\":false,\"useChromeCustomTabs\":false,\"mainActivityClassName\":\"\",\"useDiskCacheWebview\":false}"
        
        val result = ConfigCache.loadLastRegionsInit(mockContext)
        
        assert(result != null)
        val (_, flags) = result!!
        assert(flags.mainActivityClassName == null)
    }

    @Test
    fun `loadLastRegionsInit handles missing optional fields with defaults`() {
        storedStrings["regions_json"] = "[{\"key\":\"us\",\"baseUrl\":\"https://us.frontegg.com\",\"clientId\":\"client-us\"}]"
        storedStrings["regions_flags_json"] = "{}"
        
        val result = ConfigCache.loadLastRegionsInit(mockContext)
        
        assert(result != null)
        val (_, flags) = result!!
        assert(!flags.useAssetsLinks)
        assert(!flags.useChromeCustomTabs)
        assert(flags.mainActivityClassName == null)
        assert(!flags.useDiskCacheWebview)
        assert(!flags.useLegacySocialLoginFlow)
    }

    @Test
    fun `RegionsInitFlags default useLegacySocialLoginFlow is false`() {
        val flags = ConfigCache.RegionsInitFlags(
            useAssetsLinks = false,
            useChromeCustomTabs = false,
            mainActivityClassName = null,
            useDiskCacheWebview = false
        )
        
        assert(!flags.useLegacySocialLoginFlow)
    }

    @Test
    fun `roundtrip save and load preserves data`() {
        val originalRegions = listOf(
            RegionConfig("us", "https://us.frontegg.com", "client-us", "app-us"),
            RegionConfig("eu", "https://eu.frontegg.com", "client-eu", null)
        )
        val originalFlags = ConfigCache.RegionsInitFlags(
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            mainActivityClassName = "com.test.MainActivity",
            useDiskCacheWebview = true,
            useLegacySocialLoginFlow = true
        )
        
        ConfigCache.saveLastRegionsInit(mockContext, originalRegions, originalFlags)
        
        val result = ConfigCache.loadLastRegionsInit(mockContext)
        
        assert(result != null)
        val (loadedRegions, loadedFlags) = result!!
        
        assert(loadedRegions.size == originalRegions.size)
        assert(loadedRegions[0].key == originalRegions[0].key)
        assert(loadedRegions[0].applicationId == originalRegions[0].applicationId)
        assert(loadedRegions[1].applicationId == originalRegions[1].applicationId)
        
        assert(loadedFlags.useAssetsLinks == originalFlags.useAssetsLinks)
        assert(loadedFlags.useChromeCustomTabs == originalFlags.useChromeCustomTabs)
        assert(loadedFlags.mainActivityClassName == originalFlags.mainActivityClassName)
        assert(loadedFlags.useDiskCacheWebview == originalFlags.useDiskCacheWebview)
        assert(loadedFlags.useLegacySocialLoginFlow == originalFlags.useLegacySocialLoginFlow)
    }
}
