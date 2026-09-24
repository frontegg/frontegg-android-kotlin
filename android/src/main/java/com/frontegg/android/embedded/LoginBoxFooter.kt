package com.frontegg.android.embedded

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.frontegg.android.services.FronteggInnerStorage
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Host-supplied content appended below the embedded login box's card, on its login
 * screen only.
 *
 * The box's configuration has no slot for content below the card — the React SDK exposes
 * a `boxFooter` render prop for exactly this, which has no equivalent when the box is
 * served into a WebView. Unlike [LoginBoxCustomization], which hands values to the box's
 * own configuration, the footer is built in the DOM — but from a *structured* payload
 * (text and label/URL pairs) rather than host-supplied HTML, and anchored on
 * `[data-test-id="root-element"]`, a test id that is part of the box's test contract
 * rather than its generated styling. No host string is ever interpreted as markup.
 *
 * Android counterpart of iOS `LoginBoxFooter` (WKUserScript at `.atDocumentStart`).
 */
object LoginBoxFooter {
    private val TAG = LoginBoxFooter::class.java.simpleName

    /** Schemes that can execute script or read local data; never admissible. */
    private val DENIED_SCHEMES = setOf(
        "javascript", "data", "file", "blob", "about", "vbscript", "intent", "content"
    )

    /**
     * Registers the footer script, if the host set a usable footer. Scoped to the auth
     * origin like [LoginBoxCustomization.install], and gated on the same
     * [LoginBoxCustomization.isSupported] capability.
     */
    fun install(webView: WebView, storage: FronteggInnerStorage = FronteggInnerStorage()) {
        val footer = storage.loginBoxFooter ?: return
        val script = script(footer) { scheme -> hostAppHandles(webView.context, scheme) }
        if (script == null) {
            Log.w(TAG, "loginBoxFooter was set but contains no usable rows; no footer rendered")
            return
        }

        if (!LoginBoxCustomization.isSupported()) {
            Log.e(TAG, "DOCUMENT_START_SCRIPT unsupported; login box footer not installed")
            return
        }

        val origin = LoginBoxCustomization.authOrigin(storage.baseUrl)
        if (origin == null) {
            Log.e(TAG, "baseUrl has no usable origin; login box footer not installed")
            return
        }

        WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(origin))
    }

    /**
     * Normalizes a host-supplied footer payload, dropping anything unsafe, or `null` when
     * nothing usable survives.
     *
     * A segment whose URL fails the scheme check degrades to plain text rather than being
     * dropped: the footer's usual job is a legal attribution, and a sentence missing a
     * fragment reads as a bug, whereas an unlinked label still says what it needs to say.
     *
     * [handlesScheme] is injected rather than resolved here because, unlike iOS
     * (`CFBundleURLTypes`), Android exposes no way to ENUMERATE an app's own registered
     * schemes — only to ask whether anything handles a given one. See [hostAppHandles].
     */
    internal fun sanitizedFooter(
        footer: Map<String, Any?>?,
        handlesScheme: (String) -> Boolean = { false }
    ): JSONObject? {
        @Suppress("UNCHECKED_CAST")
        val rows = footer?.get("rows") as? List<Map<String, Any?>> ?: return null
        if (rows.isEmpty()) return null

        val sanitizedRows = JSONArray()

        for (row in rows) {
            @Suppress("UNCHECKED_CAST")
            val segments = row["segments"] as? List<Map<String, Any?>> ?: continue

            val sanitizedSegments = JSONArray()
            for (segment in segments) {
                val text = segment["text"] as? String
                if (!text.isNullOrEmpty()) {
                    sanitizedSegments.put(JSONObject().put("text", text))
                    continue
                }

                val label = segment["label"] as? String
                if (label.isNullOrEmpty()) continue

                val safeUrl = sanitizedLinkUrl(segment["url"] as? String, handlesScheme)
                if (safeUrl != null) {
                    sanitizedSegments.put(
                        JSONObject().put("label", label).put("url", safeUrl)
                    )
                } else {
                    sanitizedSegments.put(JSONObject().put("text", label))
                }
            }

            if (sanitizedSegments.length() == 0) continue

            val variant = if (row["variant"] as? String == "fine") "fine" else "body"
            sanitizedRows.put(
                JSONObject().put("variant", variant).put("segments", sanitizedSegments)
            )
        }

        if (sanitizedRows.length() == 0) return null

        return JSONObject()
            .put("hideCaptchaBadge", footer["hideCaptchaBadge"] as? Boolean ?: false)
            .put("rows", sanitizedRows)
    }

    /**
     * Accepts an absolute `http(s)` URL, or a URL on a scheme the host app itself
     * declares an intent filter for.
     *
     * The value becomes an `href`, so anything else — `javascript:` above all — is
     * dropped rather than injected. A host app is trusted, but this value can originate
     * in remote configuration on its side, and the cost of the check is nothing.
     *
     * The app-scheme case is what makes a hand-off possible: a host that wants its
     * sign-up flow presented in its own browser rather than inside this WebView points a
     * footer link at its own scheme, and [FronteggWebClient] opens it and dismisses
     * the box.
     */
    internal fun sanitizedLinkUrl(
        url: String?,
        handlesScheme: (String) -> Boolean = { false }
    ): String? {
        if (url.isNullOrEmpty()) return null
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null

        if (scheme == "http" || scheme == "https") {
            if (uri.host.isNullOrEmpty()) return null
            return url
        }

        // Denied ahead of the predicate so the guard that actually matters does not
        // depend on it: `handlesScheme` is supplied by the caller, and a script-executing
        // scheme must be impossible to admit even through a wrong one.
        if (scheme in DENIED_SCHEMES) return null

        return if (handlesScheme(scheme)) url else null
    }

    /**
     * The `http(s)` footer URLs, which must be opened outside the login box.
     *
     * The box's WebView has no navigation chrome, so letting an attribution link load in
     * place strands the user with no way back. [FronteggWebClient] consults this
     * exact-match set and hands those URLs to a browser instead — an allowlist rather
     * than a general "off-origin" rule, because the box legitimately navigates to social
     * identity providers.
     */
    internal fun footerExternalUrls(
        footer: Map<String, Any?>?,
        handlesScheme: (String) -> Boolean = { false }
    ): Set<String> {
        val sanitized = sanitizedFooter(footer, handlesScheme) ?: return emptySet()
        val rows = sanitized.optJSONArray("rows") ?: return emptySet()

        val urls = mutableSetOf<String>()
        for (i in 0 until rows.length()) {
            val segments = rows.optJSONObject(i)?.optJSONArray("segments") ?: continue
            for (j in 0 until segments.length()) {
                val url = segments.optJSONObject(j)?.optString("url").orEmpty()
                if (url.isEmpty()) continue
                val scheme = try {
                    URI(url).scheme?.lowercase()
                } catch (e: Exception) {
                    null
                }
                if (scheme == "http" || scheme == "https") urls.add(url)
            }
        }
        return urls
    }

    /**
     * Whether the HOST APP — not some other installed app — declares a browsable VIEW
     * filter for [scheme].
     *
     * Restricted to the host's own package deliberately: any app can claim a scheme, and
     * accepting a third party's would let remote configuration bounce the user out to it.
     */
    internal fun hostAppHandles(context: Context, scheme: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            context.packageManager
                .queryIntentActivities(intent, 0)
                .any { it.activityInfo?.packageName == context.packageName }
        } catch (e: Exception) {
            Log.w(TAG, "could not resolve handlers for scheme '$scheme'", e)
            false
        }
    }

    /**
     * Builds the document-start script, or `null` when the footer is absent or has no
     * usable rows so callers can skip injecting entirely.
     */
    fun script(
        footer: Map<String, Any?>?,
        handlesScheme: (String) -> Boolean = { false }
    ): String? {
        val sanitized = sanitizedFooter(footer, handlesScheme) ?: return null

        // U+2028/U+2029 are valid inside JSON but terminate a line in JavaScript source.
        val footerJson = sanitized.toString()
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")

        return """
        (function () {
          if (window.__fronteggLoginBoxFooterInstalled) { return; }
          window.__fronteggLoginBoxFooterInstalled = true;

          var FOOTER = $footerJson;
          var FOOTER_ID = 'frontegg-login-box-footer';
          var ROOT_SELECTOR = '[data-test-id="root-element"]';
          // The footer follows the login screen only, mirroring the React SDK where
          // `boxFooter` is configured under `login` and the other screens
          // (forgot-password, MFA, signup) carry their own. Keyed on the login title's
          // test id, which is present on that screen and no other.
          var LOGIN_MARKER = '[data-test-id="login-page-title"]';

          // ---- footer --------------------------------------------------------
          // Google's badge is rendered into the light DOM at body level, so a
          // document-level stylesheet reaches it even though the box itself lives in a
          // shadow root. Hiding it is only permitted alongside the visible attribution
          // the host supplies in `rows`.
          function hideCaptchaBadge() {
            if (!FOOTER.hideCaptchaBadge) { return; }
            if (document.getElementById(FOOTER_ID + '-badge-style')) { return; }
            var head = document.head || document.documentElement;
            if (!head) { return; }
            var style = document.createElement('style');
            style.id = FOOTER_ID + '-badge-style';
            style.textContent = '.grecaptcha-badge{visibility:hidden!important;}';
            head.appendChild(style);
          }

          function boxShadowRoot() {
            var found = null;
            var all = document.querySelectorAll('*');
            for (var i = 0; i < all.length; i++) {
              var host = all[i];
              if (host.shadowRoot && host.shadowRoot.querySelector(ROOT_SELECTOR)) {
                found = host.shadowRoot;
                break;
              }
            }
            return found;
          }

          // The box nests several full-height centring wrappers inside [root-element]
          // before the card. Descend while a wrapper has exactly one child that still
          // fills it; the first child that does NOT fill its parent is the card, so we
          // stop on the card's parent and append there. The footer then flows directly
          // under the card, and that column's justify-content:center re-centres the pair.
          //
          // Deliberately geometric rather than structural: it reads the layout the box
          // actually produced instead of hard-coding a depth, so an added or removed
          // wrapper does not silently move the footer inside the card.
          function insertionPoint(shadowRoot) {
            var node = shadowRoot.querySelector(ROOT_SELECTOR);
            if (!node) { return null; }
            var guard = 0;
            while (node.childElementCount === 1 && guard++ < 10) {
              var child = node.firstElementChild;
              var parentHeight = node.getBoundingClientRect().height;
              var childHeight = child.getBoundingClientRect().height;
              if (!(parentHeight > 0 && childHeight >= 0.9 * parentHeight)) { break; }
              node = child;
            }
            return node;
          }

          function buildRow(row) {
            var line = document.createElement('div');
            var fine = row.variant === 'fine';
            line.style.cssText = [
              'text-align:center',
              'margin-top:' + (fine ? '24px' : '16px'),
              'font-size:' + (fine ? '9px' : '14px'),
              'line-height:1.3',
              'color:' + (fine ? 'rgba(0,0,0,0.6)' : 'rgba(0,0,0,0.87)'),
              'font-family:inherit'
            ].join(';');

            (row.segments || []).forEach(function (segment) {
              if (segment.url) {
                var anchor = document.createElement('a');
                // textContent, never markup: host copy is never parsed as HTML.
                anchor.textContent = segment.label;
                anchor.setAttribute('href', segment.url);
                anchor.style.cssText =
                  'font-size:inherit;line-height:inherit;color:#2e74c7;text-decoration:none';
                line.appendChild(anchor);
              } else if (segment.text) {
                line.appendChild(document.createTextNode(segment.text));
              }
            });

            return line;
          }

          function renderFooter() {
            var shadowRoot = boxShadowRoot();
            if (!shadowRoot) { return; }

            var existing = shadowRoot.querySelector('#' + FOOTER_ID);
            var onLoginScreen = !!shadowRoot.querySelector(LOGIN_MARKER);

            if (!onLoginScreen) {
              if (existing) { existing.remove(); }
              return;
            }
            // Still mounted where we put it: nothing to do. React re-rendering the card
            // can detach it, which is what the observer below is for.
            if (existing && existing.isConnected) { return; }

            var target = insertionPoint(shadowRoot);
            if (!target) { return; }

            var wrapper = document.createElement('div');
            wrapper.id = FOOTER_ID;
            wrapper.style.cssText = 'width:100%;display:block;flex:0 0 auto';
            (FOOTER.rows || []).forEach(function (row) {
              wrapper.appendChild(buildRow(row));
            });
            target.appendChild(wrapper);

            hideCaptchaBadge();
          }

          // This script runs at document start, so the box does not exist yet, and its
          // screen changes happen inside a shadow root — which a MutationObserver on
          // `document` does not see. So: poll until the shadow root appears, then observe
          // it directly, keeping a slow poll as a backstop in case the box is re-created
          // wholesale.
          var observed = null;
          function attach() {
            renderFooter();
            var shadowRoot = boxShadowRoot();
            if (!shadowRoot || observed === shadowRoot) { return; }
            if (typeof MutationObserver !== 'function') { return; }
            observed = shadowRoot;
            new MutationObserver(function () {
              renderFooter();
            }).observe(shadowRoot, { childList: true, subtree: true });
          }

          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', attach);
          } else {
            attach();
          }
          setInterval(attach, 500);
        })();
        """.trimIndent()
    }
}
