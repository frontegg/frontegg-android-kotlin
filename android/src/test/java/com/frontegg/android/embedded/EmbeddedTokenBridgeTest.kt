package com.frontegg.android.embedded

import com.frontegg.android.services.FronteggInnerStorage
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the embedded login WebView's native token bridge (getTokens) —
 * the fix that lets step-up / re-auth reuse the native session instead of the
 * cookie token-refresh that 401s in the WebView. Covers the security-critical
 * origin gate, the callback JS the redux-store consumes, and the advertised
 * capability flag.
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedTokenBridgeTest {

    // --- Trusted-origin gate: only the configured Frontegg origin may pull tokens ---

    @Test
    fun `isTrustedOrigin true when scheme host and port match`() {
        assertTrue(
            FronteggNativeBridge.isTrustedOrigin(
                "https://acme.frontegg.com/oauth/authorize?response_type=code",
                "https://acme.frontegg.com"
            )
        )
    }

    @Test
    fun `isTrustedOrigin false for a different host`() {
        assertFalse(
            FronteggNativeBridge.isTrustedOrigin(
                "https://evil.example.com/oauth/authorize",
                "https://acme.frontegg.com"
            )
        )
    }

    @Test
    fun `isTrustedOrigin false for a different scheme`() {
        assertFalse(
            FronteggNativeBridge.isTrustedOrigin(
                "http://acme.frontegg.com/oauth/authorize",
                "https://acme.frontegg.com"
            )
        )
    }

    @Test
    fun `isTrustedOrigin false for null or malformed current url`() {
        assertFalse(FronteggNativeBridge.isTrustedOrigin(null, "https://acme.frontegg.com"))
        assertFalse(FronteggNativeBridge.isTrustedOrigin("random text", "https://acme.frontegg.com"))
    }

    // --- Callback JS consumed by the redux-store FronteggNativeBridgeCallbacks registry ---

    @Test
    fun `resolveCallbackJs delivers the tokens to the callback and cleans up`() {
        val tokens = """{"accessToken":"AT","refreshToken":"RT"}"""
        val js = FronteggNativeBridge.resolveCallbackJs("cb-1", tokens)
        assertTrue(js.contains("window.FronteggNativeBridgeCallbacks"))
        assertTrue(js.contains("r['cb-1'].resolve($tokens)"))
        assertTrue(js.contains("delete r['cb-1']"))
    }

    @Test
    fun `rejectCallbackJs escapes single quotes in the reason`() {
        val js = FronteggNativeBridge.rejectCallbackJs("cb-2", "it's bad")
        assertTrue(js.contains("""r['cb-2'].reject('it\'s bad')"""))
    }

    // --- Capability advertised so the login box takes the native-token path ---

    @Test
    fun `embedded webview advertises getTokens capability`() {
        val storage = mockk<FronteggInnerStorage>(relaxed = true)
        val fns = FronteggWebClient.buildNativeBridgeFunctions(storage)
        assertTrue(
            "getTokens must be advertised so the login box bootstraps from native tokens",
            fns.getBoolean("getTokens")
        )
    }
}
