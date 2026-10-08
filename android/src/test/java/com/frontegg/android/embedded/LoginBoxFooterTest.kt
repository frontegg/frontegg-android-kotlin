package com.frontegg.android.embedded

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginBoxFooterTest {

    private fun footerPayload(
        url: Any? = "https://policies.google.com/privacy",
        hideBadge: Boolean = true
    ): Map<String, Any?> = mapOf(
        "hideCaptchaBadge" to hideBadge,
        "rows" to listOf(
            mapOf(
                "variant" to "fine",
                "segments" to listOf(
                    mapOf("text" to "Protected by reCAPTCHA — "),
                    mapOf("label" to "Privacy Policy", "url" to url)
                )
            )
        )
    )

    private fun singleRowFooter(vararg segments: Map<String, Any?>): Map<String, Any?> =
        mapOf("rows" to listOf(mapOf("variant" to "body", "segments" to segments.toList())))

    private fun payloadOf(script: String): JSONObject {
        val prefix = "window.__fronteggLoginBoxFooter = "
        assertTrue(script.startsWith(prefix))
        assertTrue(script.endsWith(";"))
        return JSONObject(script.removePrefix(prefix).removeSuffix(";"))
    }

    // region script

    @Test
    fun `script assigns the global the login box reads`() {
        val script = LoginBoxFooter.script(footerPayload())

        assertNotNull(script)
        assertTrue(script!!.startsWith("window.__fronteggLoginBoxFooter = {"))
        assertTrue(script.endsWith("};"))
        assertFalse(script.contains("document."))
        assertFalse(script.contains("data-test-id"))
        assertFalse(script.contains("MutationObserver"))
    }

    @Test
    fun `script carries the sanitized footer`() {
        val payload = payloadOf(LoginBoxFooter.script(footerPayload())!!)

        val segments = payload.getJSONArray("rows").getJSONObject(0).getJSONArray("segments")
        assertEquals("fine", payload.getJSONArray("rows").getJSONObject(0).getString("variant"))
        assertEquals("Protected by reCAPTCHA — ", segments.getJSONObject(0).getString("text"))
        assertEquals("Privacy Policy", segments.getJSONObject(1).getString("label"))
        assertEquals("https://policies.google.com/privacy", segments.getJSONObject(1).getString("url"))
    }

    @Test
    fun `the badge is only hidden when asked`() {
        val hiding = payloadOf(LoginBoxFooter.script(footerPayload(hideBadge = true))!!)
        assertTrue(hiding.getBoolean("hideCaptchaBadge"))

        val notHiding = payloadOf(LoginBoxFooter.script(footerPayload(hideBadge = false))!!)
        assertFalse(notHiding.getBoolean("hideCaptchaBadge"))
    }

    @Test
    fun `quotes in footer copy do not break the script`() {
        val script = LoginBoxFooter.script(singleRowFooter(mapOf("text" to "Don't \"stop\"")))

        val text = payloadOf(script!!).getJSONArray("rows").getJSONObject(0)
            .getJSONArray("segments").getJSONObject(0).getString("text")
        assertEquals("Don't \"stop\"", text)
    }

    @Test
    fun `line separators are escaped for javascript source`() {
        val script = LoginBoxFooter.script(singleRowFooter(mapOf("text" to "a\u2028b\u2029c")))

        assertNotNull(script)
        assertFalse(script!!.contains("\u2028"))
        assertFalse(script.contains("\u2029"))
        assertTrue(script.contains("\\u2028"))
        assertTrue(script.contains("\\u2029"))
    }

    @Test
    fun `no footer produces no script`() {
        assertNull(LoginBoxFooter.script(null))
        assertNull(LoginBoxFooter.script(mapOf("rows" to emptyList<Any>())))
    }

    // endregion

    // region footer validation

    @Test
    fun `unsafe schemes degrade to plain text`() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "otherapp://sign-up"
        ).forEach { url ->
            val sanitized = LoginBoxFooter.sanitizedFooter(footerPayload(url = url))
            assertNotNull("expected $url to still produce a footer", sanitized)

            val segments = sanitized!!.getJSONArray("rows")
                .getJSONObject(0)
                .getJSONArray("segments")

            assertEquals("expected $url to keep both segments", 2, segments.length())
            assertEquals("Privacy Policy", segments.getJSONObject(1).getString("text"))
            assertFalse(segments.getJSONObject(1).has("url"))
        }
    }

    @Test
    fun `script schemes stay rejected even when the predicate accepts everything`() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "blob:https://x/y",
            "about:blank",
            "vbscript:msgbox",
            "intent://x#Intent;end",
            "content://settings/secure"
        ).forEach {
            assertNull(
                "expected $it to be rejected",
                LoginBoxFooter.sanitizedLinkUrl(it) { true }
            )
        }
    }

    @Test
    fun `http and https links are accepted`() {
        assertEquals(
            "https://app.example.com/x",
            LoginBoxFooter.sanitizedLinkUrl("https://app.example.com/x")
        )
        assertEquals(
            "http://localhost:3000/x",
            LoginBoxFooter.sanitizedLinkUrl("http://localhost:3000/x")
        )
        assertEquals(
            "HTTPS://app.example.com/x",
            LoginBoxFooter.sanitizedLinkUrl("HTTPS://app.example.com/x")
        )
    }

    @Test
    fun `relative and empty links are rejected`() {
        assertNull(LoginBoxFooter.sanitizedLinkUrl("/users/sign_up/select"))
        assertNull(LoginBoxFooter.sanitizedLinkUrl(""))
        assertNull(LoginBoxFooter.sanitizedLinkUrl(null))
        assertNull(LoginBoxFooter.sanitizedLinkUrl("https://"))
    }

    @Test
    fun `an app-registered scheme is accepted`() {
        assertEquals(
            "healthie://sign-up",
            LoginBoxFooter.sanitizedLinkUrl("healthie://sign-up") { it == "healthie" }
        )
        assertEquals(
            "Healthie://sign-up",
            LoginBoxFooter.sanitizedLinkUrl("Healthie://sign-up") { it == "healthie" }
        )
        assertNull(
            LoginBoxFooter.sanitizedLinkUrl("otherapp://sign-up") { it == "healthie" }
        )
    }

    @Test
    fun `oauth-shaped app-scheme links are rejected`() {
        listOf(
            "healthie://sign-up?code=INVITE",
            "healthie://sign-up?error=x",
            "healthie://sign-up?error_description=x",
            "healthie://sign-up?plan=pro&code=INVITE",
            "healthie://app/#/sign-up?code=INVITE",
            "healthie://sign-up?plan=pro#&error=x"
        ).forEach {
            assertNull(
                "expected $it to be rejected",
                LoginBoxFooter.sanitizedLinkUrl(it) { scheme -> scheme == "healthie" }
            )
        }
        assertEquals(
            "healthie://sign-up?plan=pro#section",
            LoginBoxFooter.sanitizedLinkUrl("healthie://sign-up?plan=pro#section") { it == "healthie" }
        )
    }

    @Test
    fun `oauth callback parameters are detected in the query and the fragment`() {
        assertTrue(LoginBoxFooter.carriesOAuthCallbackParameter("myapp://sign-up?code=INVITE"))
        assertTrue(LoginBoxFooter.carriesOAuthCallbackParameter("myapp://app/#/sign-up?code=INVITE"))
        assertTrue(LoginBoxFooter.carriesOAuthCallbackParameter("myapp://sign-up?plan=pro#&error=x"))
        assertTrue(LoginBoxFooter.carriesOAuthCallbackParameter("myapp://sign-up?co%64e=x"))
        assertFalse(LoginBoxFooter.carriesOAuthCallbackParameter("myapp://sign-up?plan=pro#section"))
        assertFalse(LoginBoxFooter.carriesOAuthCallbackParameter("myapp://sign-up?promo_code=x"))
        assertFalse(LoginBoxFooter.carriesOAuthCallbackParameter("myapp://code/error"))
    }

    @Test
    fun `an empty footer produces nothing`() {
        assertNull(LoginBoxFooter.sanitizedFooter(null))
        assertNull(LoginBoxFooter.sanitizedFooter(emptyMap()))
        assertNull(LoginBoxFooter.sanitizedFooter(mapOf("rows" to emptyList<Any>())))
        assertNull(LoginBoxFooter.sanitizedFooter(singleRowFooter(mapOf("label" to ""), mapOf("text" to ""))))
    }

    @Test
    fun `an unknown variant falls back to body`() {
        val sanitized = LoginBoxFooter.sanitizedFooter(
            mapOf(
                "rows" to listOf(
                    mapOf("variant" to "enormous", "segments" to listOf(mapOf("text" to "hi")))
                )
            )
        )

        assertNotNull(sanitized)
        assertEquals("body", sanitized!!.getJSONArray("rows").getJSONObject(0).getString("variant"))
    }

    @Test
    fun `rejected link urls list only links that render as text`() {
        val footer = singleRowFooter(
            mapOf("label" to "Privacy", "url" to "https://policies.google.com/privacy"),
            mapOf("label" to "Script", "url" to "javascript:alert(1)"),
            mapOf("label" to "Unregistered", "url" to "otherapp://sign-up"),
            mapOf("label" to "Sign up", "url" to "healthie://sign-up"),
            mapOf("text" to "plain text")
        )

        assertEquals(
            listOf("javascript:alert(1)", "otherapp://sign-up"),
            LoginBoxFooter.rejectedLinkUrls(footer) { it == "healthie" }
        )
        assertTrue(LoginBoxFooter.rejectedLinkUrls(null).isEmpty())
    }

    @Test
    fun `rejected link urls omit query and fragment`() {
        val footer = singleRowFooter(
            mapOf("label" to "Invite", "url" to "healthie://sign-up?code=INVITE#ref=abc")
        )

        assertEquals(
            listOf("healthie://sign-up"),
            LoginBoxFooter.rejectedLinkUrls(footer) { it == "healthie" }
        )
    }

    @Test
    fun `rejected link urls report non-string urls`() {
        val footer = footerPayload(url = java.net.URI("https://policies.google.com/privacy"))

        assertEquals(
            listOf("https://policies.google.com/privacy"),
            LoginBoxFooter.rejectedLinkUrls(footer)
        )
    }

    // endregion

    // region link hand-off

    @Test
    fun `external urls cover only http links`() {
        val links = LoginBoxFooter.footerLinks(
            singleRowFooter(
                mapOf("label" to "Privacy", "url" to "https://policies.google.com/privacy"),
                mapOf("label" to "Terms", "url" to "http://example.com/terms"),
                mapOf("label" to "Sign up", "url" to "healthie://sign-up"),
                mapOf("text" to "no link here")
            )
        ) { it == "healthie" }

        assertEquals(
            setOf("https://policies.google.com/privacy", "http://example.com/terms"),
            links.externalUrls
        )
    }

    @Test
    fun `app schemes cover only app-scheme links`() {
        val links = LoginBoxFooter.footerLinks(
            singleRowFooter(
                mapOf("label" to "Privacy", "url" to "https://policies.google.com/privacy"),
                mapOf("label" to "Sign up", "url" to "Healthie://sign-up"),
                mapOf("label" to "Other", "url" to "otherapp://x")
            )
        ) { it == "healthie" }

        assertEquals(setOf("healthie"), links.appSchemes)
        assertTrue(links.isAppHandoff("healthie"))
        assertTrue(links.isAppHandoff("HEALTHIE"))
        assertFalse(links.isAppHandoff("otherapp"))
        assertFalse(links.isAppHandoff("https"))
        assertFalse(links.isAppHandoff(null))
    }

    @Test
    fun `external footer link matches the webview canonical form`() {
        val links = LoginBoxFooter.footerLinks(footerPayload(url = "HTTPS://Policies.Google.com:443"))

        assertTrue(links.isExternal("https://policies.google.com/"))
    }

    @Test
    fun `external footer link drops only the default port`() {
        val links = LoginBoxFooter.footerLinks(footerPayload(url = "http://example.com:80/a"))

        assertTrue(links.isExternal("http://example.com/a"))
        assertFalse(
            LoginBoxFooter.footerLinks(footerPayload(url = "http://example.com:8080/a"))
                .isExternal("http://example.com/a")
        )
    }

    @Test
    fun `external footer link resolves dot segments`() {
        val links = LoginBoxFooter.footerLinks(
            footerPayload(url = "https://policies.google.com/legal/../privacy")
        )

        assertTrue(links.isExternal("https://policies.google.com/privacy"))
    }

    @Test
    fun `external footer link does not match other urls`() {
        val links = LoginBoxFooter.footerLinks(footerPayload())

        assertFalse(links.isExternal("https://policies.google.com/terms"))
        assertFalse(links.isExternal("https://policies.google.com/privacy/more"))
        assertFalse(links.isExternal("not a url"))
        assertFalse(LoginBoxFooter.Links().isExternal("https://policies.google.com/privacy"))
    }

    @Test
    fun `canonical link key normalizes like webview`() {
        assertEquals("https://example.com/", LoginBoxFooter.canonicalLinkKey("HTTPS://EXAMPLE.com"))
        assertEquals("https://example.com/", LoginBoxFooter.canonicalLinkKey("https://example.com:443"))
        assertEquals("http://example.com:443/", LoginBoxFooter.canonicalLinkKey("http://example.com:443"))
        assertEquals(
            "https://example.com/Path?Q=1#F",
            LoginBoxFooter.canonicalLinkKey("https://Example.com/Path?Q=1#F")
        )
        assertNull(LoginBoxFooter.canonicalLinkKey("https://"))
        assertNull(LoginBoxFooter.canonicalLinkKey("not a url"))
    }

    @Test
    fun `links are empty without a footer`() {
        val links = LoginBoxFooter.footerLinks(null)

        assertTrue(links.externalUrls.isEmpty())
        assertTrue(links.appSchemes.isEmpty())
        assertTrue(LoginBoxFooter.footerLinks(mapOf("rows" to emptyList<Any>())).externalUrls.isEmpty())
    }

    @Test
    fun `links exclude rejected urls`() {
        assertTrue(
            LoginBoxFooter.footerLinks(footerPayload(url = "javascript:alert(1)")).externalUrls.isEmpty()
        )
        assertTrue(
            LoginBoxFooter.footerLinks(footerPayload(url = "healthie://x?code=1")) { it == "healthie" }
                .appSchemes.isEmpty()
        )
    }

    // endregion
}
