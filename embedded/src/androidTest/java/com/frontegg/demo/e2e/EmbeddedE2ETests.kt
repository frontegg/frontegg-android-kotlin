package com.frontegg.demo.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith

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
}
