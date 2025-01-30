package com.frontegg.android.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import java.time.Instant

fun Long.calculateTimerOffset(): Long {
    val expirationTime = this
    val now: Long = Instant.now().toEpochMilli()
    val remainingTime = (expirationTime * 1000) - now

    val minRefreshWindow = 20000 // minimum 20 seconds before exp
    val adaptiveRefreshTime = remainingTime * 0.8 // 80% of remaining time

    return if (remainingTime > minRefreshWindow) {
        adaptiveRefreshTime.toLong()
    } else {
        (remainingTime - minRefreshWindow).coerceAtLeast(0)
    }
}

fun Context.isActivityEnabled(activityClassName: String): Boolean {
    return try {
        val componentName = ComponentName(this, activityClassName)
        packageManager.getActivityInfo(componentName, PackageManager.MATCH_ALL).isEnabled
    } catch (e: Exception) {
        false
    }
}