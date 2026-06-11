package com.frontegg.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.core.view.WindowCompat
import com.frontegg.android.ui.FronteggBaseActivity
import com.frontegg.android.utils.DefaultDispatcherProvider
import com.frontegg.android.utils.LogUrlSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Embedded Frontegg admin portal — opens `${baseUrl}/oauth/portal?appId=<applicationId>`
 * in a WebView.
 *
 * **Beta — API may change in future minor releases.** The class name, [open]
 * entry point, presentation style (currently a full-screen [Activity]), and
 * Manifest registration are all subject to revision while this feature is in
 * beta. Pin to an exact SDK version if you embed this in a shipping app, and
 * watch the CHANGELOG when upgrading.
 */
class AdminPortalActivity : FronteggBaseActivity() {

    private lateinit var webView: WebView
    private lateinit var loader: ProgressBar

    @Volatile
    private var currentPageUrl: String? = null

    private val bridgeScope = CoroutineScope(DefaultDispatcherProvider.io + SupervisorJob())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: let the WebView extend under the system bars. The root
        // FrameLayout has a solid background drawn under those areas, so there
        // is no transparency where the system overlay sits.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_admin_portal)

        webView = findViewById(R.id.admin_portal_webview)
        loader = findViewById(R.id.admin_portal_loader)
        configureWebView(webView)

        if (savedInstanceState != null) {
            // State-restoration path: the WebView already has its previous
            // page rendered, no bridge needed, no loader needed.
            loader.visibility = View.GONE
            webView.restoreState(savedInstanceState)
        } else {
            loadPortal()
        }
    }

    private fun loadPortal() {
        val portalUrl = buildPortalUrl()
        Log.i(TAG, "AdminPortal: loading $portalUrl")
        webView.loadUrl(portalUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Mobile viewports collapse the portal sidebar into a hamburger drawer.
            // Wide viewport + overview mode is the Android equivalent of iOS's
            // `preferredContentMode = .desktop` and lets the portal render its
            // persistent sidebar.
            useWideViewPort = true
            loadWithOverviewMode = true
            // Pinch-to-zoom + pan-while-zoomed: without builtInZoomControls the
            // user can zoom (because user-scalable=yes) but cannot scroll around
            // the zoomed page. displayZoomControls=false hides the legacy
            // on-screen +/- buttons. setSupportZoom(true) is the default on most
            // devices, but explicit is safer than relying on the default.
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            // Desktop Safari UA — backstop for any UA-sniffing branches in the
            // portal's responsive logic.
            userAgentString = DESKTOP_USER_AGENT
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(AdminPortalBridge(), "FronteggNativeBridge")

        // Resolve the host theme's window background and paint the WebView with
        // it directly. The WebView default is transparent — without this, any
        // moment the page hasn't drawn yet (cold load, route transition, scroll
        // overflow) lets the activity behind us bleed through.
        webView.setBackgroundColor(resolveThemeBackground())
        webView.webViewClient = AdminPortalWebViewClient(
            onFirstPaint = {
                // Hide the loader once the page has rendered (or at least
                // reported finished). Idempotent: subsequent in-page
                // navigations also call onPageFinished but the loader is
                // already gone.
                loader.visibility = View.GONE
            },
            onNavigate = { url -> currentPageUrl = url }
        )
        webView.webChromeClient = object : WebChromeClient() {
            // The portal's X button calls window.close() — bridge to finish().
            override fun onCloseWindow(window: WebView?) {
                Log.d(TAG, "onCloseWindow — finishing")
                finish()
            }
        }
    }

    private fun resolveThemeBackground(): Int {
        val typedValue = TypedValue()
        // android.R.attr.colorBackground falls back to white on stock themes.
        return if (theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
            typedValue.data
        } else {
            0xFFFFFFFF.toInt()
        }
    }

    private fun buildPortalUrl(): String {
        val auth = applicationContext.fronteggAuth
        val url = buildPortalUrl(auth.baseUrl, auth.applicationId)
        Log.d(TAG, "loading ${LogUrlSanitizer.sanitize(url)}")
        return url
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        bridgeScope.cancel()
        // Best-effort cleanup; activity-scoped WebView so leak risk is bounded.
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        } catch (_: Throwable) {
            // best-effort
        }
    }

    private inner class AdminPortalBridge {

        @JavascriptInterface
        fun isMobileSDK(): Boolean = true

        @JavascriptInterface
        fun getTokens(callbackId: String) {
            bridgeScope.launch {
                if (!isTrustedOrigin()) {
                    Log.e(TAG, "AdminPortal: getTokens refused — untrusted origin $currentPageUrl")
                    rejectCallback(callbackId, "untrusted_origin")
                    return@launch
                }
                try {
                    applicationContext.fronteggAuth.refreshTokenAndWait()
                } catch (_: Throwable) {
                }
                val auth = applicationContext.fronteggAuth
                val access = auth.accessToken.value
                val refresh = auth.refreshToken.value
                if (access.isNullOrEmpty() || refresh.isNullOrEmpty()) {
                    rejectCallback(callbackId, "no_tokens")
                    return@launch
                }
                val json = JSONObject()
                    .put("accessToken", access)
                    .put("refreshToken", refresh)
                    .toString()
                resolveCallback(callbackId, json)
            }
        }

        @JavascriptInterface
        fun requestAuthorize(payload: String?) {
            Log.i(TAG, "AdminPortal: portal requested authorize — dismissing to app")
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun closeWindow(reason: String?) {
            Log.i(TAG, "AdminPortal: portal requested closeWindow ($reason)")
            runOnUiThread { finish() }
        }
    }

    private fun isTrustedOrigin(): Boolean {
        val current = originOf(currentPageUrl ?: return false) ?: return false
        val trusted = originOf(applicationContext.fronteggAuth.baseUrl) ?: return false
        return current == trusted
    }

    private fun originOf(url: String): String? = try {
        val u = Uri.parse(url)
        if (u.scheme == null || u.host == null) null else "${u.scheme}://${u.host}:${u.port}"
    } catch (_: Throwable) {
        null
    }

    private fun resolveCallback(callbackId: String, json: String) {
        runOnUiThread {
            webView.evaluateJavascript(
                "(function(){var r=window.FronteggNativeBridgeCallbacks;" +
                    "if(r&&r['$callbackId']){r['$callbackId'].resolve($json);delete r['$callbackId'];}})();",
                null
            )
        }
    }

    private fun rejectCallback(callbackId: String, message: String) {
        val safe = message.replace("\\", "\\\\").replace("'", "\\'")
        runOnUiThread {
            webView.evaluateJavascript(
                "(function(){var r=window.FronteggNativeBridgeCallbacks;" +
                    "if(r&&r['$callbackId']){r['$callbackId'].reject('$safe');delete r['$callbackId'];}})();",
                null
            )
        }
    }

    private class AdminPortalWebViewClient(
        private val onFirstPaint: () -> Unit,
        private val onNavigate: (String?) -> Unit,
    ) : WebViewClient() {
        private var firstPaintReported = false

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            onNavigate(url)
            Log.i(TAG, "AdminPortal: navigation → $url")
            view?.evaluateJavascript("window.FronteggNativeBridgeFunctions = $ADMIN_PORTAL_BRIDGE_FUNCTIONS;", null)
            // Force the viewport meta to a desktop width before the page's CSS
            // and JS evaluate. Material-UI's responsive hooks read
            // window.innerWidth, so this is what actually flips the breakpoint
            // from mobile to desktop.
            view?.evaluateJavascript(VIEWPORT_OVERRIDE_JS, null)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            if (!firstPaintReported) {
                firstPaintReported = true
                onFirstPaint()
            }
            // Re-apply after the page's own JS has had a chance to overwrite the
            // viewport meta. The script is idempotent and installs a
            // MutationObserver, so a second invocation just refreshes its watch.
            view?.evaluateJavascript(VIEWPORT_OVERRIDE_JS, null)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            // SPA route changes (pushState/replaceState/popstate) do not fire
            // onPageStarted/Finished, but they do fire here. The portal swaps
            // routes via React Router; without this hook a back+forward cycle
            // sometimes lands us on a route whose own viewport meta clobbered
            // ours and the layout drops to mobile.
            view?.evaluateJavascript(VIEWPORT_OVERRIDE_JS, null)
        }
    }

    companion object {
        private val TAG = AdminPortalActivity::class.java.simpleName

        /**
         * Build the portal URL. Exposed (internal) for testing.
         *
         * Without `?appId=` the portal renders "Application not found" after login —
         * appId is required when the SDK was configured with an application context.
         */
        @JvmStatic
        internal fun buildPortalUrl(baseUrl: String, applicationId: String?): String {
            val builder = Uri.parse(baseUrl)
                .buildUpon()
                .appendEncodedPath("oauth/portal")
            if (!applicationId.isNullOrEmpty()) {
                builder.appendQueryParameter("appId", applicationId)
            }
            return builder.build().toString()
        }

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

        private const val ADMIN_PORTAL_BRIDGE_FUNCTIONS =
            "{\"getTokens\":true,\"requestAuthorize\":true,\"closeWindow\":true," +
                "\"isMobileSDK\":true,\"useNativeLoader\":true}"

        // Idempotent: safe to evaluate multiple times. Installs a MutationObserver
        // on <head> exactly once (guarded by a window flag) that reverts any
        // attempt by the portal's SPA to replace the viewport meta. This is what
        // keeps the desktop layout sticky across React Router transitions and
        // back/forward navigation — without it, the page renders desktop on
        // first load and silently flips to mobile after a few SPA routes.
        internal const val VIEWPORT_OVERRIDE_JS = """
            (function() {
                var DESIRED = 'width=1024, user-scalable=yes, minimum-scale=0.3, maximum-scale=3';
                var setViewport = function() {
                    var head = document.head || document.documentElement;
                    var existing = document.querySelector('meta[name="viewport"]');
                    if (existing && existing.getAttribute('content') === DESIRED) return;
                    if (existing) { existing.parentNode.removeChild(existing); }
                    var meta = document.createElement('meta');
                    meta.setAttribute('name', 'viewport');
                    meta.setAttribute('content', DESIRED);
                    head.appendChild(meta);
                };
                setViewport();
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', setViewport);
                }
                if (!window.__fronteggAdminPortalViewportObserver) {
                    var head = document.head || document.documentElement;
                    if (head && typeof MutationObserver !== 'undefined') {
                        var observer = new MutationObserver(function() { setViewport(); });
                        observer.observe(head, { childList: true, subtree: true, attributes: true, attributeFilter: ['content', 'name'] });
                        window.__fronteggAdminPortalViewportObserver = observer;
                    }
                }
            })();
        """

        /**
         * Launch the embedded admin portal.
         *
         * **Beta — signature and behavior may change in future minor releases.**
         * See [AdminPortalActivity] for the full beta caveat.
         */
        @JvmStatic
        fun open(activity: Activity) {
            activity.startActivity(Intent(activity, AdminPortalActivity::class.java))
        }
    }
}
