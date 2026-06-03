package com.frontegg.android.services

import android.content.Context
import android.os.Handler
import android.util.Log
import android.webkit.CookieManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.frontegg.android.utils.FronteggCallback

class FronteggAppLifecycle(
    context: Context,
) {
    var appInForeground = true
    val startApp = FronteggCallback()
    val stopApp = FronteggCallback()

    private var lifecycleEventObserver: LifecycleEventObserver

    init {
        // Initialize lifecycleEventObserver before using it
        lifecycleEventObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    Log.d(TAG, "ON_STOP")
                    appInForeground = false
                    // Persist the WebView cookie jar to disk whenever the app
                    // backgrounds. Android writes CookieManager lazily, so the
                    // embedded-login session cookies (fe_refresh_*, fe_device_*)
                    // that AdminPortalActivity needs can be lost on a hard kill
                    // (the "finish app → restart / cold start" cases) if they
                    // were never flushed. ON_STOP always fires before the
                    // process is killed, so flushing here guarantees the
                    // cookies survive to the next cold start.
                    try {
                        CookieManager.getInstance().flush()
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to flush CookieManager on ON_STOP", e)
                    }
                    stopApp.trigger()
                }

                Lifecycle.Event.ON_START -> {
                    Log.d(TAG, "ON_START")
                    appInForeground = true
                    startApp.trigger()
                }

                else -> {}
            }
        }


        Handler(context.mainLooper).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)
        }
    }

    companion object {
        val TAG: String = FronteggAppLifecycle::class.java.simpleName
    }
}