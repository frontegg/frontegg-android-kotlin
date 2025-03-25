package com.frontegg.demo

import androidx.test.uiautomator.By
import com.frontegg.demo.utils.Env
import com.frontegg.demo.utils.UiTestInstrumentation
import com.frontegg.demo.utils.logout
import com.frontegg.demo.utils.tapLoginButton
import org.junit.Before
import org.junit.Test


class SelectRegionTest {
    private lateinit var instrumentation: UiTestInstrumentation

    @Before
    fun setUp() {
        instrumentation = UiTestInstrumentation()
        instrumentation.clearApp()
        instrumentation.openApp()
    }

    @Test
    fun success_select_region_1_and_login() {
        instrumentation.clickByText("https://${Env.fronteggDomainRegion1}")

        instrumentation.inputTextByIndex(0, Env.loginEmail)

        instrumentation.clickByText("Continue", timeout = 3_000)

        instrumentation.inputTextByIndex(1, Env.loginPassword)

        instrumentation.clickByText("Sign in")

        if (instrumentation.waitForView(By.text("https://${Env.fronteggDomainRegion1}")) == null) {
            throw Exception("Should be '${Env.fronteggDomainRegion1}' on the Home screen")
        }

        instrumentation.clearApp()
    }

    @Test
    fun success_select_region_2_and_login() {
        instrumentation.clickByText("https://${Env.fronteggDomainRegion2}")

        instrumentation.inputTextByIndex(0, Env.loginEmail)

        instrumentation.clickByText("Continue", timeout = 3_000)

        instrumentation.inputTextByIndex(1, Env.loginPassword)

        instrumentation.clickByText("Sign in")

        if (instrumentation.waitForView(By.text("https://${Env.fronteggDomainRegion2}")) == null) {
            throw Exception("Should be '${Env.fronteggDomainRegion1}' on the Home screen")
        }

        instrumentation.clearApp()
    }
}