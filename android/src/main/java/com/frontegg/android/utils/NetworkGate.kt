package com.frontegg.android.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.CheckResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit

object NetworkGate {

    private var fronteggBaseUrl: String? = null
    
    fun setFronteggBaseUrl(url: String) {
        fronteggBaseUrl = url
    }

    @CheckResult
    fun isNetworkLikelyGood(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        if (network == null) {
            return false
        }
        val caps = cm.getNetworkCapabilities(network)
        if (caps == null) {
            return false
        }

        // 2) Check basic capabilities
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false
        }

        // 3) If no URL for ping - use old logic
        if (fronteggBaseUrl == null) {
            return performBasicNetworkCheck(caps)
        }

        // 4) Perform real ping to Frontegg server
        return performPingTest()
    }
    
    private fun performBasicNetworkCheck(caps: NetworkCapabilities): Boolean {
        val onWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val onCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val onEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        
        val down = caps.linkDownstreamBandwidthKbps
        val up = caps.linkUpstreamBandwidthKbps
        val transportType = when {
            onWifi -> "WiFi"
            onCellular -> "Cellular"
            onEthernet -> "Ethernet"
            else -> "Unknown"
        }
        
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
    
    private fun performPingTest(): Boolean {
        val url = fronteggBaseUrl ?: return false
        
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url("$url/")
                .head()
                .build()
            
            val startTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val endTime = System.currentTimeMillis()
            val responseTime = endTime - startTime
            
            response.close()
            
            return responseTime < 1000
            
        } catch (e: Exception) {
            return false
        }
    }
}