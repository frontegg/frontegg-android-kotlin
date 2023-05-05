package com.frontegg.demo

import android.os.Looper
import android.util.Log
import androidx.test.espresso.IdlingResource
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import com.frontegg.android.FronteggWebView
import java.lang.Exception
import java.lang.RuntimeException
import java.net.URL
import java.util.Scanner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class WebViewHelper(private val webView: FronteggWebView) {
    private var id = 1
    private val delayBetweenActions = 500
    private val defaultTimeout = 20000

    init {
        val initScript = getUrlContent()

        waitForWebView()

        val script =
            "const BETWEEN_ACTIONS_TIMEOUT = $delayBetweenActions;" +
                    "const DEFAULT_TIMEOUT = $defaultTimeout;" +
                    initScript
        evaluateJavascript(script)
    }

    private fun waitForWebView(): Boolean {
        val latch = CountDownLatch(1)
        val timeoutInSeconds = 20L
        lateinit var intervalFunction: Runnable
        val intervalInMillis = 200L
        intervalFunction = Runnable {
            if (latch.count > 0) {
                val jsCheck = "document.querySelectorAll('frontegg-login-box-container-default').length > 0"
                val handler: (result: String) -> Unit = {
                    if (it == "true") latch.countDown() else {
                        Thread.sleep(intervalInMillis)
                        Thread(intervalFunction).start()
                        Thread.interrupted()
                    }
                }
                if (Looper.getMainLooper().isCurrentThread) {
                    webView.evaluateJavascript(jsCheck, handler)
                } else {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        webView.evaluateJavascript(jsCheck, handler)
                    }
                }
            }
        }
        Thread(intervalFunction).start()
        return latch.await(timeoutInSeconds, TimeUnit.SECONDS)

    }

    private fun getUrlContent(): String {
        val url = URL("http://10.0.2.2:3004/ui-helpers.js")
        val scanner = Scanner(url.openStream())
        scanner.useDelimiter("\\Z")
        return if (scanner.hasNext()) scanner.next() else ""
    }

    private fun evaluateJavascript(runJs: String): Boolean? {
        val isAsync = runJs.startsWith("await ")
        if (!isAsync) {
            return webView.evaluateJavascriptSync(runJs)
        }
        val js = runJs.removePrefix("await ")

        val interfaceName = "WaitForPromise_${id++}"
        val completableFuture = CompletableFuture<Boolean?>()
        webView.addPromiseListener(interfaceName) { _, error ->
            if(error != null) {
                completableFuture.completeExceptionally(Exception(error))
            }else {
                completableFuture.complete(true)
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript("$js.then(res=>FronteggHanlder.then(\"$interfaceName\", res)).catch(e => FronteggHanlder.catch(\"$interfaceName\", e))") { }
        }

        return completableFuture.get(40, TimeUnit.SECONDS)
    }

    private fun WebView.evaluateJavascriptSync(js: String): Boolean? {
        var result: Boolean? = null

        val lock = Object()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            evaluateJavascript(js) { value ->
                result = value != null
                synchronized(lock) {
                    lock.notify()
                }
            }
        }

        synchronized(lock) {
            lock.wait(10_000)
        }

        return result
    }


    fun click(testId: String) {
        this.evaluateJavascript("await click(`$testId`)")
    }

    fun typeText(testId: String, text: String) {
        this.evaluateJavascript("await typeText(`$testId`, `$text`)")
    }

    fun findWithText(searchText: String) {
        this.evaluateJavascript("await findWithText(`$searchText`, `contains`)")
    }

    fun exist(selector: Boolean) {
        this.evaluateJavascript("await findElement(`$selector`)")
    }

    fun getAttr(testId: String, attr: String) {
        this.evaluateJavascript("await getAttr(`$testId`,`$attr`)")
    }
}

