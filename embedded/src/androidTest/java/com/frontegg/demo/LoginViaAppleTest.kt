package com.frontegg.demo

import androidx.test.uiautomator.By
import com.frontegg.demo.utils.Env
import com.frontegg.demo.utils.UiTestInstrumentation
import com.frontegg.demo.utils.delay
import com.frontegg.demo.utils.logout
import com.frontegg.demo.utils.tapLoginButton
import org.junit.Before
import org.junit.Test

class LoginViaAppleTest {
    private lateinit var instrumentation: UiTestInstrumentation

    @Before
    fun setUp() {
        instrumentation = UiTestInstrumentation()
        instrumentation.openApp()
    }

    @Test
    fun success_login_via_Google_provider() {
        instrumentation.tapLoginButton()

        instrumentation.clickByText("continue with apple")

        instrumentation.inputTextByResourceId("account_name_text_field", Env.appleEmail)

        instrumentation.clickByResourceId("sign-in")

        delay(ms = 5_000)

        // For some reason clickByResourceId("continue-password") is not working
        val signInForm = instrumentation.waitForView(By.res("sign_in_form"))
        for (signInFormItem in signInForm?.children?.get(2)?.children ?: listOf()) {
            if (signInFormItem.resourceName == "continue-password") {
                signInFormItem.click()
            }
        }

        instrumentation.inputTextByResourceId("password_text_field", Env.applePassword)

        instrumentation.clickByResourceId("sign-in")

        instrumentation.clickByText("Continue")

        instrumentation.logout()
    }
}