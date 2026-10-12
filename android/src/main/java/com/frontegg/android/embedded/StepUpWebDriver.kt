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
 *   1. seeds the box's step-up flag (`SHOULD_STEP_UP`) in `localStorage`,
 *   2. rewrites the URL to the hosted root, whose page opens the box's step-up route in-page
 *      only once the box has authenticated from the native session, so the step-up request
 *      carries a token,
 *   3. points the box's after-auth redirect (`FRONTEGG_AFTER_AUTH_REDIRECT_URL`) at the
 *      original authorize URL, which the box strips to its same-origin path and routes to
 *      in-page (not a full navigation), and
 *   4. saves the OAuth params the skipped hosted prelogin page would have saved
 *      (`redirect_uri`, `state`, `acr_values`, `max_age`) in localStorage and sessionStorage.
 *
 * When step-up completes, the box routes in-page to the hosted page, which reads those
 * params, posts the stepped-up session to postlogin and redirects to `redirect_uri` with a
 * stepped-up `code` that the OAuth callback interception in [FronteggWebClient] exchanges.
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
          function decodedOrRaw(value) {
            try { return decodeURIComponent(value); } catch (e) { return value; }
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
            ls.setItem('FRONTEGG_AFTER_AUTH_REDIRECT_URL', $afterAuth);

            var hostedParams = {
              FRONTEGG_OAUTH_STEP_UP_ACR_VALUES: params.get('acr_values'),
              FRONTEGG_OAUTH_STEP_UP_MAX_AGE: maxAge
            };
            var redirectUri = params.get('redirect_uri');
            var state = params.get('state');
            if (redirectUri && state) {
              hostedParams.FRONTEGG_OAUTH_REDIRECT_AFTER_LOGIN = decodedOrRaw(redirectUri);
              hostedParams.FRONTEGG_OAUTH_STATE_AFTER_LOGIN = state;
            }
            try {
              [window.localStorage, window.sessionStorage].forEach(function (storage) {
                Object.keys(hostedParams).forEach(function (key) {
                  if (hostedParams[key]) { storage.setItem(key, hostedParams[key]); }
                });
              });
            } catch (e) {
              report('saving hosted params failed: ' + (e && e.message ? e.message : e));
            }

            // Hosted-login root, e.g. '/oauth/' from '/oauth/prelogin'.
            var hostedRoot = loc.pathname.substring(0, loc.pathname.lastIndexOf('/') + 1);
            window.history.replaceState(null, '', loc.origin + hostedRoot);
            report('routed to ' + hostedRoot + ' (maxAge=' + maxAge + ')');
          } catch (e) {
            report('error: ' + (e && e.message ? e.message : e));
          }
        })();
        """.trimIndent()
    }
}
