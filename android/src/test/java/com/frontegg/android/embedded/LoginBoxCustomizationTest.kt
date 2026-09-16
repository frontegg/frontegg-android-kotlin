package com.frontegg.android.embedded

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `assigns overrides to the global the login box reads`() {
        val script = LoginBoxCustomization.script(
            mapOf("loginBox" to mapOf("palette" to mapOf("primary" to mapOf("main" to "#3F6655")))),
            null
        )

        assertNotNull(script)
        assertTrue(script!!.startsWith("window.__fronteggLoginBoxOverrides = "))
        assertTrue(script.endsWith(";"))
        assertFalse(script.contains("window.fetch"))
    }

    @Test
    fun `theme options are emitted under themeV2`() {
        val payload = payloadOf(
            mapOf("loginBox" to mapOf("palette" to mapOf("primary" to mapOf("main" to "#3F6655")))),
            null
        )

        val main = payload.getJSONObject("themeV2")
            .getJSONObject("loginBox")
            .getJSONObject("palette")
            .getJSONObject("primary")
            .getString("main")

        assertEquals("#3F6655", main)
        assertFalse(payload.has("localizations"))
    }

    @Test
    fun `localizations are emitted under localizations`() {
        val payload = payloadOf(
            null,
            mapOf("en" to mapOf("loginBox" to mapOf("login" to mapOf("title" to "Sign-in"))))
        )

        val title = payload.getJSONObject("localizations")
            .getJSONObject("en")
            .getJSONObject("loginBox")
            .getJSONObject("login")
            .getString("title")

        assertEquals("Sign-in", title)
        assertFalse(payload.has("themeV2"))
    }

    @Test
    fun `both overrides are emitted together`() {
        val payload = payloadOf(
            mapOf("loginBox" to mapOf("logo" to mapOf("image" to "https://example.com/logo.png"))),
            mapOf("en" to mapOf("loginBox" to mapOf("login" to mapOf("continue" to "Log In"))))
        )

        assertTrue(payload.has("themeV2"))
        assertTrue(payload.has("localizations"))
    }

    @Test
    fun `nested lists survive encoding`() {
        val payload = payloadOf(
            mapOf("loginBox" to mapOf("phoneNumberCountryCodes" to mapOf("allowedCountries" to listOf("us", "gb")))),
            null
        )

        val allowed = payload.getJSONObject("themeV2")
            .getJSONObject("loginBox")
            .getJSONObject("phoneNumberCountryCodes")
            .getJSONArray("allowedCountries")

        assertEquals(2, allowed.length())
        assertEquals("us", allowed.getString(0))
        assertEquals("gb", allowed.getString(1))
    }

    @Test
    fun `quotes in copy do not break the payload`() {
        val payload = payloadOf(
            null,
            mapOf("en" to mapOf("loginBox" to mapOf("login" to mapOf("title" to "Don't \"stop\" now"))))
        )

        val title = payload.getJSONObject("localizations")
            .getJSONObject("en")
            .getJSONObject("loginBox")
            .getJSONObject("login")
            .getString("title")

        assertEquals("Don't \"stop\" now", title)
    }

    @Test
    fun `javascript line terminators are escaped`() {
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

    // endregion

    // region unencodable values

    @Test
    fun `values that cannot be represented in JSON drop the whole payload`() {
        val script = LoginBoxCustomization.script(
            mapOf("loginBox" to mapOf("logo" to mapOf("image" to Any()))),
            null
        )

        assertNull(script)
    }

    @Test
    fun `invalid key path is null for encodable values`() {
        val value = mapOf(
            "loginBox" to mapOf(
                "themeName" to "modern",
                "enabled" to true,
                "order" to 3,
                "ratio" to 1.5,
                "tags" to listOf("a", "b"),
                "absent" to null
            )
        )

        assertNull(LoginBoxCustomization.invalidKeyPath(value))
    }

    @Test
    fun `invalid key path names the offending key`() {
        val value = mapOf("loginBox" to mapOf("palette" to mapOf("primary" to mapOf("main" to Any()))))

        assertEquals("loginBox.palette.primary.main", LoginBoxCustomization.invalidKeyPath(value))
    }

    @Test
    fun `invalid key path names the offending list element`() {
        val value = mapOf("loginBox" to mapOf("tags" to listOf("ok", Any())))

        assertEquals("loginBox.tags[1]", LoginBoxCustomization.invalidKeyPath(value))
    }

    @Test
    fun `non finite numbers are rejected`() {
        val value = mapOf("loginBox" to mapOf("ratio" to Double.NaN))

        assertEquals("loginBox.ratio", LoginBoxCustomization.invalidKeyPath(value))
    }

    // endregion

    // region origin

    @Test
    fun `auth origin drops path and query`() {
        assertEquals(
            "https://app.example.com",
            LoginBoxCustomization.authOrigin("https://app.example.com/auth?x=1")
        )
    }

    @Test
    fun `auth origin preserves a non default port`() {
        assertEquals(
            "https://app.example.com:8443",
            LoginBoxCustomization.authOrigin("https://app.example.com:8443")
        )
    }

    @Test
    fun `auth origin is null for unusable input`() {
        assertNull(LoginBoxCustomization.authOrigin(""))
        assertNull(LoginBoxCustomization.authOrigin("not a url"))
    }

    // endregion

    private fun payloadOf(
        themeOptions: Map<String, Any?>?,
        localizations: Map<String, Any?>?
    ): JSONObject {
        val script = LoginBoxCustomization.script(themeOptions, localizations)
        assertNotNull(script)

        val prefix = "window.${LoginBoxCustomization.GLOBAL_NAME} = "
        val json = script!!.removePrefix(prefix).removeSuffix(";")
        return JSONObject(json)
    }
}
