package com.frontegg.demo

import androidx.test.uiautomator.By
import com.frontegg.demo.utils.Env
import com.frontegg.demo.utils.UiTestInstrumentation
import com.frontegg.demo.utils.logout
import com.frontegg.demo.utils.tapLoginButton
import org.junit.Before
import org.junit.Test

class SwitchTenantTest {
    private lateinit var instrumentation: UiTestInstrumentation

    @Before
    fun setUp() {
        instrumentation = UiTestInstrumentation()
        instrumentation.openApp()
    }

    @Test
    fun success_tenant_switch() {
        instrumentation.tapLoginButton()

        instrumentation.inputTextByIndex(0, Env.loginEmail)

        instrumentation.clickByText("Continue")

        instrumentation.inputTextByIndex(1, Env.loginPassword)

        instrumentation.clickByText("Sign in")

        // Switch to Tenant Tab
        instrumentation.clickByText("Tenants")

        // Switch tenant 2
        instrumentation.clickByText(Env.tenantName2)
        instrumentation.waitForView(By.text(" (active)"))
        checkTenantIsActiveByTenantName(Env.tenantName2)

        // Switch tenant 1
        instrumentation.clickByText(Env.tenantName1)
        instrumentation.waitForView(By.text(" (active)"))
        checkTenantIsActiveByTenantName(Env.tenantName1)

        // Switch to Profile Tab
        instrumentation.clickByText("Profile")

        instrumentation.logout()
    }

    private fun checkTenantIsActiveByTenantName(tenantName: String): Boolean {
        val listView = instrumentation.waitForView(By.res("com.frontegg.demo:id/tenants_list"))
        if (listView != null) {
            for (child in listView.children) {
                if (child.children.size >= 2) {
                    // active
                    assert(child.children.first().text == tenantName)
                    assert(child.children.last().text == " (active)")
                }
            }
        } else {
            throw Exception("Cannot find 'com.frontegg.demo:id/tenants_list'")
        }
        return false
    }
}