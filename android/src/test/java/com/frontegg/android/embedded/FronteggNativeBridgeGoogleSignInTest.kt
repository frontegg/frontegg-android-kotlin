package com.frontegg.android.embedded

import android.app.Activity
import android.util.Log
import com.frontegg.android.FronteggApp
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.init.ConfigCache
import com.frontegg.android.models.SocialLoginProvider
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.StorageProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FronteggNativeBridgeGoogleSignInTest {
    private val baseUrl = "https://base.url.com"

    private val activity = mockk<Activity>(relaxed = true)
    private val webClient = mockk<FronteggWebClient>()
    private val auth = mockk<FronteggAuthService>()
    private val storage = mockk<FronteggInnerStorage>()
    private val googleCallback = slot<(Exception?) -> Unit>()
    private lateinit var bridge: FronteggNativeBridge

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        every { auth.baseUrl } returns baseUrl
        every { auth.loginWithGoogle(any(), capture(googleCallback)) } returns Unit
        coEvery { auth.loginWithSocialLoginProvider(any(), any(), any(), any()) } returns Unit

        val app = mockk<FronteggApp>()
        every { app.auth } returns auth
        mockkObject(FronteggApp.Companion)
        mockkObject(FronteggApp)
        every { FronteggApp.instance } returns app

        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() } returns storage

        mockkObject(ConfigCache)
        every { ConfigCache.loadLastRegionsInit(any()) } returns null

        every { webClient.getFormAction() } returns "login"
        every { webClient.currentUrlMainSafe() } returns "$baseUrl/oauth/account/login"

        bridge = FronteggNativeBridge(activity, webClient)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun verifyBrowserSocialLoginStarted(times: Int) {
        coVerify(exactly = times, timeout = if (times > 0) 1_000 else 0) {
            auth.loginWithSocialLoginProvider(activity, SocialLoginProvider.GOOGLE, any(), any())
        }
    }

    @Test
    fun `google uses native sign-in when enabled`() {
        every { storage.useNativeGoogleSignIn } returns true

        bridge.loginWithSocialLoginProvider("google")
        googleCallback.captured(null)

        verify { auth.loginWithGoogle(activity, any()) }
        verifyBrowserSocialLoginStarted(times = 0)
    }

    @Test
    fun `google uses the browser when native sign-in is disabled`() {
        every { storage.useNativeGoogleSignIn } returns false

        bridge.loginWithSocialLoginProvider("google")

        verify(exactly = 0) { auth.loginWithGoogle(any(), any()) }
        verifyBrowserSocialLoginStarted(times = 1)
    }

    @Test
    fun `canceled native sign-in stays on the login box`() {
        every { storage.useNativeGoogleSignIn } returns true

        bridge.loginWithSocialLoginProvider("google")
        googleCallback.captured(CanceledByUserException())

        verifyBrowserSocialLoginStarted(times = 0)
    }

    @Test
    fun `failed native sign-in falls back to the browser`() {
        every { storage.useNativeGoogleSignIn } returns true

        bridge.loginWithSocialLoginProvider("google")
        googleCallback.captured(IllegalStateException("no Android OAuth client"))

        verifyBrowserSocialLoginStarted(times = 1)
    }

    @Test
    fun `other providers ignore the native Google flag`() {
        every { storage.useNativeGoogleSignIn } returns true

        bridge.loginWithSocialLoginProvider("github")

        verify(exactly = 0) { auth.loginWithGoogle(any(), any()) }
        coVerify(timeout = 1_000) {
            auth.loginWithSocialLoginProvider(activity, SocialLoginProvider.GITHUB, any(), any())
        }
    }
}
