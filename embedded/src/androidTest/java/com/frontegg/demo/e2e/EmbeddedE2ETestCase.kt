package com.frontegg.demo.e2e

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.frontegg.demo.App
import com.frontegg.demo.NavigationActivity
import org.junit.After
import org.junit.Before
import java.util.concurrent.TimeUnit

/**
 * Base class for embedded mock-server E2E tests (Swift `DemoEmbeddedUITestCase` parity).
 */
open class EmbeddedE2ETestCase {

    /** Merged APK id for the SDK embedded layout's WebView (library R differs from app at compile time). */
    private fun authWebView() = run {
        val id = instrumentation.targetContext.resources.getIdentifier(
            "custom_webview",
            "id",
            instrumentation.targetContext.packageName,
        )
        require(id != 0) { "custom_webview not found in merged resources" }
        onWebView(withId(id))
    }

    protected lateinit var mock: LocalMockAuthServer
    private var scenario: ActivityScenario<NavigationActivity>? = null

    protected val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    protected val instrumentation: Instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Before
    open fun e2eBaseSetUp() {
        mock = LocalMockAuthServer()
        mock.start()
    }

    @After
    open fun e2eBaseTearDown() {
        scenario?.close()
        mock.shutdown()
    }

    protected fun launchApp(
        resetState: Boolean = true,
        forceNetworkPathOffline: Boolean = false,
        enableOfflineMode: Boolean? = null,
    ) {
        scenario?.close()
        scenario = null
        // Custom Tabs / Chrome often stay on top after SSO; the next test still sees the browser
        // while our process already has NavigationActivity — waitForDesc then times out on LoginPageRoot.
        dismissBrowserForegroundIfNeeded()
        Thread.sleep(450)
        // Do not call mock.reset() here: each @Before gets a fresh MockWebServer. Resetting on every
        // relaunch would drop refresh-token state and any queued probe responses (breaks session restore,
        // token refresh, and tests that enqueue failures before launchApp).
        E2EBootstrap.write(
            baseUrl = mock.urlRoot(),
            clientId = mock.clientId,
            resetState = resetState,
            forceNetworkPathOffline = forceNetworkPathOffline,
            enableOfflineMode = enableOfflineMode,
        )
        instrumentation.runOnMainSync {
            (instrumentation.targetContext.applicationContext as App).consumeAndApplyE2EBootstrapFromDisk()
        }
        // Without Test Orchestrator, tests share one process; a plain launch() can leave the previous
        // task/back stack (e.g. still on the authenticated graph). Clear task so each launchApp starts cold UI.
        val launchIntent = Intent(instrumentation.targetContext, NavigationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        scenario = ActivityScenario.launch(launchIntent)
        ensureDemoAppForegroundAfterLaunch()
    }

    private fun targetPackageName(): String = instrumentation.targetContext.packageName

    private fun foregroundPackage(): String? =
        runCatching { device.currentPackageName }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun isLikelyBrowserPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        return "chrome" in p || p.endsWith(".browser") || "customtabs" in p
    }

    /** Press back while a browser/Custom Tab is in the foreground so the next launch is visible. */
    protected fun dismissBrowserForegroundIfNeeded() {
        repeat(15) {
            val cur = foregroundPackage() ?: return
            if (cur == targetPackageName()) return
            if (!isLikelyBrowserPackage(cur)) return
            runCatching { device.pressBack() }
            Thread.sleep(450)
        }
    }

    private fun waitUntilDemoWindowVisible(timeoutMs: Long): Boolean {
        val pkg = targetPackageName()
        return device.wait(Until.hasObject(By.pkg(pkg)), timeoutMs)
    }

    private fun dismissSystemDialogIfNeeded() {
        val fg = foregroundPackage() ?: return
        if (fg == targetPackageName()) return
        if (isLikelyBrowserPackage(fg)) return
        repeat(5) {
            runCatching { device.pressBack() }
            Thread.sleep(350)
            val cur = foregroundPackage()
            if (cur == null || cur == targetPackageName()) return
        }
    }

    private fun launchDemoActivityIntent() {
        val ctx = instrumentation.targetContext
        val i = Intent(ctx, NavigationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        ctx.startActivity(i)
    }

    private fun ensureDemoAppForegroundAfterLaunch() {
        val pkg = targetPackageName()
        runCatching { device.wakeUp() }

        var ok = waitUntilDemoWindowVisible(TimeUnit.SECONDS.toMillis(35))
        var fg = foregroundPackage()
        if (ok && (fg == null || fg == pkg)) return

        dismissSystemDialogIfNeeded()
        dismissBrowserForegroundIfNeeded()
        launchDemoActivityIntent()
        Thread.sleep(800)
        ok = waitUntilDemoWindowVisible(TimeUnit.SECONDS.toMillis(20))
        fg = foregroundPackage()
        if (ok && (fg == null || fg == pkg)) return

        runCatching { device.pressHome() }
        Thread.sleep(500)
        launchDemoActivityIntent()
        ok = waitUntilDemoWindowVisible(TimeUnit.SECONDS.toMillis(20))
        fg = foregroundPackage()
        if (!ok || (fg != null && fg != pkg)) {
            throw AssertionError(
                "Demo app did not reach foreground (waitOk=$ok, foregroundPackage=$fg, expected=$pkg)",
            )
        }
    }

    protected fun terminateApp() {
        scenario?.close()
        scenario = null
        runCatching { device.pressHome() }
        Thread.sleep(400)
    }

    protected fun waitForDesc(contentDescription: String, timeoutMs: Long = 20_000) {
        val ok = device.wait(Until.hasObject(By.desc(contentDescription)), timeoutMs)
        if (!ok) {
            throw AssertionError("Timeout waiting for contentDescription=$contentDescription")
        }
    }

    protected fun waitForText(text: String, timeoutMs: Long = 20_000) {
        val ok = device.wait(Until.hasObject(By.text(text)), timeoutMs)
        if (!ok) {
            throw AssertionError("Timeout waiting for text=$text")
        }
    }

    protected fun tapDesc(contentDescription: String, timeoutMs: Long = 20_000) {
        scrollDescIntoView(contentDescription, timeoutMs)
        device.findObject(By.desc(contentDescription))?.click()
            ?: throw AssertionError("No node for desc=$contentDescription")
    }

    /** E2E controls sit inside a ScrollView; swipe until the node exists with non-zero bounds. */
    private fun scrollDescIntoView(contentDescription: String, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var swipes = 0
        while (System.currentTimeMillis() < deadline) {
            val o = device.findObject(By.desc(contentDescription))
            if (o != null) {
                val b = o.visibleBounds
                if (b.width() > 0 && b.height() > 0) return
            }
            val w = device.displayWidth
            val h = device.displayHeight
            val x = w / 2
            if (swipes % 2 == 0) {
                device.swipe(x, (h * 0.72).toInt(), x, (h * 0.32).toInt(), 12)
            } else {
                device.swipe(x, (h * 0.35).toInt(), x, (h * 0.78).toInt(), 12)
            }
            swipes++
            Thread.sleep(280)
            if (swipes > 18) break
        }
        val ok = device.wait(Until.hasObject(By.desc(contentDescription)), 2_000)
        if (!ok) {
            throw AssertionError("Timeout waiting for contentDescription=$contentDescription (scroll)")
        }
    }

    protected fun tapText(text: String, timeoutMs: Long = 10_000) {
        waitForText(text, timeoutMs)
        device.findObject(By.text(text))?.click()
            ?: throw AssertionError("No node for text=$text")
    }

    protected fun loginWithPassword() {
        waitForDesc("LoginPageRoot", 120_000)
        tapDesc("E2EEmbeddedPasswordButton")
        // Embedded WebView: hosted password step — pre-filled email; submit password flow
        Thread.sleep(3_500)
        tapWebButtonIfPresent("Sign in", timeoutMs = 90_000)
    }

    protected fun tapWebButtonIfPresent(text: String, timeoutMs: Long = 20_000) {
        val primaryLower = text.lowercase()
        if ("continue" in primaryLower || "mock" in primaryLower) {
            Thread.sleep(3_500)
        }
        if ("sign in" in primaryLower || "okta" in primaryLower) {
            Thread.sleep(4_000)
        }
        if (tryEspressoWebTapLink(text)) return
        val deadline = System.currentTimeMillis() + timeoutMs
        val primary = text.lowercase()
        val started = System.currentTimeMillis()
        var waitedChrome = false
        var lastEspressoRetry = System.currentTimeMillis()
        fun tryClickNeedle(n: String): Boolean {
            val needle = n.lowercase()
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return false
            try {
                val r = findBoundsForTextInTree(root, needle)
                if (r != null && r.width() > 4 && r.height() > 4) {
                    device.click(r.centerX(), r.centerY())
                    return true
                }
            } finally {
                root.recycle()
            }
            return false
        }
        while (System.currentTimeMillis() < deadline) {
            if (tryClickNeedle(primary)) return
            device.findObject(By.text(text))?.let { it.click(); return }
            device.findObject(By.textContains(text))?.let { it.click(); return }
            device.findObject(By.descContains(text))?.let { it.click(); return }
            val shortDesc = text.removePrefix("Continue ").removePrefix("Login ").trim()
            if (shortDesc.isNotEmpty() && shortDesc != text) {
                device.findObject(By.descContains(shortDesc))?.let { it.click(); return }
            }
            val subPhrase = when {
                "okta" in primary -> "Login With Okta"
                "mock google" in primary -> "Continue with Mock Google"
                "mock social" in primary -> "Continue Mock Social"
                "custom sso" in primary -> "Continue Custom SSO"
                else -> null
            }
            if (subPhrase != null && subPhrase != text) {
                if (tryClickNeedle(subPhrase)) return
                device.findObject(By.textContains(subPhrase))?.let { it.click(); return }
            }
            if (!waitedChrome && System.currentTimeMillis() - started > 2_500L) {
                waitedChrome = true
                device.wait(Until.hasObject(By.pkg("com.android.chrome")), 3_000)
                device.wait(Until.hasObject(By.pkg("com.google.android.apps.chrome")), 2_000)
            }
            if (System.currentTimeMillis() - lastEspressoRetry > 4_000) {
                lastEspressoRetry = System.currentTimeMillis()
                if (tryEspressoWebTapLink(text)) return
            }
            val w = device.displayWidth
            val h = device.displayHeight
            device.swipe(w / 2, (h * 0.65).toInt(), w / 2, (h * 0.35).toInt(), 10)
            Thread.sleep(280)
        }
        throw AssertionError("Web/UI button not found: $text")
    }

    private fun tryEspressoWebTapLink(text: String): Boolean {
        val lower = text.lowercase().trim()
        val tries = buildList {
            when {
                "sign in" in lower -> {
                    add { authWebView().withElement(findElement(Locator.LINK_TEXT, text)).perform(webClick()) }
                    add {
                        authWebView()
                            .withElement(
                                findElement(Locator.XPATH, "//button[contains(normalize-space(.), 'Sign in')]"),
                            )
                            .perform(webClick())
                    }
                    add {
                        authWebView()
                            .withElement(findElement(Locator.PARTIAL_LINK_TEXT, text))
                            .perform(webClick())
                    }
                }
                "login with okta" in lower -> {
                    add { authWebView().withElement(findElement(Locator.ID, "e2e-sso-okta")).perform(webClick()) }
                    add { authWebView().withElement(findElement(Locator.LINK_TEXT, text)).perform(webClick()) }
                    add {
                        authWebView()
                            .withElement(
                                findElement(
                                    Locator.XPATH,
                                    "//button[contains(normalize-space(.), 'Login With Okta')]",
                                ),
                            )
                            .perform(webClick())
                    }
                }
                "continue with mock google" in lower -> {
                    add {
                        authWebView()
                            .withElement(findElement(Locator.XPATH, "//*[@id='e2e-mock-google']"))
                            .perform(webClick())
                    }
                    add { authWebView().withElement(findElement(Locator.ID, "e2e-mock-google")).perform(webClick()) }
                    add { authWebView().withElement(findElement(Locator.LINK_TEXT, text)).perform(webClick()) }
                    add {
                        authWebView()
                            .withElement(findElement(Locator.PARTIAL_LINK_TEXT, text))
                            .perform(webClick())
                    }
                }
                "continue custom sso" in lower || "continue mock social" in lower -> {
                    add { authWebView().withElement(findElement(Locator.ID, "e2e-complete")).perform(webClick()) }
                    add { authWebView().withElement(findElement(Locator.LINK_TEXT, text)).perform(webClick()) }
                    add {
                        authWebView()
                            .withElement(findElement(Locator.PARTIAL_LINK_TEXT, text))
                            .perform(webClick())
                    }
                }
                else -> {
                    add { authWebView().withElement(findElement(Locator.LINK_TEXT, text)).perform(webClick()) }
                    add {
                        authWebView()
                            .withElement(findElement(Locator.PARTIAL_LINK_TEXT, text))
                            .perform(webClick())
                    }
                }
            }
        }
        for (t in tries) {
            try {
                t()
                return true
            } catch (_: Throwable) {
            }
        }
        return false
    }

    protected fun waitForA11yTextContains(fragment: String, timeoutMs: Long = 30_000): Boolean {
        val needle = fragment.lowercase()
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                try {
                    if (findBoundsForTextInTree(root, needle) != null) return true
                } finally {
                    root.recycle()
                }
            }
            Thread.sleep(250)
        }
        return false
    }

    private fun findBoundsForTextInTree(node: AccessibilityNodeInfo, needle: String): Rect? {
        val t = "${node.text}\n${node.contentDescription}".lowercase()
        if (needle in t && node.isVisibleToUser) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 4 && r.height() > 4) return r
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val found = findBoundsForTextInTree(c, needle)
            c.recycle()
            if (found != null) return found
        }
        return null
    }

    protected fun oauthRefreshRequestCount(): Int {
        val paths = listOf("/oauth/token", "/frontegg/identity/resources/auth/v1/user/token/refresh")
        return paths.sumOf { mock.requestCount(null, it) }
    }

    protected fun waitForUserEmail(email: String, timeoutMs: Long = 60_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        // Phase 1: wait WITHOUT pressing Back — let any in-flight OAuth redirect in the Custom Tab
        // complete naturally.  Pressing Back too early cancels the redirect.
        val firstWait = (timeoutMs * 3) / 5
        var ok = device.wait(Until.hasObject(By.desc("UserPageRoot")), firstWait)
        if (!ok) {
            // Phase 2: redirect should be done by now; dismiss any lingering browser / system overlay
            dismissBrowserForegroundIfNeeded()
            dismissSystemDialogIfNeeded()
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(5_000)
            ok = device.wait(Until.hasObject(By.desc("UserPageRoot")), remaining)
        }
        if (!ok) {
            val fg = foregroundPackage()
            throw AssertionError(
                "Timeout waiting for contentDescription=UserPageRoot (foreground=$fg, expected=${targetPackageName()})",
            )
        }
        val textWait = (deadline - System.currentTimeMillis()).coerceAtLeast(10_000)
        waitForText(email, textWait)
    }

    protected fun accessTokenVersion(): Int {
        waitForDesc("AccessTokenVersionValue", 10_000)
        val n = device.findObject(By.desc("AccessTokenVersionValue"))?.text?.toIntOrNull()
            ?: throw AssertionError("No AccessTokenVersionValue")
        return n
    }

    protected fun waitForAccessTokenVersionChange(from: Int, timeoutMs: Long = 30_000): Int {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val v = device.findObject(By.desc("AccessTokenVersionValue"))?.text?.toIntOrNull()
            if (v != null && v != from) return v
            Thread.sleep(350)
        }
        throw AssertionError("Token version did not change from $from")
    }

    protected fun waitDurationSeconds(seconds: Long) {
        Thread.sleep(TimeUnit.SECONDS.toMillis(seconds))
    }
}
