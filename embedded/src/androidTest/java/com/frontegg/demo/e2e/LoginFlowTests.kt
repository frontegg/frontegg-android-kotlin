package com.frontegg.demo.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontegg.demo.DemoEmbeddedTestMode
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Login flow E2E tests covering: password complexity, MFA, magic code/link, SMS, and username login.
 */
@RunWith(AndroidJUnit4::class)
class LoginFlowTests : EmbeddedE2ETestCase() {

    // ── Password complexity ──

    @Test
    fun testPasswordLoginMediumComplexity() {
        mock.configurePasswordPolicy("medium")
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testPasswordLoginHardComplexity() {
        mock.configurePasswordPolicy("hard")
        launchApp(resetState = true)
        loginWithPassword()
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
    }

    // ── MFA ──

    @Test
    fun testLoginWithMfaAuthenticator() {
        mock.configureMfa(DemoEmbeddedTestMode.MFA_AUTHENTICATOR_EMAIL, "authenticator")
        mock.configureLoginMethod(DemoEmbeddedTestMode.MFA_AUTHENTICATOR_EMAIL, "password")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EMfaAuthenticatorButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        // Password page → submit → MFA page
        tapWebButtonIfPresent("Sign in", timeoutMs = 95_000)
        Thread.sleep(5_000)
        verifyMfaPage("authenticator")
        submitMfaCode("123456")
        waitForUserEmail(DemoEmbeddedTestMode.MFA_AUTHENTICATOR_EMAIL, timeoutMs = 120_000)
    }

    @Test
    fun testLoginWithMfaSms() {
        mock.configureMfa(DemoEmbeddedTestMode.MFA_SMS_EMAIL, "sms")
        mock.configureLoginMethod(DemoEmbeddedTestMode.MFA_SMS_EMAIL, "password")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EMfaSmsButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        tapWebButtonIfPresent("Sign in", timeoutMs = 95_000)
        Thread.sleep(5_000)
        verifyMfaPage("sms")
        submitMfaCode("123456")
        waitForUserEmail(DemoEmbeddedTestMode.MFA_SMS_EMAIL, timeoutMs = 120_000)
    }

    // ── Magic Code ──

    @Test
    fun testLoginWithMagicCode() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, "magic-code")
        launchApp(resetState = true)
        loginWithMagicCode()
        waitForUserEmail(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, timeoutMs = 120_000)
    }

    @Test
    fun testLoginWithMagicCodeMfaAuthenticator() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, "magic-code")
        mock.configureMfa(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, "authenticator")
        launchApp(resetState = true)
        loginWithMagicCode()
        Thread.sleep(5_000)
        verifyMfaPage("authenticator")
        submitMfaCode("123456")
        waitForUserEmail(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, timeoutMs = 120_000)
    }

    @Test
    fun testLoginWithMagicCodeMfaSms() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, "magic-code")
        mock.configureMfa(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, "sms")
        launchApp(resetState = true)
        loginWithMagicCode()
        Thread.sleep(5_000)
        verifyMfaPage("sms")
        submitMfaCode("123456")
        waitForUserEmail(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, timeoutMs = 120_000)
    }

    // ── Magic Link ──

    @Test
    fun testLoginWithMagicLink() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, "magic-link")
        launchApp(resetState = true)
        loginWithMagicLink()
        waitForUserEmail(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, timeoutMs = 120_000)
    }

    @Test
    fun testLoginWithMagicLinkMfaAuthenticator() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, "magic-link")
        mock.configureMfa(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, "authenticator")
        launchApp(resetState = true)
        loginWithMagicLink()
        Thread.sleep(5_000)
        verifyMfaPage("authenticator")
        submitMfaCode("123456")
        waitForUserEmail(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, timeoutMs = 120_000)
    }

    @Test
    fun testLoginWithMagicLinkMfaSms() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, "magic-link")
        mock.configureMfa(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, "sms")
        launchApp(resetState = true)
        loginWithMagicLink()
        Thread.sleep(5_000)
        verifyMfaPage("sms")
        submitMfaCode("123456")
        waitForUserEmail(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, timeoutMs = 120_000)
    }

    // ── SMS Login ──

    @Test
    fun testLoginWithSms() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.SMS_LOGIN_EMAIL, "sms")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ESmsLoginButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        // Phone number page → enter phone → Continue → code page → enter code → Verify
        enterWebInput("e2e-phone-input", "+15550001234")
        clickWebElement("e2e-sms-submit-phone")
        Thread.sleep(5_000)
        enterWebInput("e2e-sms-code-input", "123456")
        clickWebElement("e2e-sms-verify")
        waitForUserEmail(DemoEmbeddedTestMode.SMS_LOGIN_EMAIL, timeoutMs = 120_000)
    }

    // ── Username Login ──

    @Test
    fun testLoginWithUsername() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.USERNAME_LOGIN_EMAIL, "username")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EUsernameLoginButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        enterWebInput("e2e-username-input", "testuser")
        enterWebInput("e2e-username-password-input", "Testpassword1!")
        clickWebElement("e2e-username-submit")
        waitForUserEmail(DemoEmbeddedTestMode.USERNAME_LOGIN_EMAIL, timeoutMs = 120_000)
    }
}
