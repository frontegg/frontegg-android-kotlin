package com.frontegg.android

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * FR-26330: the unlock-account link was resolved by the OS to a browser rather than the app,
 * because EmbeddedAuthActivity declared no intent filter for /oauth/account/unlock.
 *
 * These resolve real VIEW intents through the package manager against the manifest, so they
 * exercise the declaration itself rather than a copy of the path list.
 */
@RunWith(RobolectricTestRunner::class)
class UnlockDeepLinkResolutionTest {
    private val host = "app-x4gr8g28fxr5.frontegg.com"

    private fun resolvesToEmbeddedAuth(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val packageManager = RuntimeEnvironment.getApplication().packageManager
        return packageManager.queryIntentActivities(intent, 0).any {
            it.activityInfo?.name == EmbeddedAuthActivity::class.java.name
        }
    }

    @Test
    fun `unlock deep link resolves to the embedded auth activity`() {
        assertTrue(resolvesToEmbeddedAuth("https://$host/oauth/account/unlock?token=abc&userId=123"))
    }

    @Test
    fun `previously working deep links still resolve`() {
        listOf(
            "/oauth/account/activate",
            "/oauth/account/invitation/accept",
            "/oauth/account/reset-password",
            "/oauth/account/login/magic-link",
        ).forEach { path ->
            assertTrue(path, resolvesToEmbeddedAuth("https://$host$path?token=abc"))
        }
    }

    @Test
    fun `ordinary login page is not claimed by the embedded auth activity`() {
        assertFalse(resolvesToEmbeddedAuth("https://$host/oauth/account/login"))
    }

    @Test
    fun `unlock on a foreign host is not claimed`() {
        assertFalse(resolvesToEmbeddedAuth("https://evil.example.com/oauth/account/unlock?token=abc"))
    }
}
