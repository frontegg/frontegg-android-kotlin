package com.frontegg.android.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.CheckResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object NetworkGate {

    private var fronteggBaseUrl: String? = null

    @Volatile
    private var e2eForceNetworkPathOffline: Boolean = false

    /**
     * When true, [isNetworkLikelyGood] always returns false (embedded E2E / instrumented tests).
     */
    fun setE2eForceNetworkPathOffline(enabled: Boolean) {
        e2eForceNetworkPathOffline = enabled
    }

    fun isE2eForceNetworkPathOffline(): Boolean = e2eForceNetworkPathOffline
    
    fun setFronteggBaseUrl(url: String) {
        fronteggBaseUrl = url
    }

    @CheckResult
    fun isNetworkLikelyGood(ctx: Context): Boolean {
        if (e2eForceNetworkPathOffline) {
            return false
        }

        // Local mock-server URLs (E2E tests) bypass ConnectivityManager entirely:
        // loopback is always reachable and CM.activeNetwork may return null on CI emulators.
        val url = fronteggBaseUrl
        if (url != null && isLocalUrl(url)) {
            return performPingTest()
        }

        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false
        }

        if (fronteggBaseUrl == null) {
            return performBasicNetworkCheck(caps)
        }

        return performPingTest()
    }

    private fun isLocalUrl(url: String): Boolean {
        val u = url.lowercase().trimEnd('/')
        return u.startsWith("http://127.0.0.1") ||
            u.startsWith("http://localhost") ||
            u.startsWith("http://10.0.2.2")
    }
    
    private fun performBasicNetworkCheck(caps: NetworkCapabilities): Boolean {
        val onWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val onCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        val down = caps.linkDownstreamBandwidthKbps
        val up = caps.linkUpstreamBandwidthKbps

        // Allow only fast networks
        if (onWifi && down > 0 && up > 0) {
            if (down < 2000 || up < 1000) {
                return false
            }
        } else if (onCellular && down > 0 && up > 0) {
            if (down < 500 || up < 200) {
                return false
            }
        } else {
            return false
        }
        
        return true
    }
    
    private val pingClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun performPingTest(): Boolean {
        val url = fronteggBaseUrl ?: return false
        val trimmed = url.trimEnd('/')

        if (isLocalUrl(trimmed)) {
            return performLocalPing("$trimmed/test")
        }

        val probeUrl = "$trimmed/"
        return try {
            val request = Request.Builder()
                .url(probeUrl)
                .head()
                .build()
            
            val startTime = System.currentTimeMillis()
            val response = pingClient.newCall(request).execute()
            val responseTime = System.currentTimeMillis() - startTime
            
            response.close()
            
            responseTime < 3000
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fresh [HttpURLConnection] per call avoids OkHttp connection-pool state
     * that can leak between E2E test runs sharing the same process.
     */
    private fun performLocalPing(probeUrl: String): Boolean {
        return try {
            val conn = URL(probeUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000
            conn.instanceFollowRedirects = false
            try {
                val startTime = System.currentTimeMillis()
                val code = conn.responseCode
                val elapsed = System.currentTimeMillis() - startTime
                code in 200..499 && elapsed < 3000
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }
}