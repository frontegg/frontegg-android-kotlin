package com.frontegg.android.utils

import android.content.Context
import com.frontegg.android.models.FronteggConstants
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SentryHelperTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        SentryHelper.resetForTesting()
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.packageName } returns "com.frontegg.test"
    }

    @After
    fun tearDown() {
        SentryHelper.resetForTesting()
    }

    @Test
    fun `MOBILE_ENABLE_LOGGING_FLAG has correct value`() {
        assertEquals("mobile-enable-logging", SentryHelper.MOBILE_ENABLE_LOGGING_FLAG)
    }

    @Test
    fun `isEnabled returns false before initialization`() {
        assertFalse(SentryHelper.isEnabled())
    }

    @Test
    fun `isInitialized returns false before initialization`() {
        assertFalse(SentryHelper.isInitialized())
    }

    @Test
    fun `prepare stores context for later use`() {
        assertNull(SentryHelper.getAppContextOrNull())
        
        SentryHelper.prepare(mockContext, null)
        
        assertNotNull(SentryHelper.getAppContextOrNull())
    }

    @Test
    fun `prepare does not enable Sentry`() {
        SentryHelper.prepare(mockContext, null)
        
        assertFalse(SentryHelper.isEnabled())
        assertFalse(SentryHelper.isInitialized())
    }

    @Test
    fun `enableFromFeatureFlag with false does not initialize Sentry`() {
        SentryHelper.prepare(mockContext, null)
        
        SentryHelper.enableFromFeatureFlag(false)
        
        assertFalse(SentryHelper.isEnabled())
        assertFalse(SentryHelper.isInitialized())
    }

    @Test
    fun `enableFromFeatureFlag without prepare does not crash and does not initialize`() {
        // Should not throw, just log warning
        SentryHelper.enableFromFeatureFlag(true)
        
        // Still not initialized because context was not prepared
        assertFalse(SentryHelper.isInitialized())
    }

    @Test
    fun `enableFromFeatureFlag with false multiple times keeps Sentry disabled`() {
        SentryHelper.prepare(mockContext, null)
        
        SentryHelper.enableFromFeatureFlag(false)
        SentryHelper.enableFromFeatureFlag(false)
        SentryHelper.enableFromFeatureFlag(false)
        
        assertFalse(SentryHelper.isEnabled())
        assertFalse(SentryHelper.isInitialized())
    }

    @Test
    fun `resetForTesting clears context`() {
        SentryHelper.prepare(mockContext, null)
        assertNotNull(SentryHelper.getAppContextOrNull())
        
        SentryHelper.resetForTesting()
        
        assertNull(SentryHelper.getAppContextOrNull())
    }

    @Test
    fun `resetForTesting clears enabled and initialized flags`() {
        SentryHelper.prepare(mockContext, null)
        
        // After reset, should be cleared
        SentryHelper.resetForTesting()
        
        assertFalse(SentryHelper.isEnabled())
        assertFalse(SentryHelper.isInitialized())
    }

    @Test
    fun `logging methods do nothing when not enabled`() {
        // These should not throw even when Sentry is not enabled
        SentryHelper.logError(RuntimeException("test"))
        SentryHelper.logMessage("test message")
        SentryHelper.addBreadcrumb("test breadcrumb")
        SentryHelper.setUser("user-id")
        SentryHelper.clearUser()
        SentryHelper.setTag("key", "value")
        SentryHelper.setContext("key", mapOf("nested" to "value"))
        SentryHelper.setBaseUrl("https://test.com")
        
        // No assertions needed - just verify no exceptions thrown
        assertFalse(SentryHelper.isEnabled())
    }

    @Test
    fun `sanitizeBreadcrumbPayload strips URL query`() {
        val raw = mapOf("url" to "https://example.com/oauth/callback?code=secret&state=abc")
        val out = SentryHelper.sanitizeBreadcrumbPayloadForTesting(raw)
        assertEquals("https://example.com/oauth/callback", out["url"])
    }

    @Test
    fun `sanitizeBreadcrumbPayload redacts sensitive keys`() {
        val raw = mapOf(
            "Authorization" to "Bearer x",
            "x-amz-security-token" to "tok",
            "safe" to "ok",
        )
        val out = SentryHelper.sanitizeBreadcrumbPayloadForTesting(raw)
        assertEquals("[redacted]", out["Authorization"])
        assertEquals("[redacted]", out["x-amz-security-token"])
        assertEquals("ok", out["safe"])
    }

    @Test
    fun `sanitizeBreadcrumbPayload redacts http query and fragment keys`() {
        val raw = mapOf(
            "http.query" to "token=1",
            "http.fragment" to "frag",
        )
        val out = SentryHelper.sanitizeBreadcrumbPayloadForTesting(raw)
        assertEquals("[redacted]", out["http.query"])
        assertEquals("[redacted]", out["http.fragment"])
    }

    @Test
    fun `sanitizeBreadcrumbPayload redacts PKCE and WebAuthn style keys`() {
        val raw = mapOf(
            "code_verifier" to "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
            "clientDataJSON" to """{"type":"webauthn.get"}""",
            "challenge" to "binary-challenge-material",
            "userHandle" to "dXNlcg",
        )
        val out = SentryHelper.sanitizeBreadcrumbPayloadForTesting(raw)
        assertEquals("[redacted]", out["code_verifier"])
        assertEquals("[redacted]", out["clientDataJSON"])
        assertEquals("[redacted]", out["challenge"])
        assertEquals("[redacted]", out["userHandle"])
    }

    @Test
    fun `sanitizeBreadcrumbPayload strips query from location`() {
        val raw = mapOf(
            "location" to "https://idp.example.com/callback?code=abc&session_state=xyz",
        )
        val out = SentryHelper.sanitizeBreadcrumbPayloadForTesting(raw)
        assertEquals("https://idp.example.com/callback", out["location"])
    }

    @Test
    fun `prepare stores constants for later use`() {
        val constants = FronteggConstants(
            baseUrl = "https://test.frontegg.com",
            clientId = "test-client",
            applicationId = "test-app",
            useAssetsLinks = false,
            useChromeCustomTabs = false,
            deepLinkScheme = null,
            useDiskCacheWebview = false,
            mainActivityClass = null,
            disableAutoRefresh = false,
            enableSessionPerTenant = false,
            sentryMaxQueueSize = 100,
            fronteggOrganization = null
        )
        
        SentryHelper.prepare(mockContext, constants)
        
        // Verify context is stored
        assertNotNull(SentryHelper.getAppContextOrNull())
        
        // Verify Sentry is not yet enabled (waiting for feature flag)
        assertFalse(SentryHelper.isEnabled())
    }

    @Test
    fun `Sentry stays disabled when feature flag is never enabled`() {
        val constants = FronteggConstants(
            baseUrl = "https://test.frontegg.com",
            clientId = "test-client",
            applicationId = null,
            useAssetsLinks = false,
            useChromeCustomTabs = false,
            deepLinkScheme = null,
            useDiskCacheWebview = false,
            mainActivityClass = null,
            disableAutoRefresh = false,
            enableSessionPerTenant = false,
            sentryMaxQueueSize = 50,
            fronteggOrganization = null
        )
        
        SentryHelper.prepare(mockContext, constants)
        
        // Simulate feature flags loaded without mobile-enable-logging
        SentryHelper.enableFromFeatureFlag(false)
        
        assertFalse(SentryHelper.isEnabled())
        assertFalse(SentryHelper.isInitialized())
    }
}
