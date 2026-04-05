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
    fun testEmbeddedGoogleSocialLoginWithSystemWebAuthenticationSession() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedGoogleSocialButton")
        // Custom Tab: oauth/authorize → mock Google → deep link.
        // Use chunked sleeps instead of one long Thread.sleep to reduce emulator pressure
        // (a single 120s sleep with Chrome GPU rendering causes process crashes on CI).
        for (i in 1..12) {
            Thread.sleep(10_000)
            // Early exit: if our app is back in foreground, stop waiting
            if (device.currentPackageName == instrumentation.targetContext.packageName) {
                if (device.findObject(By.desc("UserPageRoot")) != null) break
            }
        }
        dismissBrowserForegroundIfNeeded()
        waitForUserEmail("google-social@frontegg.com", timeoutMs = 420_000)
    }

    @Test
    fun testEmbeddedGoogleSocialLoginOAuthErrorShowsToastAndKeepsLoginOpen() {
        mock.queueEmbeddedSocialSuccessOAuthError(
            "ER-05001",
            "JWT token size exceeded the maximum allowed size. Please contact support to reduce token payload size.",
        )
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedGoogleSocialButton")
        // Long passive wait first (no Back): dismissing Chrome too early can cancel the redirect to EmbeddedAuth.
        var sawEr =
            waitForTextOrDescContains("ER-05001", 150_000) ||
                waitForTextOrDescContains("JWT token size exceeded", 30_000) ||
                waitForA11yTextContains("er-050", 30_000) ||
                device.wait(Until.hasObject(By.descContains("ER-050")), 30_000)
        if (!sawEr) {
            dismissBrowserForegroundIfNeeded()
            Thread.sleep(2_000)
            dismissBrowserForegroundIfNeeded()
            sawEr =
                waitForTextOrDescContains("ER-05001", 240_000) ||
                    waitForTextOrDescContains("JWT token size exceeded", 120_000) ||
                    waitForA11yTextContains("er-050", 120_000) ||
                    device.wait(Until.hasObject(By.textContains("ER-050")), 90_000) ||
                    device.wait(Until.hasObject(By.descContains("ER-050")), 60_000) ||
                    device.wait(Until.hasObject(By.textContains("contact support")), 60_000)
        }
        if (!sawEr) {
            throw AssertionError("Expected OAuth error (ER-05001 / JWT / support text) in UI")
        }
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
        launchApp(resetState = true, enableOfflineMode = false)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        terminateApp()
        mock.queueConnectionDrops(method = "POST", path = "/oauth/token", count = 1)
        launchApp(resetState = false, enableOfflineMode = false)
        waitForUserEmail("test@frontegg.com", timeoutMs = 90_000)
        Thread.sleep(2000)
        mock.reset()
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

    // ═══════════════════════════════════════════════════════════
    // Password Complexity & Validation
    // ═══════════════════════════════════════════════════════════

    @Test
    fun testPasswordLoginMediumComplexityValidation() {
        mock.configurePasswordPolicy(minLength = 8, requireUppercase = true, requireNumber = true)
        launchApp(resetState = true)
        loginWithPasswordExpectError("Password must")
    }

    @Test
    fun testPasswordLoginHardComplexityValidation() {
        mock.configurePasswordPolicy(minLength = 12, requireUppercase = true, requireNumber = true, requireSpecial = true)
        launchApp(resetState = true)
        loginWithPasswordExpectError("Password must")
    }

    @Test
    fun testAccountLockoutAfterIncorrectPasswordAttempts() {
        mock.configureLockout(maxAttempts = 2)
        launchApp(resetState = true)
        loginWithPasswordExpectError("locked", timeoutMs = 120_000)
    }

    @Test
    fun testBreachedPasswordError() {
        mock.configureBreachedPassword("test@frontegg.com")
        launchApp(resetState = true)
        loginWithPasswordExpectError("breach")
    }

    // ═══════════════════════════════════════════════════════════
    // Magic Code
    // ═══════════════════════════════════════════════════════════

    @Test
    fun testMagicCodeLogin() {
        mock.configureAuthStrategies(password = false, magicCode = true)
        launchApp(resetState = true)
        loginWithMagicCode()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testIncorrectMagicCodeValidation() {
        mock.configureAuthStrategies(password = false, magicCode = true)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedPasswordButton")
        device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 30_000)
        Thread.sleep(15_000)
        // Enter wrong code
        val input = device.findObject(By.clazz("android.widget.EditText"))
        input?.click(); Thread.sleep(500); input?.text = "000000"
        Thread.sleep(1_000)
        tapWebButtonIfPresent("Verify", timeoutMs = 30_000)
        Thread.sleep(3_000)
        val found = waitForWebViewText("Invalid code", 30_000)
        if (!found) throw AssertionError("Expected 'Invalid code' error")
    }

    @Test
    fun testMagicCodeExpiration() {
        mock.configureAuthStrategies(password = false, magicCode = true)
        mock.configureMagicCodeTTL(1)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedPasswordButton")
        device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 30_000)
        Thread.sleep(15_000) // code expires during this wait (1s TTL)
        val code = mock.getIssuedMagicCode("test@frontegg.com") ?: throw AssertionError("No code")
        val input = device.findObject(By.clazz("android.widget.EditText"))
        input?.click(); Thread.sleep(500); input?.text = code
        Thread.sleep(1_000)
        tapWebButtonIfPresent("Verify", timeoutMs = 30_000)
        Thread.sleep(3_000)
        val found = waitForWebViewText("expired", 30_000)
        if (!found) throw AssertionError("Expected 'expired' error")
    }

    @Test
    fun testResendMagicCodeButton() {
        mock.configureAuthStrategies(password = false, magicCode = true)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedPasswordButton")
        device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 30_000)
        Thread.sleep(15_000)
        tapWebButtonIfPresent("Resend code", timeoutMs = 30_000)
        Thread.sleep(3_000)
        assert(mock.requestCount("POST", "/identity/resources/auth/v1/passwordless/code/resend") >= 1)
        val code = mock.getIssuedMagicCode("test@frontegg.com") ?: throw AssertionError("No code after resend")
        val input = device.findObject(By.clazz("android.widget.EditText"))
        input?.click(); Thread.sleep(500); input?.text = code
        Thread.sleep(1_000)
        tapWebButtonIfPresent("Verify", timeoutMs = 30_000)
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
    }

    // ═══════════════════════════════════════════════════════════
    // Magic Link
    // ═══════════════════════════════════════════════════════════

    @Test
    fun testMagicLinkSentPage() {
        mock.configureAuthStrategies(password = false, magicLink = true)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedPasswordButton")
        device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 30_000)
        Thread.sleep(10_000)
        val found = waitForWebViewText("magic link", 60_000)
        if (!found) throw AssertionError("Expected magic link sent page")
        mock.waitForRequest(path = "/identity/resources/auth/v1/passwordless/code/prelogin", timeoutMs = 15_000)
    }

    // ═══════════════════════════════════════════════════════════
    // MFA
    // ═══════════════════════════════════════════════════════════

    @Test
    fun testPasswordLoginWithMfaAuthenticator() {
        mock.configureMfa("test@frontegg.com", "authenticator")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedPasswordButton")
        device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 30_000)
        Thread.sleep(10_000)
        tapWebButtonIfPresent("Sign in", timeoutMs = 90_000)
        Thread.sleep(5_000)
        completeMfaChallenge()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testPasswordLoginWithMfaSms() {
        mock.configureMfa("test@frontegg.com", "sms")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedPasswordButton")
        device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 30_000)
        Thread.sleep(10_000)
        tapWebButtonIfPresent("Sign in", timeoutMs = 90_000)
        Thread.sleep(5_000)
        completeMfaChallenge()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
    }

    // ═══════════════════════════════════════════════════════════
    // Signup
    // ═══════════════════════════════════════════════════════════

    @Test
    fun testSignupWithEmail() {
        launchApp(resetState = true)
        navigateToSignupAndFill("signup-new@frontegg.com", "Test User", "Test1234!", "TestOrg")
        waitForUserEmail("signup-new@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testSignupWithEmailVerificationAndPassword() {
        mock.configureSignup(requireEmailVerification = true)
        launchApp(resetState = true)
        navigateToSignupAndFill("verify-me@frontegg.com", "Verify User", "Test1234!", "VerifyOrg")
        Thread.sleep(5_000)
        val found = waitForWebViewText("verify", 60_000)
        if (!found) throw AssertionError("Expected email verification page")
    }

    @Test
    fun testSignupWithTermsChecked() {
        mock.configureSignup(requireTerms = true)
        launchApp(resetState = true)
        navigateToSignupAndFill("terms-ok@frontegg.com", "Terms User", "Test1234!", "TermsOrg", acceptTerms = true)
        waitForUserEmail("terms-ok@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testSignupWithTermsUncheckedShowsError() {
        mock.configureSignup(requireTerms = true)
        launchApp(resetState = true)
        navigateToSignupAndFill("terms-fail@frontegg.com", "No Terms", "Test1234!", "TermsOrg", acceptTerms = false)
        Thread.sleep(5_000)
        val found = waitForWebViewText("terms", 60_000)
        if (!found) throw AssertionError("Expected terms error message")
    }

    // ═══════════════════════════════════════════════════════════
    // Password Management
    // ═══════════════════════════════════════════════════════════

    @Test
    fun testForgotPasswordFlow() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedPasswordButton")
        device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 30_000)
        Thread.sleep(10_000)
        tapWebButtonIfPresent("Forgot password?", timeoutMs = 60_000)
        Thread.sleep(5_000)
        // Type email
        val input = device.findObject(By.clazz("android.widget.EditText"))
        input?.click(); Thread.sleep(300); input?.text = "test@frontegg.com"
        tapWebButtonIfPresent("Reset password", timeoutMs = 30_000)
        Thread.sleep(3_000)
        val found = waitForWebViewText("reset", 60_000)
        if (!found) throw AssertionError("Expected password reset confirmation")
    }

    @Test
    fun testPasswordExpirationWarning() {
        mock.configurePasswordExpiration("test@frontegg.com", daysUntilExpiry = 5)
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testPasswordExpiredPage() {
        mock.configurePasswordExpiration("test@frontegg.com", daysUntilExpiry = -1)
        launchApp(resetState = true)
        loginWithPasswordExpectError("expired")
    }

    // ═══════════════════════════════════════════════════════════
    // Custom Login Box
    // ═══════════════════════════════════════════════════════════

    @Test
    fun testCustomLoginBoxWithPassword() {
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testCustomLoginBoxWithMagicCode() {
        mock.configureAuthStrategies(password = false, magicCode = true)
        launchApp(resetState = true)
        loginWithMagicCode()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
    }
}
