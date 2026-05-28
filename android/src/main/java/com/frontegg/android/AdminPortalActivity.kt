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

/**
 * Embedded Frontegg admin portal — opens `${baseUrl}/oauth/portal?appId=<applicationId>`
 * in a WebView that shares the process-wide [CookieManager] with the SDK's login WebView,
 * so an authenticated user does not see a second login.
 *
 * Cookie strategy: write a synthetic `fe_refresh_<id>=<refreshToken>` cookie into
 * [CookieManager] before loading the portal URL. The auth server accepts the raw
 * refresh-token JWT as the cookie value — this is the same Cookie header the SDK's
 * own HTTP client sends to `/identity/resources/auth/v1/user/token/refresh` and
 * `/identity/resources/auth/v1/logout` (see `Api.kt`, `authorizeWithTokens` /
 * `logout`). Re-using that exact name+value here lets the portal recognize the
 * existing session without any server-side change.
 *
 * The cookie name must match `Api.kt`'s `refreshCookieName()` exactly — strip ONLY
 * the first dash from the clientId (not all dashes). An earlier version stripped
 * all dashes and the server didn't recognize the cookie, causing the portal to
 * bounce to login. The cookie name format is pinned by an SDK-internal test.
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
            bridgeRefreshCookieThenLoad(webView, buildPortalUrl())
        }
    }

    /**
     * Write a synthetic `fe_refresh_<id>=<refreshToken>` cookie into the
     * process-wide [CookieManager] before loading the portal. The portal's
     * server recognizes this cookie name + the raw refresh-token JWT as
     * its session identifier (same format and value the SDK's own HTTP
     * client uses for `/identity/resources/auth/v1/user/token/refresh`
     * and `/identity/resources/auth/v1/logout`).
     *
     * Why bridge at all: on cold start and on browser-login flows
     * (Chrome Custom Tabs uses Chrome's isolated cookie jar), the
     * WebView's CookieManager has no session cookies. Without the
     * bridge the portal renders its own login form and the user logs in
     * twice. The previous attempt to capture server `Set-Cookie` headers
     * over OkHttp didn't work for mobile because the auth backend does
     * not emit them on the mobile `/oauth/token` path — hence the
     * synthetic approach.
     *
     * Async timing: `CookieManager.setCookie(url, value, callback)` is
     * asynchronous on the persistent store. We dispatch [WebView.loadUrl]
     * from inside the callback (via [WebView.post] so we are back on
     * Main, where WebView APIs must be invoked) — calling loadUrl
     * before the callback runs leaves a window where the WebView reads
     * the old (empty) cookie store and the portal bounces to login.
     *
     * Failure modes:
     *  - No refresh token (user not logged in): skip the bridge, load
     *    the portal anyway. Its own login form renders, same as before.
     *  - Malformed baseUrl: skip the bridge, load the portal anyway.
     */
    private fun bridgeRefreshCookieThenLoad(webView: WebView, portalUrl: String) {
        val auth = applicationContext.fronteggAuth
        val cookieHeader = buildRefreshCookieHeader(
            refreshToken = auth.refreshToken.value,
            baseUrl = auth.baseUrl,
            clientId = auth.clientId,
        )

        if (cookieHeader == null) {
            Log.d(TAG, "bridge: no refresh token (or invalid baseUrl) — loading $portalUrl without bridge")
            webView.loadUrl(portalUrl)
            return
        }

        Log.i(TAG, "bridge: writing fe_refresh cookie to WebView store before loading $portalUrl")
        val cookieManager = CookieManager.getInstance()
        // CookieManager.setCookie is keyed by URL — pass auth.baseUrl so the
        // bridged cookie scopes to the same host the portal will request.
        // The callback variant lets us wait for the write to complete before
        // calling loadUrl, avoiding a race where the WebView reads the cookie
        // store before our write has landed.
        cookieManager.setCookie(auth.baseUrl, cookieHeader) { _ ->
            // setCookie callback may fire on a worker thread. WebView APIs
            // must be called on Main — hop back via webView.post.
            webView.post {
                cookieManager.flush()
                webView.loadUrl(portalUrl)
            }
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

        /**
         * Build the cookie name the Frontegg auth server expects for the
         * refresh-token session cookie. Strips ONLY the first dash from the
         * clientId — must match `Api.kt`'s `refreshCookieName()` exactly.
         *
         * Exposed (internal) for tests that pin the format.
         */
        @JvmStatic
        internal fun refreshCookieName(clientId: String): String {
            val stripped = clientId.replaceFirst("-", "")
            return "fe_refresh_$stripped"
        }

        /**
         * Build a `Set-Cookie`-style header value to seed the WebView's
         * [CookieManager] with the user's existing refresh-token session.
         *
         * Returns null when there is no refresh token (user is not logged
         * in — the portal's own login form should render) or the baseUrl
         * is unusable. Callers should fall back to loading the portal URL
         * without the bridge in that case.
         *
         * Exposed (internal) for tests.
         */
        @JvmStatic
        internal fun buildRefreshCookieHeader(
            refreshToken: String?,
            baseUrl: String,
            clientId: String,
        ): String? {
            if (refreshToken.isNullOrEmpty()) return null
            val uri = try {
                Uri.parse(baseUrl)
            } catch (_: Throwable) {
                return null
            }
            val host = uri.host ?: return null
            if (host.isBlank()) return null
            val isSecure = uri.scheme?.lowercase() == "https"
            val name = refreshCookieName(clientId)
            return buildString {
                append(name).append('=').append(refreshToken)
                append("; Path=/")
                if (isSecure) append("; Secure")
            }
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
