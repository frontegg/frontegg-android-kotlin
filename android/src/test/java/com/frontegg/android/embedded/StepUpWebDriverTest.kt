package com.frontegg.android.embedded

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StepUpWebDriverTest {

    private val authorizeUrl =
        "https://base.url.com/oauth/authorize?response_type=code&acr_values=http" +
            "%3A%2F%2Fschemas.openid.net%2Fpape%2Fpolicies%2F2007%2F06%2Fmulti-factor&max_age=60"

    @Test
    fun `script seeds the step-up flag and the box's after-auth redirect`() {
        val script = StepUpWebDriver.script(authorizeUrl)

        assertTrue(script.contains("acr_values"))
        assertTrue(script.contains("SHOULD_STEP_UP"))
        assertTrue(script.contains("FRONTEGG_AFTER_AUTH_REDIRECT_URL"))
    }

    @Test
    fun `script saves the params the hosted postlogin needs in local and session storage`() {
        val script = StepUpWebDriver.script(authorizeUrl)

        listOf(
            "FRONTEGG_OAUTH_REDIRECT_AFTER_LOGIN",
            "FRONTEGG_OAUTH_STATE_AFTER_LOGIN",
            "FRONTEGG_OAUTH_STEP_UP_ACR_VALUES",
            "FRONTEGG_OAUTH_STEP_UP_MAX_AGE",
            "window.localStorage",
            "window.sessionStorage",
            "decodeURIComponent",
        ).forEach { expected -> assertTrue("expected $expected in script", script.contains(expected)) }
    }

    @Test
    fun `script routes to the hosted root instead of the box's step-up route`() {
        val script = StepUpWebDriver.script(authorizeUrl)

        assertTrue(script.contains("lastIndexOf('/') + 1"))
        assertTrue(script.contains("replaceState"))
        assertFalse(script.contains("+ '/account/step-up'"))
    }

    @Test
    fun `script embeds the authorize URL as a safely-escaped JS string literal`() {
        // A URL carrying characters that would break a naive string literal.
        val hostile = "https://x.com/oauth/authorize?a=\"b\"\\c</script>"
        val script = StepUpWebDriver.script(hostile)

        // The URL must appear only in its JSON-quoted (escaped) form, never raw —
        // otherwise a crafted authorize URL could break out of the literal.
        val quoted = JSONObject.quote(hostile)
        assertTrue("expected escaped literal $quoted in script", script.contains(quoted))
        assertFalse("raw unescaped URL must not leak into the script", script.contains("=\"b\"\\c"))
    }
}
