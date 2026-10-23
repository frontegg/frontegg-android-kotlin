package com.frontegg.android

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FR-25934: the embedded auth flow used a static callback plus an un-persisted
 * `authCompleted` flag. After process death the activity is recreated from
 * savedInstanceState with `authCompleted` reset to false, which could fire a
 * spurious user-cancellation. These cover the two contained fixes: persisting the
 * completion flag, and the guard that decides whether onDestroy reports a cancel.
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedAuthActivityTest {

    // --- user-cancellation guard ---

    @Test
    fun `delivers user cancellation when finishing, not completed, not a config change`() {
        assertTrue(
            EmbeddedAuthActivity.shouldDeliverUserCancellation(
                authCompleted = false,
                isFinishing = true,
                isChangingConfigurations = false
            )
        )
    }

    @Test
    fun `does not deliver cancellation when auth already completed`() {
        assertFalse(
            EmbeddedAuthActivity.shouldDeliverUserCancellation(
                authCompleted = true,
                isFinishing = true,
                isChangingConfigurations = false
            )
        )
    }

    @Test
    fun `does not deliver cancellation during a configuration change`() {
        assertFalse(
            EmbeddedAuthActivity.shouldDeliverUserCancellation(
                authCompleted = false,
                isFinishing = true,
                isChangingConfigurations = true
            )
        )
    }

    @Test
    fun `does not deliver cancellation when not finishing`() {
        assertFalse(
            EmbeddedAuthActivity.shouldDeliverUserCancellation(
                authCompleted = false,
                isFinishing = false,
                isChangingConfigurations = false
            )
        )
    }

    // --- authCompleted persistence across process death ---

    @Test
    fun `authCompleted round-trips through the saved-state bundle`() {
        val bundle = Bundle()
        EmbeddedAuthActivity.writeAuthCompleted(bundle, true)

        assertTrue(EmbeddedAuthActivity.readAuthCompleted(bundle))
    }

    @Test
    fun `authCompleted defaults to false when absent from the bundle`() {
        assertFalse(EmbeddedAuthActivity.readAuthCompleted(Bundle()))
    }

    // --- direct login additional query params ---

    @Test
    fun `direct login payload keeps prompt consent without caller params`() {
        val payload = JSONObject(EmbeddedAuthActivity.directLoginActionJson("social-login", "google", null))

        assertEquals("social-login", payload.getString("type"))
        assertEquals("google", payload.getString("data"))
        assertEquals("consent", payload.getJSONObject("additionalQueryParams").getString("prompt"))
        assertEquals(1, payload.getJSONObject("additionalQueryParams").length())
    }

    @Test
    fun `direct login payload adds caller params and lets them replace prompt`() {
        val queryParams = EmbeddedAuthActivity.directLoginQueryParamsBundle(
            mapOf("prompt" to "login", "login_hint" to "user@example.com")
        )

        val payloadJson = EmbeddedAuthActivity.directLoginActionJson("direct", "https://idp.example.com", queryParams)
        val payload = JSONObject(payloadJson)

        val additionalQueryParams = payload.getJSONObject("additionalQueryParams")
        assertEquals("login", additionalQueryParams.getString("prompt"))
        assertEquals("user@example.com", additionalQueryParams.getString("login_hint"))
        assertEquals(2, additionalQueryParams.length())
    }
}
