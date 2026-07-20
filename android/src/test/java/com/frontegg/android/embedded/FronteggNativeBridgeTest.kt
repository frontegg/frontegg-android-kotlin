package com.frontegg.android.embedded

import android.content.Context
import android.util.Log
import com.frontegg.android.FronteggApp
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.FronteggState
import com.frontegg.android.services.StorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FR-25935: every @JavascriptInterface method on the bridge must be gated by the same
 * same-origin check as getTokens. If the WebView is navigated/redirected to non-Frontegg
 * content, that content must not be able to drive native SSO/social-login intents or flip
 * the loader.
 */
@RunWith(RobolectricTestRunner::class)
class FronteggNativeBridgeTest {
    private val baseUrl = "https://base.url.com"
    private val trustedUrl = "https://base.url.com/oauth/account/login"
    private val untrustedUrl = "https://evil.example.com/phish"

    private val context = mockk<Context>()
    private val webClient = mockk<FronteggWebClient>()
    private val auth = mockk<FronteggAuthService>()
    private val credentialManager = mockk<CredentialManager>()
    private val storage = mockk<FronteggInnerStorage>()
    private lateinit var bridge: FronteggNativeBridge

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        every { context.startActivity(any()) } returns Unit

        every { auth.baseUrl } returns baseUrl
        every { auth.credentialManager } returns credentialManager
        every { credentialManager.saveCodeVerifier(any()) } returns true

        val app = mockk<FronteggApp>()
        every { app.auth } returns auth
        mockkObject(FronteggApp.Companion)
        mockkObject(FronteggApp)
        every { FronteggApp.instance } returns app

        // AuthorizeUrlGenerator (built for real inside the login methods) only assembles a
        // URL string — no network. Give it the storage it reads.
        every { storage.clientId } returns "TestClientId"
        every { storage.applicationId } returns null
        every { storage.baseUrl } returns baseUrl
        every { storage.packageName } returns "test.com"
        every { storage.useAssetsLinks } returns false
        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() } returns storage

        every { webClient.getFormAction() } returns "login"

        bridge = FronteggNativeBridge(context, webClient)

        FronteggState.webLoading.value = false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun onTrustedOrigin() {
        every { webClient.currentUrlMainSafe() } returns trustedUrl
    }

    private fun onUntrustedOrigin() {
        every { webClient.currentUrlMainSafe() } returns untrustedUrl
    }

    @Test
    fun `setLoading is refused from an untrusted origin`() {
        onUntrustedOrigin()
        FronteggState.webLoading.value = false

        bridge.setLoading(true)

        assert(!FronteggState.webLoading.value) { "setLoading must be ignored from an untrusted origin" }
    }

    @Test
    fun `setLoading is applied from a trusted origin`() {
        onTrustedOrigin()
        FronteggState.webLoading.value = false

        bridge.setLoading(true)

        assert(FronteggState.webLoading.value) { "setLoading must work from a trusted origin" }
    }

    @Test
    fun `loginWithSSO does not start an activity from an untrusted origin`() {
        onUntrustedOrigin()

        bridge.loginWithSSO("user@example.com")

        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `loginWithSSO starts an activity from a trusted origin`() {
        onTrustedOrigin()

        bridge.loginWithSSO("user@example.com")

        verify { context.startActivity(any()) }
    }

    @Test
    fun `loginWithSocialLogin does not start an activity from an untrusted origin`() {
        onUntrustedOrigin()

        bridge.loginWithSocialLogin("https://social.example.com/authorize")

        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `loginWithCustomSocialLoginProvider does not start an activity from an untrusted origin`() {
        onUntrustedOrigin()

        bridge.loginWithCustomSocialLoginProvider("custom-provider")

        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `loginWithSocialLoginProvider does not read form action from an untrusted origin`() {
        onUntrustedOrigin()

        bridge.loginWithSocialLoginProvider("google")

        // The guard sits above getFormAction(); on an untrusted origin the method returns
        // before consulting the web client for the form action.
        verify(exactly = 0) { webClient.getFormAction() }
    }
}
