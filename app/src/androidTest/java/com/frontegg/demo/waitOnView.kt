package com.frontegg.demo

import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers
import java.lang.Error
import java.lang.Exception
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


/**
 * Waiting for a specific view id to be displayed.
 * @param viewId The id of the view to wait for.
 * @param timeout The timeout of until when to wait for.
 */
fun waitOnView(viewId: Int, timeout: Int = 10000): ViewInteraction {
    return try {
        onView(withId(viewId)).check(
            matches(
                Matchers.allOf(
                    withId(viewId),
                    isDisplayed()
                )
            )
        )
    } catch (e: NoMatchingViewException) {
        if (timeout == 0) {
            throw e
        } else {
            Thread.sleep(1000)
            waitOnView(viewId, timeout - 1000)
        }
    }
}


class UrlNotMatchError(targetUrl: String) : Exception("WebView url does not match \"$targetUrl\"")

/**
 * Wait for webview url to be loaded
 * @param webView
 * @param timeout The timeout of until when to wait for.
 */
fun waitForWebViewUrl(webView: WebView, targetUrl: String, timeout: Int = 10000): Boolean {
    val latch = CountDownLatch(1)

    lateinit var intervalFunction: Runnable
    intervalFunction = Runnable {
        Thread.sleep(200)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (webView.url!!.startsWith(targetUrl)) {
                webView.evaluateJavascript("typeof window.searchInsideShadowDOM") {
                    if (it == "function") {
                        latch.countDown()
                    } else {
                        Thread(intervalFunction).start()
                        Thread.interrupted()
                    }
                }
            } else {
                Thread(intervalFunction).start()
            }
        }
    }
    Thread(intervalFunction).start()
    return latch.await(timeout.toLong(), TimeUnit.MILLISECONDS)

}