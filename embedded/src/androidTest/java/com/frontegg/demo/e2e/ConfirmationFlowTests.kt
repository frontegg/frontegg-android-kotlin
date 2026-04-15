package com.frontegg.demo.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontegg.demo.DemoEmbeddedTestMode
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Confirmation step E2E tests: magic link confirmation, user activation,
 * new account invitation, and account unlock.
 */
@RunWith(AndroidJUnit4::class)
class ConfirmationFlowTests : EmbeddedE2ETestCase() {

    @Test
    fun testConfirmMagicLink() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, "magic-link")
        launchApp(resetState = true)
        loginWithMagicLink()
        waitForUserEmail(DemoEmbeddedTestMode.MAGIC_LINK_EMAIL, timeoutMs = 120_000)
    }

    @Test
    fun testUserActivation() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EConfirmActivationButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        verifyWebText("Activate your account", 30_000)
        clickWebElement("e2e-confirm-submit")
        waitForUserEmail(DemoEmbeddedTestMode.ACTIVATION_EMAIL, timeoutMs = 120_000)
    }

    @Test
    fun testNewAccountInvitation() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EConfirmInvitationButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        verifyWebText("Accept invitation", 30_000)
        clickWebElement("e2e-confirm-submit")
        waitForUserEmail(DemoEmbeddedTestMode.INVITATION_EMAIL, timeoutMs = 120_000)
    }

    @Test
    fun testUnlockAccount() {
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EConfirmUnlockButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        verifyWebText("Unlock account", 30_000)
        clickWebElement("e2e-confirm-submit")
        waitForUserEmail(DemoEmbeddedTestMode.UNLOCK_EMAIL, timeoutMs = 120_000)
    }
}
