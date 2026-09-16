package com.frontegg.android.embedded

import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.frontegg.android.services.FronteggInnerStorage
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Runtime theme and copy overrides for the embedded login box.
 *
 * The hosted login box reads `window.__fronteggLoginBoxOverrides` and deep-merges it over
 * the environment's own configuration. This registers a document-start script that assigns
 * that global before the box boots.
 *
 * Android counterpart of iOS `LoginBoxCustomization` (WKUserScript at `.atDocumentStart`).
 */
object LoginBoxCustomization {
    private val TAG = LoginBoxCustomization::class.java.simpleName

    const val GLOBAL_NAME = "__fronteggLoginBoxOverrides"

    /**
     * Whether this WebView provider can apply login box overrides at all. Hosts that
     * depend on per-brand appearance should check this before presenting embedded login,
     * because on an unsupported provider the box renders the environment's own branding.
     */
    fun isSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    fun install(webView: WebView, storage: FronteggInnerStorage = FronteggInnerStorage()) {
        val themeOptions = storage.loginBoxThemeOptions
        val localizations = storage.loginBoxLocalizations
        if (themeOptions.isNullOrEmpty() && localizations.isNullOrEmpty()) {
            return
        }

        invalidKeyPath(themeOptions)?.let {
            Log.e(TAG, "loginBoxThemeOptions contains a value at $it that cannot be represented in JSON; overrides not installed")
            return
        }
        invalidKeyPath(localizations)?.let {
            Log.e(TAG, "loginBoxLocalizations contains a value at $it that cannot be represented in JSON; overrides not installed")
            return
        }

        val script = script(themeOptions, localizations) ?: return

        if (!isSupported()) {
            Log.e(TAG, "DOCUMENT_START_SCRIPT unsupported; login box overrides not installed and the box will render the environment's branding")
            return
        }

        val origin = authOrigin(storage.baseUrl)
        if (origin == null) {
            Log.e(TAG, "baseUrl has no usable origin; login box overrides not installed")
            return
        }

        WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(origin))
    }

    /** `scheme://host[:port]` for [baseUrl], or null when it cannot be parsed. */
    internal fun authOrigin(baseUrl: String): String? {
        val uri = try {
            URI(baseUrl)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        return if (uri.port != -1) "$scheme://$host:${uri.port}" else "$scheme://$host"
    }

    fun script(themeOptions: Map<String, Any?>?, localizations: Map<String, Any?>?): String? {
        val overrides = JSONObject()

        if (!themeOptions.isNullOrEmpty()) {
            overrides.put("themeV2", toJson(themeOptions) ?: return null)
        }
        if (!localizations.isNullOrEmpty()) {
            overrides.put("localizations", toJson(localizations) ?: return null)
        }
        if (overrides.length() == 0) {
            return null
        }

        // U+2028/U+2029 are valid inside JSON but terminate a line in JavaScript source.
        val json = overrides.toString()
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")

        return "window.$GLOBAL_NAME = $json;"
    }

    /**
     * Key path of the first value that cannot be represented in JSON, or null when the
     * whole value is encodable. `org.json` silently turns an unsupported value into null,
     * which would erase the environment's own value instead of leaving it alone, so the
     * offending key is named and the overrides are dropped as a whole.
     */
    internal fun invalidKeyPath(value: Any?, path: String = ""): String? = when (value) {
        null, JSONObject.NULL -> null
        is String, is Boolean, is Int, is Long, is Short, is Byte -> null
        is Double -> if (value.isFinite()) null else orRoot(path)
        is Float -> if (value.isFinite()) null else orRoot(path)
        is Map<*, *> -> value.entries.asSequence()
            .map { (key, child) ->
                if (key !is String) orRoot(path) else invalidKeyPath(child, child(path, key))
            }
            .firstOrNull { it != null }
        is Iterable<*> -> value.asSequence()
            .mapIndexed { index, child -> invalidKeyPath(child, "$path[$index]") }
            .firstOrNull { it != null }
        else -> orRoot(path)
    }

    private fun orRoot(path: String) = path.ifEmpty { "<root>" }

    private fun child(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"

    private fun toJson(map: Map<String, Any?>): JSONObject? {
        val json = JSONObject()
        map.forEach { (key, value) ->
            json.put(key, toJsonValue(value) ?: return null)
        }
        return json
    }

    private fun toJsonValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> {
            val json = JSONObject()
            value.forEach { (key, child) ->
                if (key !is String) return null
                json.put(key, toJsonValue(child) ?: return null)
            }
            json
        }
        is Iterable<*> -> {
            val array = JSONArray()
            value.forEach { child -> array.put(toJsonValue(child) ?: return null) }
            array
        }
        else -> if (invalidKeyPath(value) == null) value else null
    }
}
