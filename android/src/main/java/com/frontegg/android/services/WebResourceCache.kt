import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import androidx.collection.LruCache
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLConnection
import java.util.concurrent.TimeUnit

class WebResourceCache(
    context: Context,
    memoryCacheSize: Int = 5 * 1024 * 1024,
    diskCacheMaxSize: Long = 50L * 1024 * 1024
) {
    private data class CacheEntry(
        val data: ByteArray,
        val mimeType: String,
        val encoding: String,
        var lastAccess: Long = System.currentTimeMillis()
    )

    private val memoryCache = LruCache<String, CacheEntry>(memoryCacheSize)
    private val httpClient: OkHttpClient

    init {
        // Set up OkHttp disk cache
        val cacheDir = File(context.cacheDir, "frontegg_cache")
        val diskCache = Cache(cacheDir, diskCacheMaxSize)
        httpClient = OkHttpClient.Builder()
            .cache(diskCache)
            .build()

        // Optionally prune stale memory entries on startup
        pruneMemoryCache()
    }

    /** Guess MIME-type by URL extension or fallback to system guess */
    private fun guessMime(url: String) = when {
        url.endsWith(".js") -> "application/javascript"
        url.endsWith(".css") -> "text/css"
        url.endsWith(".woff2") -> "font/woff2"
        else -> URLConnection.guessContentTypeFromName(url)
            ?: "application/octet-stream"
    }

    /** Parse “Content-Type” header into (mimeType, charset) */
    private fun parseContentType(header: String?, url: String): Pair<String, String> {
        header?.split(";")?.map { it.trim() }?.let { parts ->
            val mime = parts[0]
            val charset = parts
                .find { it.startsWith("charset=", ignoreCase = true) }
                ?.substringAfter("=")
                ?: "utf-8"
            return mime to charset
        }
        return guessMime(url) to "utf-8"
    }

    /** Evict any in‐memory entries not accessed in the last `ttlDays` days */
    private fun pruneMemoryCache(ttlDays: Long = 10) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttlDays)
        for ((key, entry) in memoryCache.snapshot()) {
            if (entry.lastAccess < cutoff) {
                memoryCache.remove(key)
                Log.d(TAG, "Pruned memory cache entry (stale): $key")
            }
        }
    }

    /**
     * Try memory → disk → network; returns null on miss/error.
     * Disk cache only returns responses cached within the last 10 days.
     */
    fun get(url: String): WebResourceResponse? {
        try {
            // 0️⃣ Prune old in‐memory entries
            pruneMemoryCache()

            // 1️⃣ In‐RAM lookup
            memoryCache.get(url)?.let { entry ->
                entry.lastAccess = System.currentTimeMillis()
                Log.d(TAG, "Cache hit in RAM: $url")
                return WebResourceResponse(
                    entry.mimeType,
                    entry.encoding,
                    ByteArrayInputStream(entry.data)
                ).apply { responseHeaders = corsHeaders }
            }

            // 2️⃣ On‐disk cache (onlyIfCached + maxStale = 10 days)
            Log.d(TAG, "Cache miss in RAM, trying disk cache: $url")
            val cacheControl = CacheControl.Builder()
                .onlyIfCached()
                .maxStale(10, TimeUnit.DAYS)
                .build()
            val cacheReq = Request.Builder()
                .url(url)
                .cacheControl(cacheControl)
                .build()

            httpClient.newCall(cacheReq).execute().use { resp ->
                if (resp.isSuccessful && resp.code != 504) {
                    Log.d(TAG, "Cache hit on disk (≤10d old): $url")
                    val bytes = resp.body!!.bytes()
                    val (mime, encoding) = parseContentType(resp.header("Content-Type"), url)
                    put(url, bytes, mime, encoding)
                    return WebResourceResponse(mime, encoding, ByteArrayInputStream(bytes))
                        .apply { responseHeaders = corsHeaders }
                } else {
                    Log.d(TAG, "No usable disk cache (miss or >10d old): $url")
                }
            }

            // 3️⃣ Network fallback
            Log.d(TAG, "Cache miss on disk, going to network: $url")
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.d(TAG, "Network error: $url (${resp.code})")
                    return null
                }
                Log.d(TAG, "Network success: $url")
                val bytes = resp.body!!.bytes()
                val (mime, encoding) = parseContentType(resp.header("Content-Type"), url)
                put(url, bytes, mime, encoding)
                return WebResourceResponse(mime, encoding, ByteArrayInputStream(bytes))
                    .apply { responseHeaders = corsHeaders }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching resource $url", e)
            return null
        }
    }

    /** Store into both memory & disk (disk is automatic via OkHttp cache) */
    private fun put(url: String, data: ByteArray, mimeType: String, encoding: String) {
        memoryCache.put(url, CacheEntry(data, mimeType, encoding))
    }

    private val corsHeaders = mapOf(
        "Access-Control-Allow-Origin" to "https://autheu.davidantoon.me",
        "Access-Control-Allow-Methods" to "GET, OPTIONS"
    )

    private val staticAssetPattern = Regex(
        "https://(cdn\\.frontegg\\.com/content/hosted-login|" +
                "assets\\.frontegg\\.com/admin-box/|fonts\\.gstatic\\.com|" +
                "fonts\\.googleapis\\.com)/.*"
    )

    /** Should we attempt to cache this URL? */
    fun shouldCache(url: String): Boolean =
        url.isNotEmpty() && staticAssetPattern.matches(url)

    companion object {
        @Volatile
        private var INSTANCE: WebResourceCache? = null
        private const val TAG = "WebResourceCache"

        fun getInstance(context: Context): WebResourceCache =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebResourceCache(context.applicationContext).also {
                    INSTANCE = it
                }
            }
    }
}
