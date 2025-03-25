package com.frontegg.debug

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONException
import java.net.HttpURLConnection
import java.net.URL
import com.frontegg.android.BuildConfig

class AndroidDebugConfigurationChecker(
    private val context: Context,
    private val fronteggDomain: String,
    private val clientId: String
) {

    private val baseUrl: String = "https://$fronteggDomain"
    private val assetLinksUrl: String = "$baseUrl/.well-known/assetlinks.json"
    private val oauthPreloginEndpoint: String = "$baseUrl/oauth/prelogin"

    fun runChecks() {
        if (!BuildConfig.DEBUG) {
            Log.d(TAG, "ℹ️ Skipping debug configuration checks in release mode.")
            return
        }

        Log.d(TAG, "🔍 Running Android debug configuration checks...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                validateAssetLinks()
                checkCustomDomain()
                checkRedirectURI()
                Log.d(TAG, "✅ All debug checks completed.")
            } catch (e: DebugCheckException) {
                Log.e(TAG, "❌ ERROR: ${e.message}")
                throw e
            }
        }
    }

    /**
     * ✅ Check if asset links are correctly configured (Warning)
     */
    private suspend fun validateAssetLinks() {
        val response = makeHttpRequest(assetLinksUrl)

        try {
            val jsonArray = JSONArray(response)

            if (jsonArray.length() == 0) {
                Log.w(TAG, "⚠️ WARNING: `assetlinks.json` is empty. This may cause issues with passkeys/magic links.")
                return
            }

            var isValid = false

            for (i in 0 until jsonArray.length()) {
                val entry = jsonArray.getJSONObject(i)
                val relation = entry.optJSONArray("relation")
                val target = entry.optJSONObject("target")

                if (relation != null && target != null) {
                    isValid = true
                    break
                }
            }

            if (!isValid) {
                Log.w(TAG, "⚠️ WARNING: `assetlinks.json` is missing required fields. Passkeys and deep links may not work.")
                return
            }

            Log.d(TAG, "✅ `assetlinks.json` is valid. App verification is correctly configured.")

        } catch (e: JSONException) {
            Log.w(TAG, "⚠️ WARNING: JSON Parsing failed in `assetlinks.json` - ${e.localizedMessage}")
        }
    }

    /**
     * ✅ Check if the app is using a custom domain (Warning)
     */
    private fun checkCustomDomain() {
        if (fronteggDomain.length < 3 || !fronteggDomain.contains(".")) {
            Log.w(TAG, "⚠️ WARN: Custom domain seems incorrect or missing. Passkeys and magic-link login may not work.")
        }
    }

    /**
     * ❌ Check the redirect URI and throw an error if it fails
     */
    private suspend fun checkRedirectURI() {
        val baseRedirectUri = "android-app://$baseUrl/oauth/callback"
        val encodedRedirectUri = baseRedirectUri.encode()

        val state = java.util.UUID.randomUUID().toString()

        val queryParams = mapOf(
            "client_id" to clientId,
            "state" to state,
            "redirect_uri" to encodedRedirectUri
        )

        val finalUrl = buildUrlWithParams(oauthPreloginEndpoint, queryParams)

        val responseCode = makeHttpRequestForStatus(finalUrl)
        if (responseCode != 200) {
            throw DebugCheckException("❌ ERROR: Redirect URI is invalid. Status: $responseCode")
        }

        Log.d(TAG, "✅ Redirect URI check passed.")
    }

    private suspend fun makeHttpRequest(urlString: String): String {
        return withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun makeHttpRequestForStatus(urlString: String): Int {
        return withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            try {
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun buildUrlWithParams(base: String, params: Map<String, String>): String {
        return params.entries.joinToString("&", prefix = "$base?") { (key, value) -> "$key=${value.encode()}" }
    }

    private fun String.encode(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }

    companion object {
        private const val TAG = "AndroidDebugConfigurationChecker"
    }

    class DebugCheckException(message: String) : Exception(message)
}
