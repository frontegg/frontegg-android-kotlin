package com.frontegg.android.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.frontegg.android.FronteggApp
import com.frontegg.android.fronteggAuth
import com.frontegg.android.init.ConfigCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.UnknownHostException

object FronteggReconnector {
    private const val TAG = "FronteggReconnect"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var offline: Boolean = false
    @Volatile private var debounceMs: Int = 500
    @Volatile private var lastAttemptAt: Long = 0

    fun setDebounceMs(value: Int) {
        debounceMs = value
    }

    fun onNetworkLost() {
        offline = true
        Log.d(TAG, "Network lost")
    }

    fun onNetworkAvailable(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastAttemptAt < debounceMs) {
            Log.d(TAG, "Debounced network available event")
            return
        }
        lastAttemptAt = now

        scope.launch {
            mutex.withLock {
                if (!isValidated(context)) {
                    Log.d(TAG, "Network not validated; skipping")
                    return@withLock
                }
                offline = false

                // If SDK is not fully initialized (no instance OR empty client/base) and we have cached regions init, retry it
                try {
                    val cache = ConfigCache.loadLastRegionsInit(context)
                    val storage = StorageProvider.getInnerStorage()
                    val notConfigured = storage.clientId.isBlank() || storage.baseUrl.isBlank()
                    if ((FronteggApp.instance == null || notConfigured) && cache != null) {
                        val (regions, flags) = cache
                        val mainCls = flags.mainActivityClassName?.let {
                            try { Class.forName(it) } catch (_: Throwable) { null }
                        }
                        Log.d(TAG, "Retrying initWithRegions from cache (${regions.size} regions)")
                        FronteggApp.initWithRegions(
                            regions = regions,
                            context = context,
                            useAssetsLinks = flags.useAssetsLinks,
                            useChromeCustomTabs = flags.useChromeCustomTabs,
                            mainActivityClass = mainCls,
                            useDiskCacheWebview = flags.useDiskCacheWebview,
                        )
                        // small delay to allow internal setup
                        delay(50)
                    }
                } catch (t: Throwable) {
                    when (t) {
                        is UnknownHostException -> Log.i(TAG, "Still offline during init: ${t.message}")
                        is IOException -> Log.w(TAG, "Transient IO during init: ${t.message}")
                        else -> Log.w(TAG, "initWithRegions retry failed: ${t.message}")
                    }
                }

                // Always try to refresh if possible, swallow network errors
                try {
                    val refreshed = try {
                        context.fronteggAuth.refreshTokenIfNeeded()
                    } catch (_: Throwable) {
                        // If FronteggApp not initialized, skip refresh
                        false
                    }
                    Log.d(TAG, "refreshTokenIfNeeded() => $refreshed")
                } catch (t: Throwable) {
                    when (t) {
                        is UnknownHostException -> Log.i(TAG, "Still offline during refresh: ${t.message}")
                        is IOException -> Log.w(TAG, "Transient IO during refresh: ${t.message}")
                        else -> Log.w(TAG, "Refresh failed: ${t.message}")
                    }
                }
            }
        }
    }

    private fun isValidated(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val n = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(n) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Throwable) {
            false
        }
    }
}
