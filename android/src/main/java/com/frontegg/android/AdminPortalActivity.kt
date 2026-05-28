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
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.ui.FronteggBaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Embedded Frontegg admin portal — opens `${baseUrl}/oauth/portal?appId=<applicationId>`
 * in a WebView with a server-issued session bootstrapped from the SDK's stored
 * refresh token.
 *
 * The challenge: the portal SPA at `/oauth/portal` calls
 * `/frontegg/oauth/authorize/silent` on mount to validate the session, and that
 * endpoint reads a `fe_refresh_<id>` cookie from the WebView. Users who logged
 * into the SDK via Chrome Custom Tabs have their refresh-token cookie in
 * Chrome's isolated jar — not visible to the WebView's [CookieManager] — so by
 * default the portal sees no cookie, can't validate, and renders its own
 * login form (the "second login" bug).
 *
 * Fix: BEFORE loading `/oauth/portal`, the SDK itself calls
 * `/frontegg/oauth/authorize/silent` via OkHttp. The server's response
 * contains both:
 *   - a JSON body with a freshly-rotated `refresh_token` — we feed it through
 *     to [FronteggAuthService.applyOutOfBandSilentAuthorizeResult] so the SDK
 *     stays in sync, and
 *   - `Set-Cookie: fe_refresh_*; fe_device_*` headers — we mirror these into
 *     [CookieManager] so the WebView starts with server-signed cookies that
 *     are byte-identical to what the portal expects.
 * Then we load `/oauth/portal`. The portal's own silent-authorize on mount
 * will succeed (at most one rotation behind, which the auth backend's grace
 * window covers).
 *
 * Before mirroring, we delete any existing `fe_refresh_*` / `fe_device_*`
 * cookies for the host. Without this cleanup, a stale (already-rotated)
 * cookie from a prior portal open can coexist with the fresh one in a
 * different scope (host-only vs. domain), and per RFC 6265 §5.4 the older
 * one ends up first in the `Cookie:` header — the server reads it and 401s
 * the silent-authorize request. That collision was the actual root cause of
 * Pavel's "first-open-on-iOS / second-open-on-Android logs the user out"
 * reproduction.
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

    // Owned by this activity, cancelled in onDestroy. Used to dispatch the
    // silent-authorize round-trip on Dispatchers.IO without blocking Main.
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
            // page rendered, no bridge needed, no loader needed.
            loader.visibility = View.GONE
            webView.restoreState(savedInstanceState)
        } else {
            silentAuthorizeThenLoad(webView, buildPortalUrl())
        }
    }

    /**
     * Bootstrap a server-issued session via silent-authorize, then load
     * `/oauth/portal` in the WebView.
     *
     * Sequence:
     *   1. Clear any stale `fe_refresh_*` / `fe_device_*` cookies for this
     *      host from [CookieManager]. These would otherwise coexist with the
     *      fresh server-issued cookies we're about to mirror, and the older
     *      stale value would end up sorted first in the request's `Cookie:`
     *      header (RFC 6265 §5.4) → server reads stale UUID → 401.
     *   2. POST `/frontegg/oauth/authorize/silent` via [com.frontegg.android.services.Api.silentAuthorize]
     *      with the SDK's current refresh-token UUID as the cookie. This is
     *      the exact same call the portal's React app will make on mount —
     *      doing it OkHttp-side first means by the time the WebView loads
     *      `/oauth/portal`, [CookieManager] already has the rotated cookies
     *      the portal expects.
     *   3. On success: mirror the response's `Set-Cookie` headers into
     *      [CookieManager], then update the SDK's stored tokens via
     *      [FronteggAuthService.applyOutOfBandSilentAuthorizeResult] so the
     *      SDK stays one-rotation-in-sync with what the server now considers
     *      the user's valid session.
     *   4. Regardless of success/failure, load `/oauth/portal`. If silent-
     *      authorize failed, the portal will render its own login form —
     *      same fallback as before any of this existed.
     *
     * Runs the network call off [Dispatchers.Main] (silent-authorize is a
     * synchronous OkHttp request) and hops back to Main for the WebView call
     * (WebView APIs are main-thread only).
     */
    private fun silentAuthorizeThenLoad(webView: WebView, portalUrl: String) {
        val auth = applicationContext.fronteggAuth
        val baseUrl = auth.baseUrl
        val refreshToken = auth.refreshToken.value

        if (refreshToken.isNullOrEmpty()) {
            Log.d(TAG, "silent-authorize: no refresh token — loading $portalUrl without bridge (portal will render its own login)")
            webView.loadUrl(portalUrl)
            return
        }

        val host = try {
            Uri.parse(baseUrl).host
        } catch (_: Throwable) {
            null
        }
        if (host.isNullOrEmpty()) {
            Log.w(TAG, "silent-authorize: baseUrl=$baseUrl has no host — loading $portalUrl without bridge")
            webView.loadUrl(portalUrl)
            return
        }

        activityScope.launch {
            // Step 1 — drop any stale fe_refresh_* / fe_device_* cookies the
            // server might have set in a prior portal open. RFC 6265 §5.4
            // would otherwise sort the older one first in the Cookie header.
            val cleared = clearStaleSessionCookies(baseUrl)
            Log.i(TAG, "silent-authorize: cleared $cleared stale fe_refresh_*/fe_device_* cookie(s) before bridge")

            // Step 2 — silent-authorize on IO. The SDK's HTTP client is
            // synchronous OkHttp; calling it on Main would block the UI.
            val outcome = try {
                withContext(Dispatchers.IO) {
                    val service = auth as? FronteggAuthService
                        ?: return@withContext SilentAuthorizeOutcome.NoService
                    val response = service.silentAuthorizeForAdminPortal(refreshToken)
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            return@withContext SilentAuthorizeOutcome.HttpError(resp.code)
                        }
                        // Body is intentionally ignored — see comment above
                        // step 3 for why we don't sync credentialManager.
                        // Filter Set-Cookie to fe_refresh_*/fe_device_* so
                        // unrelated server cookies don't pollute the WebView
                        // store.
                        val setCookieHeaders = resp.headers("set-cookie").filter { header ->
                            val name = header.substringBefore("=").trim()
                            name.startsWith("fe_refresh_") || name.startsWith("fe_device_")
                        }
                        SilentAuthorizeOutcome.Success(setCookieHeaders)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "silent-authorize: request threw — loading portal anyway", t)
                SilentAuthorizeOutcome.Threw(t)
            }

            // Step 3 — mirror cookies into CookieManager. CookieManager.setCookie
            // writes are fast in-memory ops; flush() at the end forces
            // cross-process persistence.
            //
            // We deliberately DO NOT update the SDK's credentialManager from
            // the response body. Empirical testing (curl against staging)
            // shows the body's `refresh_token` field from silent-authorize is
            // a different identifier than the Set-Cookie value, and is
            // invalid for the SDK's existing refresh path. Trying to "keep
            // the SDK in sync" by storing either value would break the SDK's
            // existing refresh flow for at least some auth-flow combinations.
            // If customer reports confirm a follow-up auth failure manifests
            // in practice, the fix is to call applyOutOfBandSilentAuthorizeResult
            // with the parsed Set-Cookie value (not the body field) here.
            if (outcome is SilentAuthorizeOutcome.Success) {
                val cookieManager = CookieManager.getInstance()
                for (header in outcome.setCookieHeaders) {
                    cookieManager.setCookie(baseUrl, header)
                }
                cookieManager.flush()
                Log.i(TAG, "silent-authorize: mirrored ${outcome.setCookieHeaders.size} server cookie(s) into CookieManager")
            } else {
                Log.i(TAG, "silent-authorize: did not bootstrap session ($outcome) — portal will render its own login if no usable cookies remain")
            }

            // Step 4 — load the portal. WebView APIs are main-thread only;
            // activityScope is Main, but be explicit.
            Log.i(TAG, "silent-authorize: loading $portalUrl")
            webView.loadUrl(portalUrl)
        }
    }

    /**
     * Outcome of the silent-authorize round-trip. Kept inside the activity
     * (sealed class, not exposed) because it's only meaningful for this
     * specific bootstrap flow.
     */
    private sealed class SilentAuthorizeOutcome {
        data class Success(
            val setCookieHeaders: List<String>,
        ) : SilentAuthorizeOutcome()

        data class HttpError(val statusCode: Int) : SilentAuthorizeOutcome() {
            override fun toString(): String = "HTTP $statusCode"
        }

        data class Threw(val cause: Throwable) : SilentAuthorizeOutcome() {
            override fun toString(): String = "threw ${cause.javaClass.simpleName}: ${cause.message}"
        }

        object NoService : SilentAuthorizeOutcome() {
            override fun toString(): String = "FronteggAuth is not a FronteggAuthService (unexpected)"
        }
    }

    /**
     * Remove every existing `fe_refresh_` / `fe_device_` cookie scoped to
     * the given baseUrl's host from [CookieManager]. Returns the count removed.
     *
     * Implementation: CookieManager doesn't expose a "list all cookies" API,
     * but we know the cookies have a deterministic name prefix. The robust
     * approach is to overwrite each candidate name with an expired value
     * (Max-Age=0), which causes CookieManager to drop it.
     *
     * Since we don't know the clientId/userId suffix without parsing
     * existing cookies, we use a different approach: fetch the current
     * Cookie header for the URL (which lists everything the server would
     * see), find every name starting with `fe_refresh_` or `fe_device_`,
     * and overwrite each with Max-Age=0 in both host-only and domain
     * scope (cookies may be stored in either scope; both must go).
     */
    private fun clearStaleSessionCookies(baseUrl: String): Int {
        val cookieManager = CookieManager.getInstance()
        val existing = cookieManager.getCookie(baseUrl) ?: return 0
        // getCookie returns a `; `-separated `name=value; name=value` string.
        val staleNames = existing.split(";")
            .map { it.trim() }
            .mapNotNull { entry ->
                val name = entry.substringBefore("=").trim()
                if (name.startsWith("fe_refresh_") || name.startsWith("fe_device_")) name else null
            }
            .toSet()

        for (name in staleNames) {
            // Setting Max-Age=0 (or Expires in the past) tells CookieManager
            // to drop the cookie. We do this for BOTH host-only and
            // domain-scoped versions because the cookie could be stored in
            // either scope — the server's `Set-Cookie: Domain=...` makes it
            // a domain cookie; a prior bridge's synthetic write made it
            // host-only. Both must go.
            cookieManager.setCookie(baseUrl, "$name=; Path=/; Max-Age=0")
            val host = try { Uri.parse(baseUrl).host } catch (_: Throwable) { null }
            if (host != null) {
                cookieManager.setCookie(baseUrl, "$name=; Path=/; Domain=$host; Max-Age=0")
            }
        }
        if (staleNames.isNotEmpty()) {
            cookieManager.flush()
        }
        return staleNames.size
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
        // Cancel any in-flight silent-authorize so a slow network response
        // can't try to call loadUrl on a destroyed WebView.
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
