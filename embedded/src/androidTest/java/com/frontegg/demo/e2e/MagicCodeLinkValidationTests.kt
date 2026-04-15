package com.frontegg.demo.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontegg.demo.DemoEmbeddedTestMode
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Magic code/link validation E2E tests: incorrect code, code expiration,
 * link expiration, and resend button.
 */
@RunWith(AndroidJUnit4::class)
class MagicCodeLinkValidationTests : EmbeddedE2ETestCase() {

    @Test
    fun testMagicCodeIncorrectCode() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, "magic-code")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EMagicCodeButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        // Enter wrong code
        enterWebInput("e2e-code-input", "000000")
        clickWebElement("e2e-magic-code-submit")
        Thread.sleep(3_000)
        verifyErrorMessage("Invalid code")
    }

    @Test
    fun testMagicCodeExpiration() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, "magic-code")
        mock.configureMagicCodeExpiration(2_000) // 2 seconds
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EMagicCodeButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        // Wait for code to expire
        Thread.sleep(5_000)
        enterWebInput("e2e-code-input", "123456")
        clickWebElement("e2e-magic-code-submit")
        Thread.sleep(3_000)
        verifyErrorMessage("Code expired")
    }

    @Test
    fun testMagicLinkExpiration() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, "magic-link")
        mock.configureMagicLinkExpiration(1_000) // 1 second — link will expire before auto-redirect (3s)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EMagicLinkButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        verifyWebText("Magic link sent", 30_000)
        // Wait for auto-redirect — link is already expired
        Thread.sleep(10_000)
        verifyErrorMessage("Link expired")
    }

    @Test
    fun testResendCodeButton() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, "magic-code")
        mock.configureMagicCodeExpiration(2_000) // code expires quickly
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EMagicCodeButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        // Wait for initial code to expire
        Thread.sleep(5_000)
        // Click "Resend code" button — this issues a new code with fresh expiration
        clickWebElement("e2e-resend-code")
        Thread.sleep(5_000)
        // Verify we got a new code page (resend redirects back to magic-code page)
        verifyWebText("Enter code", 30_000)
        // Now enter the correct code — should work because resend issued a fresh code
        enterWebInput("e2e-code-input", "123456")
        clickWebElement("e2e-magic-code-submit")
        waitForUserEmail(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, timeoutMs = 120_000)
    }
}
