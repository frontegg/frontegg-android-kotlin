package com.frontegg.android.embedded

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the script builder that applies host-supplied theme/copy overrides to the
 * embedded login box.
 */
class LoginBoxCustomizationTest {

    // region no-op cases

    @Test
    fun `returns null when nothing is provided`() {
        assertNull(LoginBoxCustomization.script(null, null))
    }

    @Test
    fun `returns null when overrides are empty`() {
        assertNull(LoginBoxCustomization.script(emptyMap(), emptyMap()))
    }

    // endregion

    // region payload

    @Test
    fun `theme options are emitted under themeV2`() {
        val script = LoginBoxCustomization.script(
            mapOf("loginBox" to mapOf("palette" to mapOf("primary" to mapOf("main" to "#3F6655")))),
            null
        )

        assertNotNull(script)
        assertTrue(script!!.contains("\"themeV2\""))
        assertTrue(script.contains("#3F6655"))
        assertFalse(script.contains("\"localizations\""))
    }

    @Test
    fun `localizations are emitted under localizations`() {
        val script = LoginBoxCustomization.script(
            null,
            mapOf("en" to mapOf("loginBox" to mapOf("login" to mapOf("title" to "Sign-in"))))
        )

        assertNotNull(script)
        assertTrue(script!!.contains("\"localizations\""))
        assertTrue(script.contains("Sign-in"))
        assertFalse(script.contains("\"themeV2\""))
    }

    @Test
    fun `both overrides are emitted together`() {
        val script = LoginBoxCustomization.script(
            mapOf("loginBox" to mapOf("logo" to mapOf("image" to "https://example.com/logo.png"))),
            mapOf("en" to mapOf("loginBox" to mapOf("login" to mapOf("continue" to "Log In"))))
        )

        assertNotNull(script)
        assertTrue(script!!.contains("\"themeV2\""))
        assertTrue(script.contains("\"localizations\""))
        assertTrue(script.contains("example.com"))
        assertTrue(script.contains("Log In"))
    }

    // endregion

    // region script shape

    @Test
    fun `script targets the login box metadata request`() {
        val script = LoginBoxCustomization.script(
            mapOf("loginBox" to mapOf("themeName" to "modern")),
            null
        )

        assertNotNull(script)
        assertTrue(script!!.contains(LoginBoxCustomization.METADATA_PATH))
        assertTrue(script.contains("window.fetch"))
        // Guards against double-installing when the script is injected twice.
        assertTrue(script.contains("__fronteggLoginBoxOverridesInstalled"))
    }

    // endregion

    // region encoding

    @Test
    fun `javascript line terminators are escaped`() {
        // U+2028/U+2029 are valid inside JSON but terminate a line in JavaScript source,
        // which would break the emitted script.
        val script = LoginBoxCustomization.script(
            null,
            mapOf("en" to mapOf("note" to "a\u2028b\u2029c"))
        )

        assertNotNull(script)
        assertFalse(script!!.contains("\u2028"))
        assertFalse(script.contains("\u2029"))
        assertTrue(script.contains("\\u2028"))
        assertTrue(script.contains("\\u2029"))
    }

    @Test
    fun `emitted overrides parse back as json`() {
        val script = LoginBoxCustomization.script(
            mapOf("loginBox" to mapOf("palette" to mapOf("primary" to mapOf("main" to "#16284A")))),
            null
        )

        val json = script!!
            .substringAfter("var overrides = ")
            .substringBefore(";\n")

        val parsed = JSONObject(json)
        val main = parsed.getJSONObject("themeV2")
            .getJSONObject("loginBox")
            .getJSONObject("palette")
            .getJSONObject("primary")
            .getString("main")

        assertEquals("#16284A", main)
    }

    @Test
    fun `quotes in copy do not break the script`() {
        val script = LoginBoxCustomization.script(
            null,
            mapOf("en" to mapOf("loginBox" to mapOf("login" to mapOf("title" to "Don't \"stop\" now"))))
        )

        assertNotNull(script)
        // JSONObject escapes the double quotes; the apostrophe is safe because the payload
        // is embedded as an object literal, not a string.
        assertTrue(script!!.contains("Don't \\\"stop\\\" now"))
    }

    // endregion
}
