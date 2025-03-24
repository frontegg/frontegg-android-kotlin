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

        instrumentation.waitForView(By.text(Env.applicationId))
            ?: throw Exception("Application ID was not found, objects: ${instrumentation.getAllObjects()}")

        instrumentation.logout()
    }
}