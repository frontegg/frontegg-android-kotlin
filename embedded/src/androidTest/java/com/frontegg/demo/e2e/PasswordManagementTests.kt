package com.frontegg.demo.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontegg.demo.DemoEmbeddedTestMode
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Password management E2E tests covering: forget password (complexity levels, email, SMS),
 * account locking, breached password, and password expiration.
 */
@RunWith(AndroidJUnit4::class)
class PasswordManagementTests : EmbeddedE2ETestCase() {

    // ── Forget Password ──

    @Test
    fun testForgetPasswordEasyComplexity() {
        mock.configurePasswordPolicy("easy")
        launchApp(resetState = true)
        navigateToForgotPassword()
        enterWebInput("e2e-forgot-email", DemoEmbeddedTestMode.FORGOT_PASSWORD_EMAIL)
        clickWebElement("e2e-forgot-submit")
        Thread.sleep(5_000)
        // Should land on reset password page
        verifyWebText("Set new password", 30_000)
        verifyWebText("Min 6 chars", 10_000)
        enterWebInput("e2e-new-password", "Simple1")
        clickWebElement("e2e-reset-submit")
        Thread.sleep(3_000)
        verifyWebText("Password changed successfully", 20_000)
    }

    @Test
    fun testForgetPasswordMediumComplexity() {
        mock.configurePasswordPolicy("medium")
        launchApp(resetState = true)
        navigateToForgotPassword()
        enterWebInput("e2e-forgot-email", DemoEmbeddedTestMode.FORGOT_PASSWORD_EMAIL)
        clickWebElement("e2e-forgot-submit")
        Thread.sleep(5_000)
        verifyWebText("Set new password", 30_000)
        verifyWebText("Min 8 chars", 10_000)
        enterWebInput("e2e-new-password", "Medium1Pass")
        clickWebElement("e2e-reset-submit")
        Thread.sleep(3_000)
        verifyWebText("Password changed successfully", 20_000)
    }

    @Test
    fun testForgetPasswordHardComplexity() {
        mock.configurePasswordPolicy("hard")
        launchApp(resetState = true)
        navigateToForgotPassword()
        enterWebInput("e2e-forgot-email", DemoEmbeddedTestMode.FORGOT_PASSWORD_EMAIL)
        clickWebElement("e2e-forgot-submit")
        Thread.sleep(5_000)
        verifyWebText("Set new password", 30_000)
        verifyWebText("Min 12 chars", 10_000)
        enterWebInput("e2e-new-password", "HardPass123!@x")
        clickWebElement("e2e-reset-submit")
        Thread.sleep(3_000)
        verifyWebText("Password changed successfully", 20_000)
    }

    @Test
    fun testForgetPasswordWithEmail() {
        launchApp(resetState = true)
        navigateToForgotPassword()
        enterWebInput("e2e-forgot-email", DemoEmbeddedTestMode.FORGOT_PASSWORD_EMAIL)
        clickWebElement("e2e-forgot-submit")
        Thread.sleep(5_000)
        verifyWebText("Set new password", 30_000)
        enterWebInput("e2e-new-password", "NewPassword1!")
        clickWebElement("e2e-reset-submit")
        Thread.sleep(3_000)
        verifyWebText("Password changed successfully", 20_000)
    }

    @Test
    fun testForgetPasswordWithSms() {
        mock.configureLoginMethod(DemoEmbeddedTestMode.FORGOT_PASSWORD_EMAIL, "sms")
        launchApp(resetState = true)
        navigateToForgotPassword()
        // SMS-based forgot password uses phone input
        enterWebInput("e2e-forgot-phone", "+15550001234")
        clickWebElement("e2e-forgot-submit")
        Thread.sleep(5_000)
        verifyWebText("Set new password", 30_000)
        enterWebInput("e2e-new-password", "NewPassword1!")
        clickWebElement("e2e-reset-submit")
        Thread.sleep(3_000)
        verifyWebText("Password changed successfully", 20_000)
    }

    // ── Account Locking ──

    @Test
    fun testAccountLockedAfterIncorrectAttempts() {
        mock.configureAccountLocking(DemoEmbeddedTestMode.LOCKED_ACCOUNT_EMAIL, 3)
        // Pre-record 3 failed attempts
        mock.configureLoginMethod(DemoEmbeddedTestMode.LOCKED_ACCOUNT_EMAIL, "password")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedPasswordButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        tapWebButtonIfPresent("Sign in", timeoutMs = 95_000)
        Thread.sleep(5_000)
        verifyErrorMessage("Your account is locked")
    }

    // ── Breached Password ──

    @Test
    fun testBreachedPasswordWarning() {
        mock.configureBreachedPassword("Testpassword1!")
        launchApp(resetState = true)
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedPasswordButton")
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.clazz("android.webkit.WebView"),
            ),
            30_000,
        )
        Thread.sleep(10_000)
        tapWebButtonIfPresent("Sign in", timeoutMs = 95_000)
        Thread.sleep(5_000)
        verifyErrorMessage("Password has been breached")
    }

    // ── Password Expiration ──

    @Test
    fun testPasswordExpirationRemindLater() {
        mock.configurePasswordExpiration(
            "test@frontegg.com",
            daysLeft = 5,
            canRemindLater = true,
        )
        launchApp(resetState = true)
        loginWithPassword()
        Thread.sleep(5_000)
        verifyWebText("Your password will expire in 5 days", 30_000)
        // Click "Remind me later" to skip and proceed to authenticated page
        clickWebElement("e2e-remind-later")
        waitForUserEmail("test@frontegg.com", timeoutMs = 120_000)
    }

    @Test
    fun testPasswordExpirationChangePassword() {
        mock.configurePasswordExpiration(
            "test@frontegg.com",
            daysLeft = 5,
            canRemindLater = true,
        )
        launchApp(resetState = true)
        loginWithPassword()
        Thread.sleep(5_000)
        verifyWebText("Your password will expire in 5 days", 30_000)
        // Click "Change password" to go to password reset page
        clickWebElement("e2e-change-password")
        Thread.sleep(5_000)
        verifyWebText("Set new password", 30_000)
        enterWebInput("e2e-new-password", "NewPassword1!")
        clickWebElement("e2e-reset-submit")
        Thread.sleep(3_000)
        verifyWebText("Password changed successfully", 20_000)
    }

    @Test
    fun testPasswordExpiredNoRemindLater() {
        mock.configurePasswordExpiration(
            "test@frontegg.com",
            daysLeft = 0,
            canRemindLater = false,
        )
        launchApp(resetState = true)
        loginWithPassword()
        Thread.sleep(5_000)
        verifyWebText("Your password has expired", 30_000)
        // Verify "Remind me later" button is NOT present
        Thread.sleep(2_000)
        val remindBtn = try {
            authWebView()
                .withElement(
                    androidx.test.espresso.web.webdriver.DriverAtoms.findElement(
                        androidx.test.espresso.web.webdriver.Locator.ID,
                        "e2e-remind-later",
                    ),
                )
            true
        } catch (_: Throwable) {
            false
        }
        assert(!remindBtn) { "Remind me later button should not be present on expired page" }
    }

    @Test
    fun testPasswordExpiredChangePassword() {
        mock.configurePasswordExpiration(
            "test@frontegg.com",
            daysLeft = 0,
            canRemindLater = false,
        )
        launchApp(resetState = true)
        loginWithPassword()
        Thread.sleep(5_000)
        verifyWebText("Your password has expired", 30_000)
        clickWebElement("e2e-change-password")
        Thread.sleep(5_000)
        verifyWebText("Set new password", 30_000)
        enterWebInput("e2e-new-password", "NewPassword1!")
        clickWebElement("e2e-reset-submit")
        Thread.sleep(3_000)
        verifyWebText("Password changed successfully", 20_000)
    }
}
