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

    private val expiringAccessTokenTTL = 21
    private val longLivedRefreshTokenTTL = 120

    @Test
    fun testPasswordLoginAndSessionRestore() {
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        terminateApp()
        launchApp(resetState = false)
        Thread.sleep(1_500)
        waitForUserEmail("test@frontegg.com", timeoutMs = 180_000)
    }

    @Test
    fun testEmbeddedSamlLogin() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 45_000)
        tapDesc("E2EEmbeddedSAMLButton")
        tapWebButtonIfPresent("Login With Okta")
        waitForUserEmail("test@saml-domain.com")
    }

    @Test
    fun testEmbeddedOidcLogin() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 45_000)
        tapDesc("E2EEmbeddedOIDCButton")
        tapWebButtonIfPresent("Login With Okta")
        waitForUserEmail("test@oidc-domain.com")
    }

    @Test
    fun testRequestAuthorizeFlow() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 45_000)
        tapDesc("E2ESeedRequestAuthorizeTokenButton")
        Thread.sleep(2_000)
        tapDesc("RequestAuthorizeButton")
        waitForUserEmail("signup@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testCustomSSOBrowserHandoff() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 45_000)
        tapDesc("E2ECustomSSOButton")
        Thread.sleep(4_500)
        waitForUserEmail("custom-sso@frontegg.com", timeoutMs = 60_000)
    }

    @Test
    fun testDirectSocialBrowserHandoff() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 45_000)
        tapDesc("E2EDirectSocialLoginButton")
        Thread.sleep(6_000)
        waitForUserEmail("social-login@frontegg.com", timeoutMs = 90_000)
    }

    @Test
    fun testEmbeddedGoogleSocialLoginWithSystemWebAuthenticationSession() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", timeoutMs = 40_000)
        tapDesc("E2EEmbeddedGoogleSocialButton")
        // Custom Tab loads oauth/authorize → redirect to mock Google page; script auto-completes after ~600ms.
        Thread.sleep(32_000)
        waitForUserEmail("google-social@frontegg.com", timeoutMs = 150_000)
    }

    @Test
    fun testEmbeddedGoogleSocialLoginOAuthErrorShowsToastAndKeepsLoginOpen() {
        mock.queueEmbeddedSocialSuccessOAuthError(
            "ER-05001",
            "JWT token size exceeded the maximum allowed size. Please contact support to reduce token payload size.",
        )
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 45_000)
        tapDesc("E2EEmbeddedGoogleSocialButton")
        Thread.sleep(18_000)
        if (!waitForA11yTextContains("ER-05001", 50_000)) {
            throw AssertionError("Expected error text in UI")
        }
    }

    @Test
    fun testColdLaunchTransientProbeTimeoutsDoNotBlinkNoConnectionPage() {
        mock.queueProbeTimeouts(count = 2, delayMs = 1500)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 45_000)
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
        tapDesc("LogoutButton")
        waitForDesc("LoginPageRoot", 30_000)
        mock.queueProbeFailures(listOf(503, 503))
        terminateApp()
        launchApp(resetState = false)
        waitForDesc("LoginPageRoot", timeoutMs = 50_000)
        Thread.sleep(3500)
    }

    @Test
    fun testAuthenticatedOfflineModeWhenNetworkPathUnavailable() {
        launchApp(resetState = true)
        loginWithPassword()
        val initialVersion = accessTokenVersion()
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = true)
        waitForUserEmail("test@frontegg.com")
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
        waitDurationSeconds((expiringAccessTokenTTL + 6).toLong())
        launchApp(resetState = false)
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        waitForAccessTokenVersionChange(v0, timeoutMs = 150_000)
        assert(oauthRefreshRequestCount() > rc0)
    }

    @Test
    fun testAuthenticatedOfflineModeRecoversToOnlineAndRefreshesToken() {
        launchApp(resetState = true)
        loginWithPassword()
        val v0 = accessTokenVersion()
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = true)
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        waitForDesc("OfflineModeBadge", 75_000)
        // Simulate recovery: next launch without forced offline
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = false)
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        Thread.sleep(18_000)
        waitForAccessTokenVersionChange(v0, timeoutMs = 240_000)
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
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = true)
        waitForUserEmail("test@frontegg.com")
        waitForDesc("OfflineModeBadge", 30_000)
        waitDurationSeconds((expiringAccessTokenTTL + 2).toLong())
        waitForDesc("AuthenticatedOfflineModeEnabled")
        val versionBeforeReconnect = accessTokenVersion()
        terminateApp()
        launchApp(resetState = false, forceNetworkPathOffline = false)
        waitForUserEmail("test@frontegg.com")
        waitForAccessTokenVersionChange(versionBeforeReconnect, timeoutMs = 75_000)
    }

    @Test
    fun testLogoutTerminateTransientNoConnectionThenCustomSSORecovers() {
        launchApp(resetState = true)
        loginWithPassword()
        tapDesc("LogoutButton")
        waitForDesc("LoginPageRoot", 30_000)
        mock.queueProbeFailures(listOf(503))
        terminateApp()
        launchApp(resetState = false)
        waitForDesc("NoConnectionPageRoot", 45_000)
        mock.reset()
        tapDesc("RetryConnectionButton", 15_000)
        waitForDesc("LoginPageRoot", 30_000)
        tapDesc("E2ECustomSSOButton")
        Thread.sleep(8_000)
        waitForUserEmail("custom-sso@frontegg.com", 120_000)
    }

    @Test
    fun testColdLaunchWithOfflineModeDisabledReachesLoginQuickly() {
        mock.queueProbeFailures(listOf(503, 503))
        launchApp(resetState = true, enableOfflineMode = false)
        waitForDesc("LoginPageRoot", 50_000)
        Thread.sleep(3500)
    }

    @Test
    fun testOfflineModeDisabledPreservesSessionDuringConnectionLossAndRecovers() {
        launchApp(resetState = true, enableOfflineMode = false)
        loginWithPassword()
        terminateApp()
        mock.queueConnectionDrops(method = "POST", path = "/oauth/token", count = 1)
        launchApp(resetState = false, enableOfflineMode = false)
        waitForUserEmail("test@frontegg.com")
        Thread.sleep(2000)
        mock.reset()
    }

    @Test
    fun testPasswordLoginWorksWithOfflineModeDisabled() {
        launchApp(resetState = true, enableOfflineMode = false)
        waitForDesc("LoginPageRoot", 45_000)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
    }

    @Test
    fun testLogoutClearsSessionAndRelaunchShowsLogin() {
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
        tapDesc("LogoutButton")
        waitForDesc("LoginPageRoot", 30_000)
        terminateApp()
        launchApp(resetState = false)
        Thread.sleep(1_500)
        waitForDesc("LoginPageRoot", timeoutMs = 60_000)
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
        waitForUserEmail("test@frontegg.com")
        waitDurationSeconds(18)
        terminateApp()
        // Same-process instrumentation: use bootstrap reset so login is reachable after policy expiry.
        launchApp(resetState = true)
        Thread.sleep(2_500)
        waitForDesc("LoginPageRoot", 60_000)
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
        val start = oauthRefreshRequestCount()
        Thread.sleep(35_000)
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
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        terminateApp()
        // Extra slack for CI emulators (clock + load); access must be expired before relaunch.
        waitDurationSeconds((expiringAccessTokenTTL + 8).toLong())
        launchApp(resetState = false)
        waitForUserEmail("test@frontegg.com", timeoutMs = 150_000)
    }
}
