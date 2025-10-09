package com.frontegg.android.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.frontegg.android.FronteggApp
import com.frontegg.android.fronteggAuth
import com.frontegg.android.init.ConfigCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.annotation.VisibleForTesting
import com.frontegg.android.utils.DispatcherProvider
import com.frontegg.android.utils.DefaultDispatcherProvider
import java.io.IOException
import java.net.UnknownHostException

object FronteggReconnector {
    private const val TAG = "FronteggReconnect"

    private var dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val mutex = Mutex()
    @Volatile private var offline: Boolean = false
    @Volatile private var debounceMs: Int = 500
    @Volatile private var lastAttemptAt: Long = 0

    fun setDebounceMs(value: Int) {
        debounceMs = value
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun setDispatcherProviderForTesting(provider: DispatcherProvider) {
        dispatcherProvider = provider
    }

    fun onNetworkLost() {
        offline = true
    }

    fun onNetworkAvailable(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastAttemptAt < debounceMs) {
            return
        }
        lastAttemptAt = now

        scope.launch {
            mutex.withLock {
                if (!isValidated(context)) {
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
                    Log.e(TAG, "Failed to handle network reconnect", t)
                }

                // Always try to refresh if possible, swallow network errors
                try {
                    try {
                        val refreshSuccess = context.fronteggAuth.refreshTokenIfNeeded()
                        if (refreshSuccess) {
                        }
                    } catch (_: Throwable) {
                        // If FronteggApp not initialized, skip refresh
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to handle network reconnect", t)
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
