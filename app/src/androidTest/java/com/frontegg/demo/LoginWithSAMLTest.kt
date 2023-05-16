package com.frontegg.demo

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ApplicationProvider.getApplicationContext
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
open class LoginWithSAMLTest {
    @Test
    fun testLoginWithSAML() {
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

        val intent = Intent(getApplicationContext(), FronteggActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val scenario = ActivityScenario.launch<FronteggActivity>(intent)

        var webView: FronteggWebView? = null
        scenario.onActivity {
            webView = it.webView
        }

        val webHelper = WebViewHelper(webView!!)

        webHelper.typeText("input-email", "test@saml-domain.com")

        Mocker.mock(
            MockMethod.mockSSOPrelogin, mapOf(
                "options" to mapOf(
                    "success" to true,
                    "idpType" to "saml",
                    "address" to "http://10.0.2.2:3001/okta/saml"
                ),
                "partialRequestBody" to mapOf("email" to "test@saml-domain.com")
            )
        )
        webHelper.click("submit-btn")

        waitForWebViewUrl(webView!!, "http://10.0.2.2:3001/okta/saml")
        webHelper.findWithText("OKTA SAML Mock Server")
        webHelper.click("login-button")

        Thread.sleep(5000)

//        getCurrentActivity()?.finish()
//        Thread.sleep(5000)
    }
}