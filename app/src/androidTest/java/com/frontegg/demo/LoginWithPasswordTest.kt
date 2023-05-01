package com.frontegg.demo

import androidx.test.espresso.web.webdriver.*
import androidx.test.espresso.web.webdriver.DriverAtoms.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.frontegg.android.FronteggWebView
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4::class)
open class LoginWithPasswordTest {
    @Rule
    @JvmField
    var mActivityScenarioRule = ActivityScenarioRule(FronteggActivity::class.java)

    @Test
    fun useAppContext() {

        var webView: FronteggWebView? = null
        mActivityScenarioRule.scenario.onActivity {
            webView = it.webView
        }

        val webHelper = WebViewHelper(webView!!)
        webHelper.typeText("input-email", "test@frontegg.com")
        webHelper.click("submit-btn")
        webHelper.typeText("input-password", "test-password")
        webHelper.click("submit-btn")
        webHelper.getContent("submit-btn")

        Thread.sleep(10000)

    }
}