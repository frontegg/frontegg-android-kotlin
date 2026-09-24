package com.frontegg.android.embedded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginBoxFooterTest {

    // region script

    /** A footer with one usable row, for tests that only care that it is valid. */
    private fun footerPayload(
        url: String = "https://policies.google.com/privacy",
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

    @Test
    fun `footer alone is enough to build a script`() {
        val script = LoginBoxFooter.script(footerPayload())

        assertNotNull(script)
        assertTrue(script!!.contains("https://policies.google.com/privacy"))
        assertTrue(script.contains("[data-test-id=\"root-element\"]"))
    }

    @Test
    fun `the badge is only hidden when asked`() {
        val hiding = LoginBoxFooter.script(footerPayload(hideBadge = true))
        assertTrue(hiding!!.contains("\"hideCaptchaBadge\":true"))

        val notHiding = LoginBoxFooter.script(footerPayload(hideBadge = false))
        assertTrue(notHiding!!.contains("\"hideCaptchaBadge\":false"))
    }

    /** Host copy must never be interpreted as markup. */
    @Test
    fun `footer copy is rendered as text not html`() {
        val script = LoginBoxFooter.script(footerPayload())

        assertNotNull(script)
        assertTrue(script!!.contains("anchor.textContent = segment.label;"))
        assertTrue(script.contains("createTextNode(segment.text)"))
        assertFalse(script.contains(".innerHTML ="))
        assertFalse(script.contains("insertAdjacentHTML"))
    }

    /**
     * The footer follows the login screen only, matching the React SDK where `boxFooter`
     * is configured under `login`.
     */
    @Test
    fun `footer is scoped to the login screen`() {
        val script = LoginBoxFooter.script(footerPayload())

        assertNotNull(script)
        assertTrue(script!!.contains("[data-test-id=\"login-page-title\"]"))
    }

    @Test
    fun `no footer produces no script`() {
        assertNull(LoginBoxFooter.script(null))
        assertNull(LoginBoxFooter.script(mapOf("rows" to emptyList<Any>())))
    }

    // endregion

    // region footer validation

    /**
     * A bad URL degrades the segment to plain text rather than dropping it: a legal
     * attribution missing a fragment reads as a bug, whereas an unlinked label still says
     * what it needs to say.
     */
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

    /**
     * The security guard must not be delegated. A caller supplying a predicate that says
     * yes to everything still cannot inject a script-executing scheme.
     */
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
    }

    @Test
    fun `relative and empty links are rejected`() {
        assertNull(LoginBoxFooter.sanitizedLinkUrl("/users/sign_up/select"))
        assertNull(LoginBoxFooter.sanitizedLinkUrl(""))
        assertNull(LoginBoxFooter.sanitizedLinkUrl(null))
    }

    /**
     * The hand-off case: a host app pointing a footer link at its own scheme so the box
     * dismisses and the app presents sign-up itself. Rejected by default, so the
     * predicate is what admits it — nothing is accepted merely for being custom.
     */
    @Test
    fun `an app-registered scheme is accepted`() {
        assertEquals(
            "healthie://sign-up",
            LoginBoxFooter.sanitizedLinkUrl("healthie://sign-up") { it == "healthie" }
        )
        assertNull(
            LoginBoxFooter.sanitizedLinkUrl("otherapp://sign-up") { it == "healthie" }
        )
    }

    @Test
    fun `an empty footer produces nothing`() {
        assertNull(LoginBoxFooter.sanitizedFooter(null))
        assertNull(LoginBoxFooter.sanitizedFooter(emptyMap()))
        assertNull(LoginBoxFooter.sanitizedFooter(mapOf("rows" to emptyList<Any>())))
        assertNull(
            LoginBoxFooter.sanitizedFooter(
                mapOf(
                    "rows" to listOf(
                        mapOf(
                            "variant" to "body",
                            "segments" to listOf(mapOf("label" to ""), mapOf("text" to ""))
                        )
                    )
                )
            )
        )
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

    // endregion

    // region external link allowlist

    /**
     * Only `http(s)` links leave for a browser. An app-scheme link is a hand-off that
     * dismisses the box, and must not be short-circuited into "open externally, keep the
     * box mounted".
     */
    @Test
    fun `external urls cover only http links`() {
        val urls = LoginBoxFooter.footerExternalUrls(
            mapOf(
                "rows" to listOf(
                    mapOf(
                        "variant" to "body",
                        "segments" to listOf(
                            mapOf("label" to "Privacy", "url" to "https://policies.google.com/privacy"),
                            mapOf("label" to "Terms", "url" to "http://example.com/terms"),
                            mapOf("label" to "Sign up", "url" to "healthie://sign-up"),
                            mapOf("text" to "no link here")
                        )
                    )
                )
            )
        ) { it == "healthie" }

        assertEquals(
            setOf("https://policies.google.com/privacy", "http://example.com/terms"),
            urls
        )
    }

    @Test
    fun `external urls are empty without a footer`() {
        assertTrue(LoginBoxFooter.footerExternalUrls(null).isEmpty())
        assertTrue(
            LoginBoxFooter.footerExternalUrls(mapOf("rows" to emptyList<Any>())).isEmpty()
        )
    }

    /**
     * A rejected URL must not linger in the allowlist, or the web client would hand a
     * browser a value the footer never rendered.
     */
    @Test
    fun `external urls exclude rejected links`() {
        assertTrue(
            LoginBoxFooter.footerExternalUrls(
                footerPayload(url = "javascript:alert(1)")
            ).isEmpty()
        )
    }

    // endregion
}
