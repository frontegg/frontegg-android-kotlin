package com.frontegg.demo

import androidx.test.core.app.ActivityScenario
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

        val scenario = ActivityScenario.launch(FronteggActivity::class.java)
        var webView: FronteggWebView? = null
        scenario.onActivity {
            webView = it.webView
        }

        val webHelper = WebViewHelper(webView!!)

        webHelper.typeText("input-email", "test@saml-domain.com")

        Mocker.mock(MockMethod.mockSSOPrelogin, mapOf(
                "options" to mapOf(
                    "success" to true,
                    "idpType" to "saml",
                    "address" to "http://10.0.2.2:3001/okta/saml"
                ),
                "partialRequestBody" to mapOf("email" to "test@saml-domain.com")
            ))
        webHelper.click("submit-btn")

        waitForWebViewUrl(webView!!, "http://10.0.2.2:3001/okta/saml")
        webHelper.findWithText("OKTA SAML Mock Server")
        webHelper.click("login-button");

//        Mocker.mockSuccessPasswordLogin(code)

        Thread.sleep(100000)
//        waitOnView(R.id.textview_first).check(matches(withText("test@frontegg.com")))
    }
}