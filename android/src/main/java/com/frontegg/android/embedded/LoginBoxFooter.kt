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
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/** Assigns `window.__fronteggLoginBoxFooter`, which the hosted login box renders below its card. */
object LoginBoxFooter {
    private val TAG = LoginBoxFooter::class.java.simpleName

    const val GLOBAL_NAME = "__fronteggLoginBoxFooter"

    private val DENIED_SCHEMES = setOf(
        "javascript", "data", "file", "blob", "about", "vbscript", "intent", "content"
    )

    private val OAUTH_CALLBACK_PARAMETERS = setOf("code", "error", "error_description")

    /** The footer links [FronteggWebClient] hands off instead of loading in the box. */
    internal class Links(
        val externalUrls: Set<String> = emptySet(),
        val appSchemes: Set<String> = emptySet()
    ) {
        fun isExternal(url: String): Boolean =
            externalUrls.isNotEmpty() && canonicalLinkKey(url) in externalUrls

        fun isAppHandoff(scheme: String?): Boolean =
            scheme != null && scheme.lowercase() in appSchemes
    }

    /** Returns the links of the footer it injected, so they match what the box renders. */
    internal fun install(webView: WebView, storage: FronteggInnerStorage = FronteggInnerStorage()): Links {
        val footer = storage.loginBoxFooter ?: return Links()
        val handlesScheme = { scheme: String -> hostAppHandles(webView.context, scheme) }

        rejectedLinkUrls(footer, handlesScheme).forEach {
            Log.w(TAG, "loginBoxFooter link \"$it\" is not an absolute http(s) URL or an allowed app-scheme URL without code, error or error_description parameters; it renders as plain text")
        }

        val sanitized = sanitizedFooter(footer, handlesScheme)
        if (sanitized == null) {
            Log.w(TAG, "loginBoxFooter was set but contains no usable rows; no footer rendered")
            return Links()
        }

        if (!LoginBoxCustomization.isSupported()) {
            Log.e(TAG, "DOCUMENT_START_SCRIPT unsupported; login box footer not installed")
            return Links()
        }

        val origin = LoginBoxCustomization.authOrigin(storage.baseUrl)
        if (origin == null) {
            Log.e(TAG, "baseUrl has no usable origin; login box footer not installed")
            return Links()
        }

        WebViewCompat.addDocumentStartJavaScript(webView, script(sanitized), setOf(origin))
        return links(sanitized)
    }

    /** Returns `null` when the footer is absent or has no usable rows. */
    fun script(
        footer: Map<String, Any?>?,
        handlesScheme: (String) -> Boolean = { false }
    ): String? = sanitizedFooter(footer, handlesScheme)?.let(::script)

    private fun script(sanitized: JSONObject): String {
        // U+2028/U+2029 are valid inside JSON but terminate a line in JavaScript source.
        val json = sanitized.toString()
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")

        return "window.$GLOBAL_NAME = $json;"
    }

    /** Keeps text and label/URL segments only; a link whose URL fails [sanitizedLinkUrl] becomes text. */
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

    /** Accepts an absolute `http(s)` URL, or a host-app-scheme URL that is not OAuth-callback-shaped. */
    internal fun sanitizedLinkUrl(
        url: String?,
        handlesScheme: (String) -> Boolean = { false }
    ): String? {
        if (url.isNullOrEmpty()) return null
        val uri = parse(url) ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null

        if (scheme == "http" || scheme == "https") {
            if (uri.host.isNullOrEmpty()) return null
            return url
        }

        if (scheme in DENIED_SCHEMES) return null
        if (!handlesScheme(scheme)) return null

        return if (carriesOAuthCallbackParameter(url)) null else url
    }

    /** Checks the query and the fragment, which a hash-routed app reads as its query. */
    internal fun carriesOAuthCallbackParameter(url: String): Boolean {
        val start = url.indexOfAny(charArrayOf('?', '#'))
        if (start == -1) return false

        return url.substring(start + 1)
            .split('?', '#', '&')
            .map { decode(it.substringBefore('=')) }
            .any { it in OAUTH_CALLBACK_PARAMETERS }
    }

    /** The configured link URLs that render as plain text, cut before any query or fragment. */
    internal fun rejectedLinkUrls(
        footer: Map<String, Any?>?,
        handlesScheme: (String) -> Boolean = { false }
    ): List<String> {
        val rows = footer?.get("rows") as? List<*> ?: return emptyList()

        val rejected = mutableListOf<String>()
        for (row in rows) {
            val segments = (row as? Map<*, *>)?.get("segments") as? List<*> ?: continue
            for (segment in segments) {
                val fields = segment as? Map<*, *> ?: continue
                val text = fields["text"] as? String
                val label = fields["label"] as? String
                val url = fields["url"]
                if (!text.isNullOrEmpty() || label.isNullOrEmpty() || url == null) continue
                if (sanitizedLinkUrl(url as? String, handlesScheme) != null) continue

                rejected.add(url.toString().substringBefore('?').substringBefore('#'))
            }
        }
        return rejected
    }

    internal fun footerLinks(
        footer: Map<String, Any?>?,
        handlesScheme: (String) -> Boolean = { false }
    ): Links = links(sanitizedFooter(footer, handlesScheme))

    private fun links(sanitized: JSONObject?): Links {
        val rows = sanitized?.optJSONArray("rows") ?: return Links()

        val externalUrls = mutableSetOf<String>()
        val appSchemes = mutableSetOf<String>()
        for (i in 0 until rows.length()) {
            val segments = rows.optJSONObject(i)?.optJSONArray("segments") ?: continue
            for (j in 0 until segments.length()) {
                val url = segments.optJSONObject(j)?.optString("url").orEmpty()
                val scheme = parse(url)?.scheme?.lowercase() ?: continue
                if (scheme == "http" || scheme == "https") {
                    canonicalLinkKey(url)?.let(externalUrls::add)
                } else {
                    appSchemes.add(scheme)
                }
            }
        }
        return Links(externalUrls, appSchemes)
    }

    /** Normalizes an `http(s)` URL the way WebView does before navigating. */
    internal fun canonicalLinkKey(url: String): String? {
        val uri = parse(url)?.normalize() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val isDefaultPort = (scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80)

        return buildString {
            append(scheme).append("://")
            uri.rawUserInfo?.let { append(it).append('@') }
            append(host)
            if (uri.port != -1 && !isDefaultPort) append(':').append(uri.port)
            append(uri.rawPath.orEmpty().ifEmpty { "/" })
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    /** Whether the host app itself, not another installed app, has a browsable VIEW filter for [scheme]. */
    internal fun hostAppHandles(context: Context, scheme: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            context.packageManager
                .queryIntentActivities(intent, 0)
                .any { it.activityInfo?.packageName == context.packageName }
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not resolve handlers for scheme '$scheme'", e)
            false
        }
    }

    private fun parse(url: String): URI? = try {
        URI(url)
    } catch (e: URISyntaxException) {
        null
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: IllegalArgumentException) {
        value
    } catch (e: UnsupportedEncodingException) {
        value
    }
}
