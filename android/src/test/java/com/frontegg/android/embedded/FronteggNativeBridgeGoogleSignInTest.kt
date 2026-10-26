package com.frontegg.android.embedded

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.view.ContextThemeWrapper
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.FronteggApp
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.exceptions.MfaRequiredException
import com.frontegg.android.init.ConfigCache
import com.frontegg.android.models.SocialLoginAction
import com.frontegg.android.models.SocialLoginProvider
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.StorageProvider
import com.frontegg.android.utils.ObservableValue
import com.frontegg.android.utils.TenantResolver
import com.frontegg.android.utils.TenantResolverResult
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
    private val isStepUpAuthorization = ObservableValue(false)
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
        every { auth.isStepUpAuthorization } returns isStepUpAuthorization
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

        every { storage.tenantResolver } returns null
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
    fun `google uses native sign-in when the WebView context wraps the activity`() {
        every { storage.useNativeGoogleSignIn } returns true
        val wrappedBridge = FronteggNativeBridge(ContextThemeWrapper(activity, 0), webClient)

        wrappedBridge.loginWithSocialLoginProvider("google")
        googleCallback.captured(null)

        verify { auth.loginWithGoogle(activity, any()) }
        verifyBrowserSocialLoginStarted(times = 0)
    }

    @Test
    fun `google can be tapped again after native sign-in ends`() {
        every { storage.useNativeGoogleSignIn } returns true

        bridge.loginWithSocialLoginProvider("google")
        googleCallback.captured(CanceledByUserException())
        bridge.loginWithSocialLoginProvider("google")

        verify(exactly = 2) { auth.loginWithGoogle(activity, any()) }
    }

    @Test
    fun `google uses the browser when native sign-in is disabled`() {
        every { storage.useNativeGoogleSignIn } returns false

        bridge.loginWithSocialLoginProvider("google")

        verify(exactly = 0) { auth.loginWithGoogle(any(), any()) }
        verifyBrowserSocialLoginStarted(times = 1)
    }

    @Test
    fun `google uses the browser during step-up`() {
        every { storage.useNativeGoogleSignIn } returns true
        isStepUpAuthorization.value = true

        bridge.loginWithSocialLoginProvider("google")

        verify(exactly = 0) { auth.loginWithGoogle(any(), any()) }
        verifyBrowserSocialLoginStarted(times = 1)
    }

    @Test
    fun `google uses the browser on the activation page`() {
        every { storage.useNativeGoogleSignIn } returns true
        every { webClient.currentUrlMainSafe() } returns "$baseUrl/oauth/account/activate?token=t"

        bridge.loginWithSocialLoginProvider("google")

        verify(exactly = 0) { auth.loginWithGoogle(any(), any()) }
        verifyBrowserSocialLoginStarted(times = 1)
    }

    @Test
    fun `google uses the browser on the invitation pages`() {
        every { storage.useNativeGoogleSignIn } returns true
        var currentUrl = "$baseUrl/oauth/account/invitation/accept?token=t"
        every { webClient.currentUrlMainSafe() } answers { currentUrl }

        bridge.loginWithSocialLoginProvider("google")
        currentUrl = "$baseUrl/oauth/account/accept-invitation?token=t"
        bridge.loginWithSocialLoginProvider("google")

        verify(exactly = 0) { auth.loginWithGoogle(any(), any()) }
        verifyBrowserSocialLoginStarted(times = 2)
    }

    @Test
    fun `google uses the browser in a login box opened for an organization`() {
        every { storage.useNativeGoogleSignIn } returns true
        val loginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        every { loginBox.intent } returns Intent()
            .putExtra(EmbeddedAuthActivity.AUTHORIZE_URI, "$baseUrl/oauth/authorize?organization=acme")

        FronteggNativeBridge(loginBox, webClient).loginWithSocialLoginProvider("google")

        verify(exactly = 0) { auth.loginWithGoogle(any(), any()) }
        coVerify(exactly = 1, timeout = 1_000) {
            auth.loginWithSocialLoginProvider(loginBox, SocialLoginProvider.GOOGLE, any(), any())
        }
    }

    @Test
    fun `native google sign-in keeps the login box page when it resumes`() {
        every { storage.useNativeGoogleSignIn } returns true
        val loginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        every { loginBox.intent } returns Intent()

        FronteggNativeBridge(loginBox, webClient).loginWithSocialLoginProvider("google")

        verify { loginBox.keepCurrentPageOnResume() }
        verify { auth.loginWithGoogle(loginBox, any()) }
    }

    @Test
    fun `google uses the browser when the app configures login per account`() {
        every { storage.useNativeGoogleSignIn } returns true
        every { storage.tenantResolver } returns TenantResolver { TenantResolverResult(tenant = "acme") }

        bridge.loginWithSocialLoginProvider("google")

        verify(exactly = 0) { auth.loginWithGoogle(any(), any()) }
        verifyBrowserSocialLoginStarted(times = 1)
    }

    @Test
    fun `google uses the browser on the sign-up page`() {
        every { storage.useNativeGoogleSignIn } returns true
        every { webClient.getFormAction() } returns SocialLoginAction.SIGNUP.value

        bridge.loginWithSocialLoginProvider("google")

        verify(exactly = 0) { auth.loginWithGoogle(any(), any()) }
        coVerify(timeout = 1_000) {
            auth.loginWithSocialLoginProvider(activity, SocialLoginProvider.GOOGLE, SocialLoginAction.SIGNUP, any())
        }
    }

    @Test
    fun `canceled native sign-in stays on the login box`() {
        every { storage.useNativeGoogleSignIn } returns true

        bridge.loginWithSocialLoginProvider("google")
        googleCallback.captured(CanceledByUserException())

        verifyBrowserSocialLoginStarted(times = 0)
    }

    @Test
    fun `native sign-in that continues with MFA stays on the login box and can be tapped again`() {
        every { storage.useNativeGoogleSignIn } returns true

        bridge.loginWithSocialLoginProvider("google")
        googleCallback.captured(MfaRequiredException("{}"))
        bridge.loginWithSocialLoginProvider("google")

        verifyBrowserSocialLoginStarted(times = 0)
        verify(exactly = 2) { auth.loginWithGoogle(activity, any()) }
    }

    @Test
    fun `failed native sign-in falls back to the browser`() {
        every { storage.useNativeGoogleSignIn } returns true

        bridge.loginWithSocialLoginProvider("google")
        googleCallback.captured(IllegalStateException("no Android OAuth client"))

        verifyBrowserSocialLoginStarted(times = 1)
    }

    @Test
    fun `failed native sign-in after the login box closed does not open the browser`() {
        every { storage.useNativeGoogleSignIn } returns true
        every { activity.isFinishing } returns true

        bridge.loginWithSocialLoginProvider("google")
        googleCallback.captured(IllegalStateException("offline"))

        verifyBrowserSocialLoginStarted(times = 0)
    }

    @Test
    fun `failed native sign-in after the login box was recreated falls back to the browser in the new one`() {
        every { storage.useNativeGoogleSignIn } returns true
        val recreatedLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        val startingLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        every { startingLoginBox.intent } returns Intent()
        every { startingLoginBox.isDestroyed } returns true
        mockkObject(EmbeddedAuthActivity.Companion)
        every { EmbeddedAuthActivity.liveLoginBox(startingLoginBox, any()) } returns recreatedLoginBox

        FronteggNativeBridge(startingLoginBox, webClient).loginWithSocialLoginProvider("google")
        googleCallback.captured(IllegalStateException("offline"))

        coVerify(exactly = 1, timeout = 1_000) {
            auth.loginWithSocialLoginProvider(recreatedLoginBox, SocialLoginProvider.GOOGLE, any(), any())
        }
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
