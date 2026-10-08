package com.frontegg.android.embedded

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import com.frontegg.android.services.FronteggInnerStorage
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FronteggWebClientFooterLinkTest {

    private lateinit var activity: Activity
    private lateinit var client: FronteggWebClient

    private val footer: Map<String, Any?> = mapOf(
        "rows" to listOf(
            mapOf(
                "variant" to "body",
                "segments" to listOf(
                    mapOf("label" to "Notice", "url" to "https://example.com/legal/notice"),
                    mapOf("label" to "Sign up", "url" to "healthie://sign-up")
                )
            )
        )
    )

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        client = FronteggWebClient(activity, mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        FronteggInnerStorage().loginBoxFooter = null
    }

    private fun navigate(url: String): Boolean {
        val request = mockk<WebResourceRequest>(relaxed = true)
        every { request.url } returns Uri.parse(url)
        return client.shouldOverrideUrlLoading(null, request)
    }

    @Test
    fun `footer http link opens in a browser and keeps the box`() {
        client.loginBoxFooterLinks = LoginBoxFooter.footerLinks(footer) { it == "healthie" }

        assertTrue(navigate("https://EXAMPLE.com:443/legal/notice"))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://EXAMPLE.com:443/legal/notice", started.dataString)
        assertNull(started.`package`)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `footer app-scheme link is handed to the host app and dismisses the box`() {
        client.loginBoxFooterLinks = LoginBoxFooter.footerLinks(footer) { it == "healthie" }

        assertTrue(navigate("healthie://sign-up/"))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("healthie://sign-up/", started.dataString)
        assertEquals(activity.packageName, started.`package`)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `links follow the injected snapshot, not the current storage value`() {
        val storage = FronteggInnerStorage()
        storage.loginBoxFooter = footer
        client.loginBoxFooterLinks = LoginBoxFooter.footerLinks(storage.loginBoxFooter) { it == "healthie" }
        storage.loginBoxFooter = null

        assertTrue(navigate("https://example.com/legal/notice"))
        assertEquals("https://example.com/legal/notice", shadowOf(activity).nextStartedActivity.dataString)
    }

    @Test
    fun `a footer set after the box was built is not intercepted`() {
        FronteggInnerStorage().loginBoxFooter = footer

        assertFalse(navigate("https://example.com/legal/notice"))
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
