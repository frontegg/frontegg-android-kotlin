package com.frontegg.android.services

import android.content.Context
import android.os.Handler
import android.util.Log
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

    init {
        Handler(context.mainLooper).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)
        }
    }

    private var lifecycleEventObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                Log.d(TAG, "ON_STOP")
                appInForeground = false
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

    companion object {
        val TAG: String = FronteggAppLifecycle::class.java.simpleName
    }
}