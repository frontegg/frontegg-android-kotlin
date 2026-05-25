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

    // MARK: - refreshCookieName

    @Test
    fun `refreshCookieName uses clientId when applicationId is null`() {
        val name = AdminPortalActivity.refreshCookieName(
            clientId = "b1c2d3e4-1234-5678-9abc-deadbeef0000",
            applicationId = null
        )
        assertEquals("fe_refresh_b1c2d3e4123456789abcdeadbeef0000", name)
        assertFalse("dashes must be stripped", name.contains("-"))
    }

    @Test
    fun `refreshCookieName uses clientId when applicationId is empty`() {
        val name = AdminPortalActivity.refreshCookieName(
            clientId = "client-123-abc",
            applicationId = ""
        )
        assertEquals("fe_refresh_client123abc", name)
    }

    @Test
    fun `refreshCookieName uses applicationId when present`() {
        // Mirrors frontegg-nextjs CookieManager.refreshTokenKey: when appId is
        // set (multi-app workspace), the cookie is scoped to the appId instead
        // of the clientId so the portal recognizes the per-app session.
        val name = AdminPortalActivity.refreshCookieName(
            clientId = "client-id-here",
            applicationId = "app-id-multi-tenant"
        )
        assertEquals("fe_refresh_appidmultitenant", name)
    }

    @Test
    fun `refreshCookieName matches the iOS SDK and the Next_js cookie format`() {
        // Cross-platform invariant: the cookie name format must match the
        // iOS SDK's AdminPortalWebView.refreshCookieName and the Next.js
        // CookieManager.refreshTokenKey. The Frontegg auth backend reads
        // this exact name, so all three SDKs must agree.
        val name = AdminPortalActivity.refreshCookieName(
            clientId = "abc-def",
            applicationId = null
        )
        assertTrue("must start with fe_refresh_", name.startsWith("fe_refresh_"))
        assertFalse("must strip dashes", name.contains("-"))
    }

    // MARK: - buildRefreshCookieValue

    @Test
    fun `buildRefreshCookieValue returns null when refresh token is null`() {
        val cookie = AdminPortalActivity.buildRefreshCookieValue(
            refreshToken = null,
            baseUrl = "https://app.frontegg.com",
            clientId = "client",
            applicationId = null
        )
        // User not logged in → no cookie to bridge; portal falls back to its own login.
        assertEquals(null, cookie)
    }

    @Test
    fun `buildRefreshCookieValue returns null when refresh token is empty`() {
        val cookie = AdminPortalActivity.buildRefreshCookieValue(
            refreshToken = "",
            baseUrl = "https://app.frontegg.com",
            clientId = "client",
            applicationId = null
        )
        assertEquals(null, cookie)
    }

    @Test
    fun `buildRefreshCookieValue returns null when baseUrl is malformed`() {
        val cookie = AdminPortalActivity.buildRefreshCookieValue(
            refreshToken = "rt-jwt",
            baseUrl = "not a url",
            clientId = "client",
            applicationId = null
        )
        // Uri.parse of "not a url" yields scheme=null, host=null → no cookie
        // can be scoped, so we return null and the portal falls back to login.
        assertEquals(null, cookie)
    }

    @Test
    fun `buildRefreshCookieValue builds https cookie with Secure flag`() {
        val cookie = AdminPortalActivity.buildRefreshCookieValue(
            refreshToken = "rt-jwt-value-abc",
            baseUrl = "https://app.frontegg.com",
            clientId = "b1c2d3e4-1234",
            applicationId = null
        )
        assertEquals("fe_refresh_b1c2d3e41234", cookie?.name)
        assertEquals("https://app.frontegg.com", cookie?.urlForSetCookie)
        // Header includes name=value, Path=/, and Secure for HTTPS.
        assertTrue(cookie?.headerValue?.startsWith("fe_refresh_b1c2d3e41234=rt-jwt-value-abc") ?: false)
        assertTrue(cookie?.headerValue?.contains("Path=/") ?: false)
        assertTrue(cookie?.headerValue?.contains("Secure") ?: false)
    }

    @Test
    fun `buildRefreshCookieValue builds http cookie without Secure flag`() {
        // Useful for local-dev tenants on http://localhost. The cookie still
        // needs to be set so the portal recognizes the session in dev. The
        // port is intentionally stripped from the scoping URL — cookie
        // domain-matching ignores port, and downstream WebView requests to
        // `http://localhost:3000/...` still receive the cookie.
        val cookie = AdminPortalActivity.buildRefreshCookieValue(
            refreshToken = "rt-jwt",
            baseUrl = "http://localhost:3000",
            clientId = "client-1",
            applicationId = null
        )
        assertEquals("http://localhost", cookie?.urlForSetCookie)
        assertFalse("HTTP cookie must NOT be Secure", cookie?.headerValue?.contains("Secure") ?: true)
        assertTrue(cookie?.headerValue?.contains("Path=/") ?: false)
    }

    @Test
    fun `buildRefreshCookieValue scopes to applicationId when present`() {
        val cookie = AdminPortalActivity.buildRefreshCookieValue(
            refreshToken = "rt-jwt",
            baseUrl = "https://app.frontegg.com",
            clientId = "client-id",
            applicationId = "app-id-abc-123"
        )
        assertEquals("fe_refresh_appidabc123", cookie?.name)
    }

    @Test
    fun `buildRefreshCookieValue strips subpath from baseUrl in scoping URL`() {
        // Some tenants serve the SDK from a subpath, but the cookie's Domain
        // should be the host, not the host+subpath. Otherwise the portal
        // wouldn't receive the cookie on `/oauth/portal` if that path isn't
        // under the original subpath.
        val cookie = AdminPortalActivity.buildRefreshCookieValue(
            refreshToken = "rt-jwt",
            baseUrl = "https://app.frontegg.com/tenant-a",
            clientId = "client",
            applicationId = null
        )
        assertEquals("https://app.frontegg.com", cookie?.urlForSetCookie)
    }
}
