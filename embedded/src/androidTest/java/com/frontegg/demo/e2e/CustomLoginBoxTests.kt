package com.frontegg.demo.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontegg.demo.DemoEmbeddedTestMode
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Custom login box E2E tests: password login, magic code/link, and social login
 * when the custom login box configuration is enabled.
 */
@RunWith(AndroidJUnit4::class)
class CustomLoginBoxTests : EmbeddedE2ETestCase() {

    @Test
    fun testCustomLoginBoxPasswordLogin() {
        mock.configureCustomLoginBox(true)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2ECustomLoginBoxButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        tapWebButtonIfPresent("Sign in", timeoutMs = 95_000)
        waitForUserEmail(DemoEmbeddedTestMode.CUSTOM_LOGIN_BOX_EMAIL, timeoutMs = 120_000)
    }

    @Test
    fun testCustomLoginBoxMagicCodeLink() {
        mock.configureCustomLoginBox(true)
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
        enterWebInput("e2e-code-input", "123456")
        clickWebElement("e2e-magic-code-submit")
        waitForUserEmail(DemoEmbeddedTestMode.MAGIC_CODE_EMAIL, timeoutMs = 120_000)
    }

    @Test
    fun testCustomLoginBoxSocialLogin() {
        mock.configureCustomLoginBox(true)
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedGoogleSocialButton")
        Thread.sleep(20_000)
        waitForUserEmail("google-social@frontegg.com", timeoutMs = 120_000)
    }
}
