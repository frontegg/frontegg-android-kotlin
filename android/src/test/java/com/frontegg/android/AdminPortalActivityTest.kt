package com.frontegg.android

import android.app.Activity
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdminPortalActivityTest {

    @Test
    fun `buildPortalUrl appends oauth_portal path`() {
        val url = AdminPortalActivity.buildPortalUrl(
            baseUrl = "https://example.frontegg.com",
            applicationId = null
        )
        assertEquals("https://example.frontegg.com/oauth/portal", url)
    }

    @Test
    fun `buildPortalUrl appends appId query param when provided`() {
        val url = AdminPortalActivity.buildPortalUrl(
            baseUrl = "https://example.frontegg.com",
            applicationId = "app-123"
        )
        // Without ?appId= the portal renders "Application not found" — this is the
        // contract that prevents that.
        assertEquals("https://example.frontegg.com/oauth/portal?appId=app-123", url)
    }

    @Test
    fun `buildPortalUrl omits appId when empty string`() {
        val url = AdminPortalActivity.buildPortalUrl(
            baseUrl = "https://example.frontegg.com",
            applicationId = ""
        )
        // Empty appId is treated like null — the portal will fall back to the
        // tenant default rather than receiving `?appId=`.
        assertFalse("URL should not contain appId param", url.contains("appId"))
        assertEquals("https://example.frontegg.com/oauth/portal", url)
    }

    @Test
    fun `buildPortalUrl preserves base url subpath`() {
        // Some tenants are served from a subpath (e.g. when fronted by a reverse
        // proxy). The portal endpoint must be appended onto whatever path the
        // baseUrl already carries.
        val url = AdminPortalActivity.buildPortalUrl(
            baseUrl = "https://example.frontegg.com/tenant-a",
            applicationId = "app-123"
        )
        assertEquals(
            "https://example.frontegg.com/tenant-a/oauth/portal?appId=app-123",
            url
        )
    }

    @Test
    fun `buildPortalUrl preserves base url with trailing slash`() {
        val url = AdminPortalActivity.buildPortalUrl(
            baseUrl = "https://example.frontegg.com/",
            applicationId = null
        )
        // Uri.appendEncodedPath collapses extra slashes; just assert the result is
        // a valid portal URL targeting the right host.
        assertTrue(url.startsWith("https://example.frontegg.com"))
        assertTrue(url.endsWith("/oauth/portal"))
    }

    @Test
    fun `buildPortalUrl url-encodes appId values containing special characters`() {
        val url = AdminPortalActivity.buildPortalUrl(
            baseUrl = "https://example.frontegg.com",
            applicationId = "app id with spaces"
        )
        // appendQueryParameter does percent-encoding; spaces become %20 (or +).
        assertTrue(
            "appId should be percent-encoded, got: $url",
            url.contains("appId=app%20id%20with%20spaces") ||
                url.contains("appId=app+id+with+spaces")
        )
    }

    @Test
    fun `viewport override JS targets desktop width`() {
        // The portal renders mobile when window.innerWidth < ~960 — width=1024 is
        // what flips Material-UI's responsive hooks back into desktop mode.
        assertTrue(
            "expected width=1024 in viewport JS",
            AdminPortalActivity.VIEWPORT_OVERRIDE_JS.contains("width=1024")
        )
    }

    @Test
    fun `viewport override JS allows user scaling`() {
        // user-scalable=yes lets the user pinch-zoom (paired with WebView
        // builtInZoomControls). user-scalable=no would silently disable zoom.
        assertTrue(
            "expected user-scalable=yes in viewport JS",
            AdminPortalActivity.VIEWPORT_OVERRIDE_JS.contains("user-scalable=yes")
        )
    }

    @Test
    fun `viewport override JS bounds zoom scale`() {
        // Bounded zoom range keeps pan offsets sane at extremes — without these,
        // wide zoom-out or aggressive zoom-in can break the WebView's pan math.
        val js = AdminPortalActivity.VIEWPORT_OVERRIDE_JS
        assertTrue("expected minimum-scale bound", js.contains("minimum-scale=0.3"))
        assertTrue("expected maximum-scale bound", js.contains("maximum-scale=3"))
    }

    @Test
    fun `viewport override JS installs MutationObserver against SPA route changes`() {
        // The portal's React Router rewrites the viewport meta on route change;
        // without an observer, back/forward navigation silently flips the layout
        // to mobile after a few hops.
        val js = AdminPortalActivity.VIEWPORT_OVERRIDE_JS
        assertTrue("expected MutationObserver in viewport JS", js.contains("MutationObserver"))
        assertTrue(
            "observer should be installed once and remembered on window",
            js.contains("__fronteggAdminPortalViewportObserver")
        )
    }

    @Test
    fun `open starts AdminPortalActivity with correct component`() {
        val activity = mockk<Activity>(relaxed = true)
        val intentSlot = slot<android.content.Intent>()
        every { activity.startActivity(capture(intentSlot)) } returns Unit

        AdminPortalActivity.open(activity)

        verify(exactly = 1) { activity.startActivity(any()) }
        val started = intentSlot.captured
        assertEquals(
            AdminPortalActivity::class.java.name,
            started.component?.className
        )
    }


}
