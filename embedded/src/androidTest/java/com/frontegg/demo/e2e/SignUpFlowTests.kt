package com.frontegg.demo.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sign-up flow E2E tests covering: SMS signup, username signup, social signup,
 * email verification redirect, and terms of use checkbox.
 */
@RunWith(AndroidJUnit4::class)
class SignUpFlowTests : EmbeddedE2ETestCase() {

    @Test
    fun testSignUpWithSms() {
        mock.configureLoginMethod("test-sms-signup@frontegg.com", "sms")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ESignupButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        enterWebInput("e2e-signup-phone", "+15550009999")
        enterWebInput("e2e-signup-name", "SMS User")
        enterWebInput("e2e-signup-password", "Testpassword1!")
        clickWebElement("e2e-signup-submit")
        waitForUserEmail("test-sms-signup@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testSignUpWithUsername() {
        mock.configureLoginMethod("test-username-signup@frontegg.com", "username")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ESignupButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        enterWebInput("e2e-signup-username", "newuser123")
        enterWebInput("e2e-signup-name", "Username User")
        enterWebInput("e2e-signup-password", "Testpassword1!")
        clickWebElement("e2e-signup-submit")
        waitForUserEmail("test-username-signup@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testSignUpWithSocialLogin() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        // Use the embedded Google social button which goes through mock IdP
        tapDesc("E2EEmbeddedGoogleSocialButton")
        Thread.sleep(20_000)
        waitForUserEmail("google-social@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testSignUpWithEmailVerificationRedirectPassword() {
        mock.configureEmailVerificationRedirect(true)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ESignupButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        enterWebInput("e2e-signup-email", "verify-pw@frontegg.com")
        enterWebInput("e2e-signup-name", "Verify User")
        enterWebInput("e2e-signup-password", "Testpassword1!")
        enterWebInput("e2e-signup-company", "Test Corp")
        clickWebElement("e2e-signup-submit")
        Thread.sleep(5_000)
        // Verify the "Verification email sent" page appears
        verifyWebText("Verification email sent", 30_000)
        // Auto-redirect simulates clicking the email link
        waitForUserEmail("verify-pw@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testSignUpWithEmailVerificationRedirectPasswordless() {
        mock.configureEmailVerificationRedirect(true)
        mock.configureLoginMethod("verify-pl@frontegg.com", "magic-code")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ESignupButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        enterWebInput("e2e-signup-email", "verify-pl@frontegg.com")
        enterWebInput("e2e-signup-name", "Passwordless User")
        enterWebInput("e2e-signup-password", "Testpassword1!")
        enterWebInput("e2e-signup-company", "Test Corp")
        clickWebElement("e2e-signup-submit")
        Thread.sleep(5_000)
        verifyWebText("Verification email sent", 30_000)
        waitForUserEmail("verify-pl@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testSignUpWithCheckedTos() {
        mock.configureTosRequired(true)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ESignupButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        enterWebInput("e2e-signup-email", "tos-ok@frontegg.com")
        enterWebInput("e2e-signup-name", "TOS User")
        enterWebInput("e2e-signup-password", "Testpassword1!")
        enterWebInput("e2e-signup-company", "Test Corp")
        // Check the TOS checkbox
        clickWebElement("e2e-tos-checkbox")
        clickWebElement("e2e-signup-submit")
        waitForUserEmail("tos-ok@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testSignUpWithUncheckedTos() {
        mock.configureTosRequired(true)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ESignupButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        enterWebInput("e2e-signup-email", "tos-fail@frontegg.com")
        enterWebInput("e2e-signup-name", "TOS Fail User")
        enterWebInput("e2e-signup-password", "Testpassword1!")
        enterWebInput("e2e-signup-company", "Test Corp")
        // Do NOT check the TOS checkbox
        clickWebElement("e2e-signup-submit")
        Thread.sleep(3_000)
        verifyErrorMessage("You must accept the Terms of Use and Privacy Policy")
    }

    @Test
    fun testSignUpTosErrorOnSetPasswordPage() {
        mock.configureTosRequired(true)
        mock.configureEmailVerificationRedirect(true)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ESignupButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        enterWebInput("e2e-signup-email", "tos-setpw@frontegg.com")
        enterWebInput("e2e-signup-name", "TOS SetPW User")
        enterWebInput("e2e-signup-password", "Testpassword1!")
        enterWebInput("e2e-signup-company", "Test Corp")
        // Do NOT check TOS
        clickWebElement("e2e-signup-submit")
        Thread.sleep(3_000)
        verifyErrorMessage("You must accept the Terms of Use and Privacy Policy")
    }
}
