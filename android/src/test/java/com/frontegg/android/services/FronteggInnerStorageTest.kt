package com.frontegg.android.services

import com.frontegg.android.regions.RegionConfig
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FronteggInnerStorageTest {

    private lateinit var storage: FronteggInnerStorage

    @Before
    fun setUp() {
        storage = FronteggInnerStorage()
    }

    @Test
    fun `fill() should correctly store values`() {
        val mockRegion = mockk<RegionConfig>()
        val mockMainActivity = Any::class.java

        storage.fill(
                baseUrl = "https://example.com",
                clientId = "test-client",
                applicationId = "app-123",
                isEmbeddedMode = false,
                regions = listOf(mockRegion),
                selectedRegion = mockRegion,
                handleLoginWithSocialLogin = false,
                handleLoginWithSocialLoginProvider = false,
                handleLoginWithCustomSocialLoginProvider = false,
                customUserAgent = "Custom-UA",
                handleLoginWithSSO = true,
                shouldPromptSocialLoginConsent = false,
                useAssetsLinks = true,
                useChromeCustomTabs = true,
                mainActivityClass = mockMainActivity,
                packageName = "com.example.app"
        )

        assertEquals("https://example.com", storage.baseUrl)
        assertEquals("test-client", storage.clientId)
        assertEquals("app-123", storage.applicationId)
        assertFalse(storage.isEmbeddedMode)
        assertEquals(1, storage.regions.size)
        assertEquals(mockRegion, storage.selectedRegion)
        assertFalse(storage.handleLoginWithSocialLogin)
        assertFalse(storage.handleLoginWithSocialLoginProvider)
        assertFalse(storage.handleLoginWithCustomSocialLoginProvider)
        assertEquals("Custom-UA", storage.customUserAgent)
        assertTrue(storage.handleLoginWithSSO)
        assertFalse(storage.shouldPromptSocialLoginConsent)
        assertTrue(storage.useAssetsLinks)
        assertTrue(storage.useChromeCustomTabs)
        assertEquals(mockMainActivity, storage.mainActivityClass)
        assertEquals("com.example.app", storage.packageName)
    }

    @Test
    fun `fill() should correctly handle default values`() {
        storage.fill(
                baseUrl = "https://default.com",
                clientId = "default-client",
                applicationId = null, // Nullable field
                packageName = "com.example.default"
        )

        assertEquals("https://default.com", storage.baseUrl)
        assertEquals("default-client", storage.clientId)
        assertNull(storage.applicationId) // Should allow null
        assertTrue(storage.isEmbeddedMode) // Default value
        assertTrue(storage.handleLoginWithSocialLogin)
        assertTrue(storage.handleLoginWithSocialLoginProvider)
        assertTrue(storage.handleLoginWithCustomSocialLoginProvider)
        assertFalse(storage.handleLoginWithSSO)
        assertTrue(storage.shouldPromptSocialLoginConsent)
        assertFalse(storage.useAssetsLinks)
        assertFalse(storage.useChromeCustomTabs)
        assertNull(storage.mainActivityClass) // Should be null by default
        assertEquals("com.example.default", storage.packageName)
    }

    @Test
    fun `regions should return empty list if not set`() {
        assertTrue(storage.regions.isEmpty())
    }

    @Test
    fun `selectedRegion should return null if not set`() {
        assertNull(storage.selectedRegion)
    }

    @Test
    fun `customUserAgent should return null if not set`() {
        assertNull(storage.customUserAgent)
    }
}
