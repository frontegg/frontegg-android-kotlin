package com.frontegg.demo

import androidx.test.uiautomator.By
import com.frontegg.demo.utils.Env
import com.frontegg.demo.utils.UiTestInstrumentation
import com.frontegg.demo.utils.delay
import com.frontegg.demo.utils.logout
import com.frontegg.demo.utils.tapLoginButton
import org.junit.Before
import org.junit.Test

class LoginViaGoogleTest {
    private lateinit var instrumentation: UiTestInstrumentation

    @Before
    fun setUp() {
        instrumentation = UiTestInstrumentation()
        instrumentation.openApp()
    }

    @Test
    fun success_login_via_Google_provider() {
        instrumentation.clickByText("LOGIN WITH GOOGLE")

        loginViaGoogle()

        instrumentation.logout()
    }

    @Test
    fun success_login_with_Google() {
        instrumentation.tapLoginButton()

        instrumentation.clickByText("continue with google")

        loginViaGoogle()

        instrumentation.logout()
    }

    private fun loginViaGoogle() {
        instrumentation.clickByText("Accept & continue")

        instrumentation.clickByText("No thanks")
        delay(ms = 5_000)

        if (instrumentation.waitForView(By.text("Sign in")) != null) {
            instrumentation.inputTextByIndex(0, Env.googleEmail)

            instrumentation.clickByText("Next")
            delay(ms = 5_000)

            instrumentation.inputTextByIndex(0, Env.googlePassword)

            instrumentation.clickByText("Welcome")
            delay(ms = 1_000)

            instrumentation.clickByText("Next")
        }

        instrumentation.clickByText(Env.googleEmail)

        instrumentation.clickByText("Open application")
    }
}