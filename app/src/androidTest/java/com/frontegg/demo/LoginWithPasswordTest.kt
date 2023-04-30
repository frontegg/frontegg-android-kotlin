package com.frontegg.demo

import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.clearElement
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.DriverAtoms.webKeys
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@LargeTest
@RunWith(AndroidJUnit4::class)
open class LoginWithPasswordTest {
    @Rule
    @JvmField
    var mActivityScenarioRule = ActivityScenarioRule(FronteggActivity::class.java)

    @Test
    fun useAppContext() {

//        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
//        assertEquals("com.frontegg.demo", appContext.packageName)
//
//        ActivityScenario.launch(FronteggActivity::class.java)
        Thread.sleep(5000)

        onWebView()
            .withTimeout(20, TimeUnit.SECONDS)
            .withElement(
                findElement(Locator.CSS_SELECTOR, "input[data-test-id=\"input-email\"]")
            )
            .perform(clearElement())
            .perform(webClick())
            .perform(webKeys("test@frontegg.com"))

        Thread.sleep(5000)

        onWebView()
            .withTimeout(20, TimeUnit.SECONDS)
            .withElement(
                findElement(Locator.CSS_SELECTOR, "button[data-test-id=\"submit-btn\"]")
            )
            .perform(webClick())

        Thread.sleep(20000)


    }
}