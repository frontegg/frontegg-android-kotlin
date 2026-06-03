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

    // MARK: - refreshCookieName(clientId) — pins the cookie-name format the
    // diagnostic logger uses, so its output is comparable against Api.kt's
    // cookieName format byte-for-byte.

    @Test
    fun `refreshCookieName strips only first dash not all dashes`() {
        val name = AdminPortalActivity.refreshCookieName(
            "b1c2d3e4-1234-5678-9abc-deadbeef0000"
        )
        assertEquals(
            "Must strip ONLY the first dash to match Api.kt refreshCookieName format.",
            "fe_refresh_b1c2d3e41234-5678-9abc-deadbeef0000",
            name
        )
        assertTrue("Cookie name must keep the fe_refresh_ prefix", name.startsWith("fe_refresh_"))
        assertTrue("Subsequent dashes must remain in the cookie name.", name.contains("-"))
    }

    @Test
    fun `refreshCookieName handles client ids with no dashes`() {
        val name = AdminPortalActivity.refreshCookieName("nodashesclient")
        assertEquals("fe_refresh_nodashesclient", name)
    }

    // MARK: - valuePrefix — never leaks a full token in logs, even if
    // someone changes the diagnostic call site by accident.

    @Test
    fun `valuePrefix never returns the full token for a typical 36-char UUID`() {
        val uuid = "b08bcd63-1234-5678-9abc-deadbeef0000"
        val prefix = AdminPortalActivity.valuePrefix(uuid)
        assertTrue("prefix should start with first 8 chars", prefix.startsWith("b08bcd63"))
        assertTrue("prefix should include length marker", prefix.contains("len="))
        assertFalse(
            "prefix MUST NOT contain the full value (security invariant)",
            prefix.contains(uuid)
        )
    }

    @Test
    fun `valuePrefix handles empty string`() {
        assertEquals("<empty>", AdminPortalActivity.valuePrefix(""))
    }

    @Test
    fun `valuePrefix masks short values entirely`() {
        // For very short values (under 9 chars) we hide the value entirely
        // rather than reveal most of it — the prefix-of-8 strategy would
        // expose almost the whole secret.
        val prefix = AdminPortalActivity.valuePrefix("abc123")
        assertEquals("<short len=6>", prefix)
        assertFalse(prefix.contains("abc123"))
    }

    // MARK: - countFeCookies — sanity-checks the parser used in the
    // diagnostic log, so a malformed header doesn't blow up the activity.

    @Test
    fun `countFeCookies returns zero for null header`() {
        assertEquals(0, AdminPortalActivity.countFeCookies(null))
    }

    @Test
    fun `countFeCookies returns zero for empty header`() {
        assertEquals(0, AdminPortalActivity.countFeCookies(""))
    }

    @Test
    fun `countFeCookies counts only fe_refresh and fe_device prefixes`() {
        val header = "_ga=GA1.1.x; fe_refresh_abc=val1; tracking=t; fe_device_xyz=val2; fe_other=z"
        // fe_refresh_abc + fe_device_xyz = 2; fe_other is not session-relevant
        assertEquals(2, AdminPortalActivity.countFeCookies(header))
    }

    @Test
    fun `countFeCookies tolerates trailing or leading whitespace`() {
        val header = "  fe_refresh_abc=val1 ; fe_device_xyz=val2"
        assertEquals(2, AdminPortalActivity.countFeCookies(header))
    }
}
