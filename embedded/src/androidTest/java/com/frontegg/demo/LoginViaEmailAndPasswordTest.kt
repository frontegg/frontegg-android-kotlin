package com.frontegg.demo

import androidx.test.uiautomator.By
import com.frontegg.demo.utils.Env
import com.frontegg.demo.utils.UiTestInstrumentation
import com.frontegg.demo.utils.logout
import com.frontegg.demo.utils.tapLoginButton
import org.junit.Before
import org.junit.Test


class LoginViaEmailAndPasswordTest {
    private lateinit var instrumentation: UiTestInstrumentation

    @Before
    fun setUp() {
        instrumentation = UiTestInstrumentation()
        instrumentation.openApp()
    }

    @Test
    fun success_login_via_email_and_password() {
        instrumentation.tapLoginButton()

        instrumentation.inputTextByIndex(0, Env.loginEmail)

        instrumentation.clickByText("Continue")

        instrumentation.inputTextByIndex(1, Env.loginPassword)

        instrumentation.clickByText("Sign in")

        instrumentation.logout()
    }

    @Test
    fun failure_login_via_wrong_email_and_password() {
        instrumentation.tapLoginButton()

        instrumentation.inputTextByIndex(0, Env.loginWrongEmail)

        instrumentation.clickByText("Continue")

        instrumentation.inputTextByIndex(1, Env.loginPassword)

        instrumentation.clickByText("Sign in")

        instrumentation.waitForView(By.text("Incorrect email or password"), timeout = 2_000) ?: throw Exception("Should be 'Incorrect email or password' warning")
    }

    @Test
    fun failure_login_via_email_and_wrong_password() {
        instrumentation.tapLoginButton()

        instrumentation.inputTextByIndex(0, Env.loginEmail)

        instrumentation.clickByText("Continue")

        instrumentation.inputTextByIndex(1, Env.loginWrongPassword)

        instrumentation.clickByText("Sign in")

        instrumentation.waitForView(By.text("Incorrect email or password"), timeout = 2_000) ?: throw Exception("Should be 'Incorrect email or password' warning")
    }
}