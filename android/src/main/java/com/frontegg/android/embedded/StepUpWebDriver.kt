package com.frontegg.android.embedded

import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * FR-24939 — native-side step-up routing for the embedded login WebView.
 *
 * The hosted-login box renders its step-up (MFA) page only when the WebView is at the
 * step-up route with the native token bridge present. A native step-up authorize URL
 * (`acr_values` + `max_age`), however, bootstraps the box on its prelogin path and never
 * navigates to that route on its own — it is silently token-refreshed and the box renders
 * a blank page instead of the MFA challenge.
 *
 * This driver bridges that gap from the native side. While presenting a step-up flow it
 * injects a document-start script that, before the box reads the document:
 *   1. seeds the box's step-up `localStorage` contract (`SHOULD_STEP_UP`,
 *      `FRONTEGG_OAUTH_STEP_UP_MAX_AGE`),
 *   2. rewrites the URL to the box's step-up route so it renders the MFA challenge, and
 *   3. points the box's after-auth redirect (`FRONTEGG_AFTER_AUTH_REDIRECT_URL`) back at
 *      the original authorize URL.
 *
 * When step-up completes, the box performs a full navigation to that authorize URL — now
 * with an elevated session — which yields an elevated `code` that the existing OAuth
 * callback interception in [FronteggWebClient] exchanges into a stepped-up token. No new
 * native token-capture path is required.
 *
 * Android counterpart of iOS `StepUpWebDriver` (WKUserScript at `.atDocumentStart`).
 */
object StepUpWebDriver {
    private val TAG = StepUpWebDriver::class.java.simpleName

    /** JS object name the driver posts its native log line to (see [addWebMessageListener]). */
    private const val MESSAGE_CHANNEL = "FronteggStepUpDriver"

    /**
     * Registers the document-start driver script for a step-up authorize URL. No-op (with a
     * warning) on legacy WebViews without [WebViewFeature.DOCUMENT_START_SCRIPT] — the same
     * capability gate the Admin Portal bridge uses.
     */
    fun install(webView: WebView, authorizeUrl: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.w(TAG, "DOCUMENT_START_SCRIPT unsupported; step-up driver not installed")
            return
        }

        // Surface the driver's outcome into logcat — the box JS console is invisible in
        // release (webContentsDebugging is off), so this makes the routing observable.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView, MESSAGE_CHANNEL, setOf("*")
            ) { _: WebView, message: WebMessageCompat, _: Uri, _: Boolean, _: JavaScriptReplyProxy ->
                Log.i(TAG, "[step-up] ${message.data}")
            }
        }

        WebViewCompat.addDocumentStartJavaScript(webView, script(authorizeUrl), setOf("*"))
    }

    /** Builds the document-start script for the given original step-up authorize URL. */
    fun script(authorizeUrl: String): String {
        // JSON-quote yields a safely-escaped JS string literal (quotes included).
        val afterAuth = JSONObject.quote(authorizeUrl)
        return """
        (function () {
          function report(m) {
            try { $MESSAGE_CHANNEL.postMessage(String(m)); } catch (e) {}
          }
          try {
            var loc = window.location;
            // Already on the step-up route (e.g. the box's own redirect) — nothing to do.
            if (loc.pathname.indexOf('/account/step-up') !== -1) { return; }
            var params = new URLSearchParams(loc.search);
            // Only act on the step-up authorize/prelogin document.
            if (!params.get('acr_values')) { return; }

            var rawMaxAge = params.get('max_age');
            var maxAge = null;
            if (rawMaxAge != null) {
              var parsed = parseInt(parseFloat(rawMaxAge), 10);
              if (!isNaN(parsed)) { maxAge = String(parsed); }
            }

            var ls = window.localStorage;
            ls.setItem('SHOULD_STEP_UP', 'true');
            if (maxAge) { ls.setItem('FRONTEGG_OAUTH_STEP_UP_MAX_AGE', maxAge); }
            // Absolute http(s) URL => the box treats the post-MFA redirect as a full
            // navigation, re-hitting /oauth/authorize with the now-elevated session so a
            // stepped-up code is issued and captured by the native OAuth callback.
            ls.setItem('FRONTEGG_AFTER_AUTH_REDIRECT_URL', $afterAuth);

            // Hosted-login basename = current path minus its last segment
            // (e.g. '/oauth' from '/oauth/prelogin'); step-up route is
            // '<basename>/account/step-up'. Preserve the existing query params and add
            // the 'maxAge' param the step-up page reads (distinct from OAuth 'max_age').
            var path = loc.pathname;
            var basename = path.substring(0, path.lastIndexOf('/'));
            var search = loc.search || '';
            if (maxAge) { search += (search ? '&' : '?') + 'maxAge=' + maxAge; }
            var target = basename + '/account/step-up' + search;

            window.history.replaceState(null, '', loc.origin + target);
            report('routed to ' + basename + '/account/step-up (maxAge=' + maxAge + ')');
          } catch (e) {
            report('error: ' + (e && e.message ? e.message : e));
          }
        })();
        """.trimIndent()
    }
}
