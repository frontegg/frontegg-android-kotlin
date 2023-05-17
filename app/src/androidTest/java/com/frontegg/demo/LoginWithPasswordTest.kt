package com.frontegg.demo

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.web.webdriver.*
import androidx.test.espresso.web.webdriver.DriverAtoms.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.frontegg.android.FronteggWebView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID


@LargeTest
@RunWith(AndroidJUnit4::class)
open class LoginWithPasswordTest {


    @Test
    fun testLoginWithPassword() {
        Mocker.mockClearMocks()
        val code = UUID.randomUUID().toString()
        Mocker.mock(
            MockMethod.mockHostedLoginAuthorize, mapOf(
                "delay" to 500,
                "options" to mapOf(
                    "code" to code,
                    "baseUrl" to Mocker.baseUrl,
                    "appUrl" to Mocker.baseUrl
                )
            )
        )

        val intent = Intent(ApplicationProvider.getApplicationContext(), FronteggActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val scenario = ActivityScenario.launch<FronteggActivity>(intent)

        var webView: FronteggWebView? = null
        scenario.onActivity {
            webView = it.webView
        }

        val webHelper = WebViewHelper(webView!!)


        webHelper.typeText("input-email", "test@frontegg.com")

        Mocker.mock(MockMethod.mockSSOPrelogin, mapOf("options" to mapOf("success" to false)))
        webHelper.click("submit-btn")

        webHelper.typeText("input-password", "Testpassword")

        Mocker.mockSuccessPasswordLogin(code)
        webHelper.findWithText("Sign in")
        webHelper.click("submit-btn")

        Thread.sleep(5000)
        waitOnView(R.id.textview_first).check(matches(withText("test@frontegg.com")))
        Thread.sleep(5000)
        waitOnView(R.id.logout_button).perform(click())
    }
}