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
        instrumentation.tapLoginButton()

        instrumentation.clickByText("continue with google")

        instrumentation.clickByText(Env.googleEmail)

        instrumentation.clickByText("Open application")

        instrumentation.logout()
    }
}