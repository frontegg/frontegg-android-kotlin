package com.frontegg.demo.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.math.abs

/**
 * Full embedded E2E suite mirroring Swift `DemoEmbeddedE2ETests`.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddedE2ETests : EmbeddedE2ETestCase() {

    // Bumped from 21s → 60s. The 21s window was too tight for slow CI emulators where
    // a single GC pause or slow WebView render during offline relaunch (which itself can
    // take 30+ seconds before OfflineModeBadge appears) was enough to push the access
    // token past expiry mid-test, breaking timing assumptions in offline mode tests.
    private val expiringAccessTokenTTL = 60
    private val longLivedRefreshTokenTTL = 300
    private val scheduledRefreshAccessTokenTTL = 45
    private val mePath = "/identity/resources/users/v2/me"
    private val deviceIntegrityHeader = "frontegg-device-integrity-token"

    // Distinct numbers: the SDK keeps its attestor, real or fake, while the project number is unchanged.
    private val unavailablePlayIntegrityProjectNumber = 123_456_789_012L
    private val fakePlayIntegrityProjectNumber = 987_654_321_098L

    @Test
    fun testPasswordLoginAndSessionRestore() {
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        terminateApp()
        launchApp(resetState = false)
        dismissBrowserForegroundIfNeeded()
        Thread.sleep(3_000)
        instrumentation.waitForIdleSync()
        waitForUserEmail("test@frontegg.com", timeoutMs = 300_000)
    }

    @Test
    fun testEmbeddedSamlLogin() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapEmbeddedMockOktaAfterButton("E2EEmbeddedSAMLButton")
        waitForUserEmail("test@saml-domain.com")
    }

    @Test
    fun testEmbeddedOidcLogin() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapEmbeddedMockOktaAfterButton("E2EEmbeddedOIDCButton")
        waitForUserEmail("test@oidc-domain.com")
    }

    @Test
    fun testEmbeddedStepUpMfaChallenge() {
        // Reach the authenticated Home screen via the embedded password flow.
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        val tokenExchangesBeforeStepUp = mock.requestCount("POST", "/oauth/token")

        tapDesc("Sensitive action")

        device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 30_000)
        waitForStepUpChallenge()

        // Completing the challenge posts to the hosted postlogin, whose code the OAuth callback exchanges.
        clickWebElement("e2e-stepup-complete")

        val exchangeDeadline = System.currentTimeMillis() + 60_000
        while (mock.requestCount("POST", "/oauth/token") <= tokenExchangesBeforeStepUp &&
            System.currentTimeMillis() < exchangeDeadline
        ) {
            Thread.sleep(500)
        }
        if (mock.requestCount("POST", "/oauth/token") <= tokenExchangesBeforeStepUp) {
            throw AssertionError("Expected the elevated step-up code to be exchanged for a token")
        }
        waitForDesc("UserPageRoot", 120_000)
    }

    @Test
    fun testRequestAuthorizeFlow() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ESeedRequestAuthorizeTokenButton")
        Thread.sleep(2_000)
        tapDesc("RequestAuthorizeButton")
        waitForUserEmail("signup@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testCustomSSOBrowserHandoff() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ECustomSSOButton")
        Thread.sleep(20_000)
        waitForUserEmail("custom-sso@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testDirectSocialBrowserHandoff() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EDirectSocialLoginButton")
        Thread.sleep(20_000)
        waitForUserEmail("social-login@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testColdLaunchTransientProbeTimeoutsDoNotBlinkNoConnectionPage() {
        mock.queueProbeTimeouts(count = 2, delayMs = 1500)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        Thread.sleep(2100)
        val noConn = device.findObject(By.desc("NoConnectionPageRoot"))
        if (noConn != null && noConn.visibleBounds.height() > 0) {
            throw AssertionError("Unexpected NoConnection overlay")
        }
    }

    @Test
    fun testLogoutTerminateTransientProbeFailureDoesNotBlinkNoConnectionPage() {
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        tapDesc("LogoutButton", 30_000)
        waitForDesc("LoginPageRoot", 60_000)
        mock.queueProbeFailures(listOf(503, 503))
        terminateApp()
        launchApp(resetState = false)
        waitForDesc("LoginPageRoot", 120_000)
        Thread.sleep(3500)
    }

    @Test
    fun testAuthenticatedOfflineModeWhenNetworkPathUnavailable() {
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        val initialVersion = accessTokenVersion()
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = true)
        waitForDesc("UserPageRoot", 90_000)
        waitForDesc("AuthenticatedOfflineModeEnabled", 10_000)
        waitForDesc("OfflineModeBadge", 10_000)
        assert(accessTokenVersion() == initialVersion)
        if (device.findObject(By.desc("RetryConnectionButton")) != null) {
            throw AssertionError("Did not expect Retry button")
        }
    }

    @Test
    fun testExpiredAccessTokenRefreshesOnAuthenticatedRelaunch() {
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = expiringAccessTokenTTL,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        val v0 = accessTokenVersion()
        val rc0 = oauthRefreshRequestCount()
        terminateApp()
        waitDurationSeconds((expiringAccessTokenTTL + 10).toLong())
        launchApp(resetState = false)
        dismissBrowserForegroundIfNeeded()
        waitForUserEmail("test@frontegg.com", timeoutMs = 180_000)
        waitForAccessTokenVersionChange(v0, timeoutMs = 200_000)
        assert(oauthRefreshRequestCount() > rc0)
    }

    @Test
    fun testAuthenticatedOfflineModeRecoversToOnlineAndRefreshesToken() {
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = expiringAccessTokenTTL,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        val v0 = accessTokenVersion()
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = true)
        waitForDesc("UserPageRoot", 90_000)
        waitForDesc("OfflineModeBadge", 120_000)
        // Let the access token expire during the offline phase
        waitDurationSeconds((expiringAccessTokenTTL + 5).toLong())
        // Simulate recovery: next launch without forced offline
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = false)
        dismissBrowserForegroundIfNeeded()
        waitForUserEmail("test@frontegg.com", timeoutMs = 150_000)
        Thread.sleep(10_000)
        waitForAccessTokenVersionChange(v0, timeoutMs = 300_000)
    }

    @Test
    fun testAuthenticatedOfflineModeKeepsUserLoggedInUntilReconnectRefreshesExpiredToken() {
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = expiringAccessTokenTTL,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = true)
        waitForDesc("UserPageRoot", 90_000)
        waitForDesc("OfflineModeBadge", 90_000)
        waitDurationSeconds((expiringAccessTokenTTL + 2).toLong())
        waitForDesc("AuthenticatedOfflineModeEnabled")
        val versionBeforeReconnect = accessTokenVersion()
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = false)
        dismissBrowserForegroundIfNeeded()
        waitForUserEmail("test@frontegg.com", timeoutMs = 150_000)
        waitForAccessTokenVersionChange(versionBeforeReconnect, timeoutMs = 150_000)
    }

    @Test
    fun testLogoutTerminateTransientNoConnectionThenCustomSSORecovers() {
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        tapDesc("LogoutButton", 30_000)
        waitForDesc("LoginPageRoot", 60_000)
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = true)
        waitForDesc("NoConnectionPageRoot", 95_000)
        mock.reset()
        tapDesc("RetryConnectionButton", 15_000)
        waitForDesc("LoginPageRoot", 60_000)
        tapDesc("E2ECustomSSOButton")
        Thread.sleep(20_000)
        waitForUserEmail("custom-sso@frontegg.com", 180_000)
    }

    @Test
    fun testColdLaunchWithOfflineModeDisabledReachesLoginQuickly() {
        mock.queueProbeFailures(listOf(503, 503))
        launchApp(resetState = true, enableOfflineMode = false)
        waitForDesc("LoginPageRoot", 120_000)
        Thread.sleep(3500)
    }

    @Test
    fun testOfflineModeDisabledPreservesSessionDuringConnectionLossAndRecovers() {
        // Pin a long access TTL so the relaunch /me call succeeds against the cached token
        // and the SDK never needs to hit /oauth/token. The queued 503 below stays unconsumed
        // and asserts that a *transient* server error sitting in the pipeline does NOT cause
        // session loss when offline mode is disabled.
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = longLivedRefreshTokenTTL,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true, enableOfflineMode = false)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        terminateApp()
        mock.enqueue(
            "POST",
            "/oauth/token",
            listOf(mapOf("status" to 503, "json" to org.json.JSONObject().put("error", "transient"))),
        )
        launchApp(resetState = false, enableOfflineMode = false)
        waitForUserEmail("test@frontegg.com", timeoutMs = 150_000)
        Thread.sleep(2000)
        mock.reset()
    }

    @Test
    fun testAuthenticatedColdStartWithExpiredAccessTokenAndTransientProbeFailurePreservesSession() {
        // Reproduces customer report: app backgrounded 5+ hours → FCM push wakes the
        // process → user lands on login screen instead of authenticated state.
        //
        // FCM-triggered cold starts often fire while the radio is resuming from doze, so
        // the SDK's network-quality probe (HEAD /<base>/test) can transiently fail even
        // though the device is online and the refresh token is valid.
        //
        // With enableOfflineMode = false (the default), the network-gate branch at
        // FronteggAuthService.kt:1216-1230 currently calls clearCredentials() on a single
        // failed probe — wiping a perfectly good session. This test asserts the opposite:
        // a transient probe failure must NOT destroy the session; the SDK should proceed
        // to refresh-token validation and keep the user authenticated.
        //
        // Expected to fail until the bug at FronteggAuthService.kt:1226 is fixed.
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = expiringAccessTokenTTL,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true, enableOfflineMode = false)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        terminateApp()
        // Simulate the long background: access token expires before relaunch.
        waitDurationSeconds((expiringAccessTokenTTL + 14).toLong())
        // Simulate the FCM-wake-from-doze condition: the first network-quality probe
        // after relaunch returns 503. Subsequent probes will succeed (only one queued).
        mock.queueProbeFailures(listOf(503))
        launchApp(resetState = false, enableOfflineMode = false)
        dismissBrowserForegroundIfNeeded()
        instrumentation.waitForIdleSync()
        // Today: lands on LoginPageRoot. After fix: must stay authenticated.
        waitForUserEmail("test@frontegg.com", timeoutMs = 300_000)
    }

    @Test
    fun testPasswordLoginWorksWithOfflineModeDisabled() {
        launchApp(resetState = true, enableOfflineMode = false)
        waitForDesc("LoginPageRoot", 120_000)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
    }

    @Test
    fun testLogoutClearsSessionAndRelaunchShowsLogin() {
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 150_000)
        tapDesc("LogoutButton", 30_000)
        waitForDesc("LoginPageRoot", 90_000)
        terminateApp()
        launchApp(resetState = false)
        dismissBrowserForegroundIfNeeded()
        Thread.sleep(3_000)
        instrumentation.waitForIdleSync()
        waitForDesc("LoginPageRoot", 180_000)
    }

    @Test
    fun testExpiredRefreshTokenClearsSessionAndShowsLogin() {
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = 30,
            refreshTTL = 12,
        )
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        waitDurationSeconds(18)
        terminateApp()
        // Same-process instrumentation: use bootstrap reset so login is reachable after policy expiry.
        launchApp(resetState = true)
        Thread.sleep(2_500)
        waitForDesc("LoginPageRoot", 120_000)
    }

    @Test
    fun testScheduledTokenRefreshFiresBeforeExpiry() {
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = 45,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        val start = oauthRefreshRequestCount()
        Thread.sleep(42_000)
        assert(oauthRefreshRequestCount() > start)
    }

    @Test
    fun testAuthenticatedRelaunchWithExpiredAccessTokenAndFreshRefreshToken() {
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = expiringAccessTokenTTL,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        terminateApp()
        // Extra slack for CI emulators (clock + load); access must be expired before relaunch.
        waitDurationSeconds((expiringAccessTokenTTL + 14).toLong())
        launchApp(resetState = false)
        dismissBrowserForegroundIfNeeded()
        instrumentation.waitForIdleSync()
        Thread.sleep(2_000)
        waitForUserEmail("test@frontegg.com", timeoutMs = 300_000)
    }

    @Test
    fun testDPoPProofSignsAuthorizationCodeExchange() {
        launchApp(resetState = true, enableDPoP = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)

        val exchange = waitForTokenRequests("authorization_code", count = 1).first()
        assertEquals(1, exchange.headers.values("DPoP").size)
        val proof = dpopProof(exchange)
        assertEquals("dpop+jwt", proof.header.getString("typ"))
        assertEquals("ES256", proof.header.getString("alg"))
        assertEquals("EC", proof.jwk.getString("kty"))
        assertEquals("P-256", proof.jwk.getString("crv"))
        assertFalse("The proof must not expose the private key", proof.jwk.has("d"))
        assertEquals("POST", proof.claims.getString("htm"))
        val htu = proof.claims.getString("htu")
        assertTrue("htu=$htu", htu.endsWith("/oauth/token") && !htu.contains('?'))
        assertTrue(proof.claims.getString("jti").isNotBlank())
        val nowSeconds = System.currentTimeMillis() / 1000
        assertTrue("iat is not current", abs(nowSeconds - proof.claims.getLong("iat")) < 600)
        assertFalse("A token request proof carries no ath", proof.claims.has("ath"))
        assertTrue("The proof signature does not verify with its jwk", proof.hasValidSignature())
    }

    @Test
    fun testDPoPRefreshReusesDeviceKeyWithoutSendingBoundToken() {
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = scheduledRefreshAccessTokenTTL,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true, enableDPoP = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        val exchangeProof = dpopProof(waitForTokenRequests("authorization_code", count = 1).first())

        val refresh = waitForTokenRequests("refresh_token", count = 1, timeoutMs = 120_000).first()
        val refreshProof = dpopProof(refresh)
        assertEquals(exchangeProof.jwkThumbprint, refreshProof.jwkThumbprint)
        assertEquals("POST", refreshProof.claims.getString("htm"))
        assertTrue(refreshProof.claims.getString("htu").endsWith("/oauth/token"))
        assertNotEquals(exchangeProof.claims.getString("jti"), refreshProof.claims.getString("jti"))
        assertTrue(refreshProof.hasValidSignature())
        assertNull("A bound access token must not be sent on a token request", refresh.headers["Authorization"])

        val refreshedTokenVersion = 2
        val refreshedMeCall = waitForMeCallWithTokenVersion(refreshedTokenVersion)
        assertTrue(refreshedMeCall.headers["Authorization"].orEmpty().startsWith("DPoP "))
        assertEquals(exchangeProof.jwkThumbprint, dpopProof(refreshedMeCall).jwkThumbprint)
        waitForDesc("UserPageRoot", 30_000)
    }

    @Test
    fun testDPoPBoundAccessTokenUsesDPoPSchemeOnResourceCalls() {
        launchApp(resetState = true, enableDPoP = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        val exchangeProof = dpopProof(waitForTokenRequests("authorization_code", count = 1).first())

        val meRequests = mock.requests("GET", mePath)
        assertTrue(
            "A bound access token was sent as Bearer",
            meRequests.none { it.headers["Authorization"]?.startsWith("Bearer ") == true },
        )
        val meRequest = meRequests.lastOrNull { it.headers["Authorization"] != null }
            ?: throw AssertionError("Expected an authenticated GET $mePath")
        val authorization = meRequest.headers["Authorization"].orEmpty()
        assertTrue("Expected the DPoP scheme", authorization.startsWith("DPoP "))
        val accessToken = authorization.removePrefix("DPoP ")
        val tokenClaims = accessTokenClaims(meRequest)
            ?: throw AssertionError("GET $mePath carried an undecodable access token")
        assertEquals(exchangeProof.jwkThumbprint, tokenClaims.getJSONObject("cnf").getString("jkt"))

        val resourceProof = dpopProof(meRequest)
        assertEquals(exchangeProof.jwkThumbprint, resourceProof.jwkThumbprint)
        assertEquals("GET", resourceProof.claims.getString("htm"))
        assertTrue(resourceProof.claims.getString("htu").endsWith(mePath))
        assertEquals(DPoPProof.sha256Base64Url(accessToken), resourceProof.claims.optString("ath"))
        assertTrue(resourceProof.hasValidSignature())
    }

    @Test
    fun testDPoPNonceChallengeIsRetriedOnceWithServerNonce() {
        val serverNonce = "e2e-nonce-${UUID.randomUUID()}"
        mock.enqueue(
            "POST",
            "/oauth/token",
            listOf(
                mapOf(
                    "status" to 400,
                    "json" to JSONObject().put("error", "use_dpop_nonce"),
                    "headers" to mapOf("DPoP-Nonce" to serverNonce),
                ),
            ),
        )
        launchApp(resetState = true, enableDPoP = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)

        val exchangeAttempts = waitForTokenRequests("authorization_code", count = 2)
        assertEquals("The nonce challenge must be retried exactly once", 2, exchangeAttempts.size)
        val challengedProof = dpopProof(exchangeAttempts[0])
        val retriedProof = dpopProof(exchangeAttempts[1])
        assertNotEquals(serverNonce, challengedProof.claims.optString("nonce"))
        assertEquals(serverNonce, retriedProof.claims.optString("nonce"))
        assertNotEquals(challengedProof.claims.getString("jti"), retriedProof.claims.getString("jti"))

        val laterProof = mock.requests("GET", mePath).lastOrNull { it.headers["DPoP"] != null }
            ?.let { dpopProof(it) }
            ?: throw AssertionError("Expected a DPoP-bound GET $mePath after sign-in")
        assertEquals("The server nonce is remembered for the host", serverNonce, laterProof.claims.optString("nonce"))
    }

    @Test
    fun testDPoPDisabledSendsNoProofs() {
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = scheduledRefreshAccessTokenTTL,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true, enableDPoP = false)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        waitForTokenRequests("refresh_token", count = 1, timeoutMs = 120_000)

        val requestsWithProof = mock.allRequests().filter { it.headers["DPoP"] != null }
        assertTrue(
            "Unexpected DPoP proofs on ${requestsWithProof.map { "${it.method} ${it.path}" }}",
            requestsWithProof.isEmpty(),
        )
        val meAuthorizations = mock.requests("GET", mePath).mapNotNull { it.headers["Authorization"] }
        assertTrue("Expected an authenticated GET $mePath", meAuthorizations.isNotEmpty())
        assertTrue("Expected the Bearer scheme", meAuthorizations.all { it.startsWith("Bearer ") })
    }

    @Test
    fun testDPoPKeyRotatesOnLogout() {
        launchApp(resetState = true, enableDPoP = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        val firstSessionProof = dpopProof(waitForTokenRequests("authorization_code", count = 1).first())

        tapDesc("LogoutButton", 30_000)
        waitForDesc("LoginPageRoot", 90_000)
        terminateApp()
        launchApp(resetState = false, enableDPoP = true)
        dismissBrowserForegroundIfNeeded()
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)

        val secondSessionProof = dpopProof(waitForTokenRequests("authorization_code", count = 2)[1])
        assertNotEquals(firstSessionProof.jwkThumbprint, secondSessionProof.jwkThumbprint)
        assertTrue(secondSessionProof.hasValidSignature())
    }

    @Test
    fun testPlayIntegrityUnavailableFailsOpenOnSignInAndRefresh() {
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = scheduledRefreshAccessTokenTTL,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true, playIntegrityCloudProjectNumber = unavailablePlayIntegrityProjectNumber)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        waitForTokenRequests("authorization_code", count = 1)

        waitForTokenRequests("refresh_token", count = 1, timeoutMs = 120_000)
        waitForMeCallWithTokenVersion(2)
        assertNoIntegrityTokenSent()
    }

    @Test
    fun testPlayIntegrityDisabledSendsNoIntegrityToken() {
        launchApp(resetState = true, playIntegrityCloudProjectNumber = 0L)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        waitForTokenRequests("authorization_code", count = 1)

        assertNoIntegrityTokenSent()
    }

    @Test
    fun testPlayIntegrityAttestsCodeExchangeButNotRefresh() {
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = scheduledRefreshAccessTokenTTL,
            refreshTTL = longLivedRefreshTokenTTL,
        )
        launchApp(resetState = true, playIntegrityCloudProjectNumber = fakePlayIntegrityProjectNumber)
        FakeDeviceIntegrity.install()
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)

        val exchange = waitForTokenRequests("authorization_code", count = 1).first()
        assertEquals(FakeDeviceIntegrity.tokenFor(exchange.body), exchange.headers[deviceIntegrityHeader])
        val refresh = waitForTokenRequests("refresh_token", count = 1, timeoutMs = 120_000).first()
        assertNull("Refreshes must not be attested", refresh.headers[deviceIntegrityHeader])
        val attestedRequests = mock.allRequests()
            .filter { it.headers[deviceIntegrityHeader] != null }
            .map { "${it.method} ${it.path}" }
        assertEquals(listOf("POST /oauth/token"), attestedRequests)
    }

    private fun dpopProof(request: CapturedRequest): DPoPProof {
        val proof = request.headers["DPoP"]
            ?: throw AssertionError("${request.method} ${request.path} carried no DPoP proof")
        return DPoPProof.parse(proof)
    }

    private fun accessTokenClaims(request: CapturedRequest): JSONObject? {
        val accessToken = request.headers["Authorization"]?.substringAfter(' ', "")?.takeIf { it.isNotEmpty() }
            ?: return null
        return runCatching { DPoPProof.decodeJsonSegment(accessToken.split('.')[1]) }.getOrNull()
    }

    private fun waitForMeCallWithTokenVersion(tokenVersion: Int, timeoutMs: Long = 60_000): CapturedRequest {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            mock.requests("GET", mePath)
                .lastOrNull { accessTokenClaims(it)?.optInt("token_version") == tokenVersion }
                ?.let { return it }
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("No GET $mePath carried an access token with token_version=$tokenVersion")
            }
            Thread.sleep(500)
        }
    }

    private fun assertNoIntegrityTokenSent() {
        val attestedRequests = mock.allRequests().filter { it.headers[deviceIntegrityHeader] != null }
        assertTrue(
            "Unexpected $deviceIntegrityHeader on ${attestedRequests.map { "${it.method} ${it.path}" }}",
            attestedRequests.isEmpty(),
        )
    }

    @Test
    fun testStateFlowUserEmailAppearsAfterPasswordLogin() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        waitForDescText("StateFlowIsAuthenticatedValue", "false")
        waitForDescText("StateFlowUserEmailValue", "none")
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        waitForDescText("StateFlowUserEmailValue", "test@frontegg.com")
        waitForDescText("StateFlowIsAuthenticatedValue", "true")
    }

    @Test
    fun testStateFlowAuthStateClearsAfterLogout() {
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        waitForDescText("StateFlowUserEmailValue", "test@frontegg.com")
        tapDesc("LogoutButton", 30_000)
        waitForDesc("LoginPageRoot", 90_000)
        waitForDescText("StateFlowIsAuthenticatedValue", "false")
        waitForDescText("StateFlowUserEmailValue", "none")
    }

    @Test
    fun testStateFlowAccessTokenUpdatesAfterTokenRefresh() {
        val loginTokenVersion = 1
        mock.configureTokenPolicy(
            email = "test@frontegg.com",
            accessTTL = 45,
            refreshTTL = longLivedRefreshTokenTTL,
            startVer = loginTokenVersion,
        )
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        waitForDescText("StateFlowAccessTokenVersionValue", loginTokenVersion.toString())
        val requestCountAfterLogin = oauthRefreshRequestCount()
        val refreshedVersion = waitForAccessTokenVersionChange(
            from = loginTokenVersion,
            timeoutMs = 120_000,
            contentDescription = "StateFlowAccessTokenVersionValue",
        )
        if (oauthRefreshRequestCount() <= requestCountAfterLogin) {
            throw AssertionError("Expected a token refresh request before the StateFlow token changed")
        }
        waitForDescText("AccessTokenVersionValue", refreshedVersion.toString())
        waitForDescText("StateFlowUserEmailValue", "test@frontegg.com")
    }

    private fun waitForDescText(contentDescription: String, expectedText: String, timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastSeenText = descText(contentDescription)
        while (lastSeenText != expectedText && System.currentTimeMillis() < deadline) {
            Thread.sleep(350)
            lastSeenText = descText(contentDescription)
        }
        if (lastSeenText != expectedText) {
            throw AssertionError("Expected $contentDescription to read '$expectedText' but it read '$lastSeenText'")
        }
    }
}
