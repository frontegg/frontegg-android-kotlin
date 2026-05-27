package com.frontegg.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import com.frontegg.android.ui.FronteggBaseActivity

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: let the WebView extend under the system bars. The root
        // FrameLayout has a solid background drawn under those areas, so there
        // is no transparency where the system overlay sits.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_admin_portal)

        webView = findViewById(R.id.admin_portal_webview)
        configureWebView(webView)

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            bridgeRefreshCookieThenLoad(webView, buildPortalUrl())
        }
    }

    /**
     * If the SDK is authenticated, write its refresh token into the
     * process-wide [CookieManager] as `fe_refresh_<appId-or-clientId>` and
     * THEN load the portal URL. Without this bridge, users who logged in via
     * Chrome Custom Tabs (the Android equivalent of iOS `ASWebAuthenticationSession`)
     * are forced to log in a second time — Chrome Custom Tabs put cookies in
     * Chrome's per-app cookie jar, which is not visible to this WebView.
     *
     * Users who logged in via the SDK's embedded WebView already have these
     * cookies, because that flow shares the same process-wide CookieManager
     * with the portal. The bridge is a no-op for that case (the cookie value
     * we'd write matches what's already there).
     *
     * Timing matters: [CookieManager.setCookie] is asynchronous — the
     * completion callback fires after the in-memory write lands. If we call
     * `webView.loadUrl(...)` synchronously after `setCookie`, the HTTP GET
     * for `/oauth/portal` can fire BEFORE the cookie is in the store, the
     * server sees no session and 302s to `/oauth/account/login`, and the
     * user sees a re-login prompt (the exact bug this method exists to fix).
     * So [loadUrl] is called from inside the setCookie callback, posted back
     * to the main thread (the callback fires on an internal CookieManager
     * thread; WebView calls must come from the main thread).
     *
     * No-op when the SDK has no refresh token to bridge — the portal is
     * loaded directly and falls back to its own login form, the existing
     * behavior before this bridge.
     */
    private fun bridgeRefreshCookieThenLoad(webView: WebView, portalUrl: String) {
        val auth = applicationContext.fronteggAuth
        val cookie = buildRefreshCookieValue(
            refreshToken = auth.refreshToken.value,
            baseUrl = auth.baseUrl,
            clientId = auth.clientId,
            applicationId = auth.applicationId
        )

        if (cookie == null) {
            // No refresh token to bridge — load directly. Portal will render
            // its own login form if no existing web cookies are present.
            Log.d(TAG, "loading $portalUrl (no refresh token to bridge)")
            webView.loadUrl(portalUrl)
            return
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(cookie.urlForSetCookie, cookie.headerValue) {
            // Completion fires on a CookieManager-internal thread. Flush the
            // in-memory cookie to persistent storage, then post the WebView
            // load to the main thread.
            cookieManager.flush()
            webView.post {
                Log.i(TAG, "AdminPortal: bridged refresh cookie ${cookie.name}, loading $portalUrl")
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
        webView.webViewClient = AdminPortalWebViewClient()
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

    private class AdminPortalWebViewClient : WebViewClient() {
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
         * Builds the Frontegg backend refresh-token cookie name. Mirrors the
         * Next.js SDK rule (frontegg-nextjs CookieManager.refreshTokenKey):
         * prefer `appId` when present (multi-app workspaces), fall back to
         * `clientId`. Dashes are stripped to match the auth-server's expected
         * cookie name format.
         */
        @JvmStatic
        internal fun refreshCookieName(clientId: String, applicationId: String?): String {
            return if (!applicationId.isNullOrEmpty()) {
                "fe_refresh_${applicationId.replace("-", "")}"
            } else {
                "fe_refresh_${clientId.replace("-", "")}"
            }
        }

        /**
         * Bundled output of [buildRefreshCookieValue]: the cookie name, the
         * `Set-Cookie` header value to hand to [CookieManager.setCookie], and
         * the URL that scopes the cookie. Keeping them together lets tests
         * verify all three without re-deriving the URL.
         */
        internal data class RefreshCookie(
            val name: String,
            val headerValue: String,
            val urlForSetCookie: String,
        )

        /**
         * Constructs the cookie payload that lets the embedded portal recognize
         * the SDK's existing authenticated session.
         *
         * Returns null when there is no refresh token to bridge (user is not
         * logged in) or when the base URL is malformed. In both cases the
         * portal falls back to its own login form — same as before this bridge.
         *
         * The `Set-Cookie`-style header includes `Path=/` and `Secure` (when
         * baseUrl is HTTPS). [CookieManager.setCookie] requires the URL form,
         * not domain form, so we return the baseUrl as the scoping URL — that
         * yields a host-scoped cookie which is what we want.
         */
        @JvmStatic
        internal fun buildRefreshCookieValue(
            refreshToken: String?,
            baseUrl: String,
            clientId: String,
            applicationId: String?,
        ): RefreshCookie? {
            if (refreshToken.isNullOrEmpty()) return null
            val parsed = runCatching { Uri.parse(baseUrl) }.getOrNull() ?: return null
            val scheme = parsed.scheme ?: return null
            if (parsed.host.isNullOrEmpty()) return null

            val name = refreshCookieName(clientId = clientId, applicationId = applicationId)
            val isSecure = scheme.equals("https", ignoreCase = true)

            val header = buildString {
                append(name).append('=').append(refreshToken)
                append("; Path=/")
                if (isSecure) {
                    append("; Secure")
                }
            }

            // CookieManager.setCookie(url, value) scopes the cookie to the URL's
            // host/scheme. Use the bare baseUrl (no extra path) so the cookie's
            // Domain is the host, not a subpath.
            val scopingUrl = Uri.Builder()
                .scheme(scheme)
                .authority(parsed.host)
                .build()
                .toString()

            return RefreshCookie(
                name = name,
                headerValue = header,
                urlForSetCookie = scopingUrl,
            )
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
