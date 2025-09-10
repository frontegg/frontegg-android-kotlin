package com.frontegg.android.init

import android.content.ContentProvider
import android.content.ComponentName
import android.content.ContentValues
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import com.frontegg.android.services.FronteggReconnector

class FronteggInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return true

        val pm = ctx.packageManager
        val cn = ComponentName(ctx, FronteggInitProvider::class.java)
        val ai = pm.getProviderInfo(cn, 0)
        val meta: Bundle? = ai.metaData

        val enabled = meta?.getBoolean(META_ENABLED, true) ?: true
        if (!enabled) {
            Log.d(TAG, "Auto-reconnect disabled via manifest metadata")
            return true
        }

        val debounce = (meta?.getInt(META_DEBOUNCE_MS) ?: DEFAULT_DEBOUNCE_MS).coerceAtLeast(0)
        FronteggReconnector.setDebounceMs(debounce)

        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Ensure the network is validated before attempting any network operations
                val caps = cm.getNetworkCapabilities(network)
                val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                if (validated) {
                    FronteggReconnector.onNetworkAvailable(ctx.applicationContext)
                } else {
                    Log.d(TAG, "Network available but not validated; ignoring")
                }
            }

            override fun onLost(network: Network) {
                FronteggReconnector.onNetworkLost()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (validated) {
                    FronteggReconnector.onNetworkAvailable(ctx.applicationContext)
                }
            }
        }

        return try {
            cm.registerDefaultNetworkCallback(callback)
            Log.d(TAG, "Registered default network callback for auto-reconnect (debounce=${debounce}ms)")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register network callback: ${t.message}")
            true
        }
    }

    override fun query(
        uri: android.net.Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: android.net.Uri): String? = null

    override fun insert(uri: android.net.Uri, values: ContentValues?): android.net.Uri? = null

    override fun delete(uri: android.net.Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: android.net.Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val TAG = "FronteggReconnect"
        private const val META_ENABLED = "frontegg.autoreconnect.enabled"
        private const val META_DEBOUNCE_MS = "frontegg.autoreconnect.debounceMs"
        private const val DEFAULT_DEBOUNCE_MS = 500
    }
}


