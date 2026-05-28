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
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.core.view.WindowCompat
import com.frontegg.android.ui.FronteggBaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Embedded Frontegg admin portal — opens `${baseUrl}/oauth/portal?appId=<applicationId>`
 * in a WebView that shares the process-wide [CookieManager] with the SDK's login WebView,
 * so an authenticated user does not see a second login.
 *
 * Cookie strategy: pure pass-through. Cookies the SDK's login WebView wrote during
 * `/oauth/account/social/success` are already visible here — do not synthesize or
 * override them.
 *
 * **Beta — API may change in future minor releases.** The class name, [open] entry
 * point, presentation style (currently a full-screen [Activity]), and Manifest
 * registration are all subject to revision while this feature is in beta. Pin to
 * an exact SDK version if you embed this in a shipping app, and watch the
 * CHANGELOG when upgrading.
 */
class AdminPortalActivity : FronteggBaseActivity() {

    private lateinit var webView: WebView
    private lateinit var loader: ProgressBar

    // Owned by this activity, cancelled in onDestroy. Used to await a
    // forced token refresh before loading the portal URL — see
    // refreshSessionThenLoad for the rationale.
    private val activityScope: CoroutineScope = MainScope()

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
            // page rendered, no refresh needed, no loader needed.
            loader.visibility = View.GONE
            webView.restoreState(savedInstanceState)
        } else {
            refreshSessionThenLoad(webView, buildPortalUrl())
        }
    }

    /**
     * Force a token refresh BEFORE loading the portal. This is the mechanism
     * that gets a valid session cookie into the process-wide [CookieManager]:
     *
     *   1. `refreshTokenIfNeeded()` → `Api.refreshToken()` → POST `/oauth/token`
     *   2. The Frontegg auth backend's response includes
     *      `Set-Cookie: fe_refresh_<id>=<server-signed-value>` …
     *   3. `Api.mirrorFronteggCookiesToWebViewStore()` captures every
     *      `fe_*` cookie from that response and writes it into
     *      `CookieManager.getInstance()` — the same store this WebView uses.
     *   4. `webView.loadUrl(portalUrl)` then carries the cookie on its request,
     *      the portal recognizes the session, no second login.
     *
     * Why force a refresh here instead of relying on the SDK's natural
     * refresh: on cold start with a non-expired access token, the SDK
     * may not call `/oauth/token` at all before the user opens the portal.
     * Without a refresh, the WebView has no session cookies for browser-login
     * users (Chrome Custom Tabs cookies live in Chrome's isolated jar) and
     * the portal bounces to `/oauth/account/login`.
     *
     * Failure modes:
     *  - User not logged in: skip the refresh, load the portal anyway —
     *    its own login form renders, same as before any of this.
     *  - Refresh fails (offline, server error): load the portal anyway,
     *    same fallback.
     *  - Refresh succeeds but server didn't emit cookies: load the portal
     *    anyway, which then bounces to login (the original bug, which we
     *    now treat as a server-side issue to investigate, not a client
     *    bug to work around).
     */
    private fun refreshSessionThenLoad(webView: WebView, portalUrl: String) {
        val auth = applicationContext.fronteggAuth
        if (auth.refreshToken.value.isNullOrEmpty()) {
            Log.d(TAG, "loading $portalUrl (no refresh token; portal will render its own login)")
            loader.visibility = View.GONE
            webView.loadUrl(portalUrl)
            return
        }

        Log.i(TAG, "AdminPortal: awaiting forced refresh before load to populate session cookies")
        activityScope.launch {
            try {
                // force=true bypasses the "access token still valid" check in
                // refreshIdempotent. Without it, on cold start with a still-valid
                // access token the SDK short-circuits and never hits /oauth/token,
                // so the server never emits Set-Cookie and we have no session
                // cookie to mirror into the WebView store.
                auth.refreshTokenAndWait(force = true)
            } catch (e: Throwable) {
                // Refresh failed (offline, server error, etc.) — load the
                // portal anyway; it'll render its own login form, same
                // fallback as before.
                Log.w(TAG, "AdminPortal: pre-load refresh threw — loading portal anyway", e)
            }
            // refreshTokenAndWait suspends until Api.refreshToken has
            // returned AND mirrorFronteggCookiesToWebViewStore has written
            // any captured cookies into CookieManager. The portal request
            // now carries those cookies.
            Log.i(TAG, "AdminPortal: loading $portalUrl after refresh")
            webView.loadUrl(portalUrl)
            // Loader stays visible until the page reports it has rendered
            // (AdminPortalWebViewClient.onPageFinished). The white-WebView
            // window is from the moment we leave HomeFragment to the moment
            // the page actually paints — a few seconds on cold start.
        }
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

        // Process-wide CookieManager already holds the SDK's cookies; just enable
        // third-party cookies for this WebView so embedded portal frames work.
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

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
            }
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
        Log.d(TAG, "loading $url")
        return url
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any in-flight refresh so a slow network response can't try
        // to call loadUrl on a destroyed WebView.
        activityScope.cancel()
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

    private class AdminPortalWebViewClient(
        private val onFirstPaint: () -> Unit,
    ) : WebViewClient() {
        private var firstPaintReported = false

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
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
