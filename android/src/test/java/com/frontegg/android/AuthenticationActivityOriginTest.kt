package com.frontegg.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
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
class AuthenticationActivityOriginTest {

    private lateinit var authService: FronteggAuthService

    private fun configure(baseUrl: String, regions: List<RegionConfig> = emptyList()) {
        FronteggInnerStorage().fill(
            baseUrl = baseUrl,
            clientId = "test-client-id",
            applicationId = null,
            isEmbeddedMode = false,
            regions = regions,
            packageName = "com.example.app",
        )
        authService = mockk<FronteggAuthService>(relaxed = true)
        every { authService.credentialManager } returns mockk<CredentialManager>(relaxed = true)
        mockkStatic("com.frontegg.android.FronteggAppKt")
        every { any<Context>().fronteggAuth } returns authService
    }

    private fun launchWith(url: String): AuthenticationActivity {
        val intent = Intent(RuntimeEnvironment.getApplication(), AuthenticationActivity::class.java)
            .apply { data = Uri.parse(url) }
        return Robolectric.buildActivity(AuthenticationActivity::class.java, intent)
            .create()
            .resume()
            .get()
    }

    @Before
    fun setUp() = configure("https://base.url.com")

    @After
    fun tearDown() {
        AuthenticationActivity.onAuthFinishedCallback = null
        unmockkAll()
    }

    @Test
    fun `attacker url is refused`() {
        assertTrue(launchWith("https://attacker.example/oauth/authorize?client_id=x").isFinishing)
    }

    @Test
    fun `look-alike host is refused`() {
        assertTrue(launchWith("https://base.url.com.attacker.io/oauth/authorize").isFinishing)
    }

    @Test
    fun `attacker callback url is never handed to the callback handler`() {
        launchWith("https://attacker.example/oauth/account/redirect/android/com.example.app?code=abc")

        verify(exactly = 0) { authService.handleSocialLoginCallback(any()) }
        verify(exactly = 0) { authService.handleHostedLoginCallback(any(), any(), any(), any()) }
    }

    @Test
    fun `configured callback url is handed to the callback handler`() {
        launchWith("https://base.url.com/oauth/account/redirect/android/com.example.app?code=abc")

        verify(exactly = 1) { authService.handleSocialLoginCallback(any()) }
    }

    @Test
    fun `configured host is accepted`() {
        assertFalse(launchWith("https://base.url.com/oauth/authorize?client_id=x").isFinishing)
    }

    @Test
    fun `custom scheme callback on the configured host is accepted`() {
        assertFalse(
            launchWith("com.example.app://base.url.com/android/oauth/callback?state=x").isFinishing
        )
    }

    @Test
    fun `region host is accepted when no base url is set yet`() {
        configure(
            baseUrl = "",
            regions = listOf(RegionConfig("eu", "https://eu.frontegg.com", "client-id"))
        )
        assertFalse(launchWith("https://eu.frontegg.com/oauth/authorize?client_id=x").isFinishing)
    }
}
