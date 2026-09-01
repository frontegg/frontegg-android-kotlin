package com.frontegg.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EmbeddedAuthActivityOriginTest {

    private fun configure(baseUrl: String, regions: List<RegionConfig> = emptyList()) {
        FronteggInnerStorage().fill(
            baseUrl = baseUrl,
            clientId = "test-client-id",
            applicationId = null,
            isEmbeddedMode = true,
            regions = regions,
            packageName = "com.example.app",
        )
        mockkStatic("com.frontegg.android.FronteggAppKt")
        every { any<Context>().fronteggAuth } returns mockk<FronteggAuthService>(relaxed = true)
    }

    private fun launchWith(url: String): EmbeddedAuthActivity {
        val intent = Intent(RuntimeEnvironment.getApplication(), EmbeddedAuthActivity::class.java)
            .apply { data = Uri.parse(url) }
        return Robolectric.buildActivity(EmbeddedAuthActivity::class.java, intent).create().get()
    }

    @Before
    fun setUp() = configure("https://base.url.com")

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `attacker url carrying a legitimate route is refused`() {
        val activity = launchWith("https://attacker.example/oauth/account/reset-password")
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `attacker url with the route in the query string is refused`() {
        val activity = launchWith("https://attacker.example/?next=/oauth/account/reset-password")
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `look-alike host is refused`() {
        val activity = launchWith("https://base.url.com.attacker.io/oauth/account/reset-password")
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `configured host is accepted`() {
        val activity = launchWith("https://base.url.com/oauth/account/reset-password?token=abc")
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `region host is accepted when no base url is set yet`() {
        configure(
            baseUrl = "",
            regions = listOf(RegionConfig("eu", "https://eu.frontegg.com", "client-id"))
        )
        val activity = launchWith("https://eu.frontegg.com/oauth/account/reset-password?token=abc")
        assertFalse(activity.isFinishing)
    }
}
