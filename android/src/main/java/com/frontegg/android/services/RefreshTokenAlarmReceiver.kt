package com.frontegg.android.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.frontegg.android.fronteggAuth
import java.net.SocketTimeoutException

/**
 * Alarm-based fallback for refresh token when device is in Doze / idle.
 * Works together with JobScheduler – whichever fires first performs the refresh.
 */
class RefreshTokenAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val tag = TAG

        // Best-effort guard – if SDK not initialized, do nothing
        val service = context.fronteggAuth as? FronteggAuthService ?: run {
            Log.w(tag, "FronteggAuthService not available, skipping alarm refresh")
            return
        }

        Log.d(tag, "Alarm received – starting token refresh from AlarmManager")

        // Run refresh in background to avoid blocking the main thread
        Thread {
            // Avoid logout on stale 401 if refresh token rotated by another in-flight refresh.
            val refreshTokenBefore = service.refreshToken.value
            try {
                // Manual call: allow refresh even if auto-refresh is disabled
                service.sendRefreshToken(isManualCall = true)
            } catch (e: Exception) {
                Log.w(tag, "Alarm-based refresh failed: ${e.message}", e)

                when (e) {
                    is com.frontegg.android.exceptions.FailedToAuthenticateException -> {
                        val refreshTokenAfter = service.refreshToken.value
                        if (
                            refreshTokenBefore != null &&
                            refreshTokenAfter != null &&
                            refreshTokenAfter != refreshTokenBefore
                        ) {
                            Log.w(
                                tag,
                                "Refresh token rotated during in-flight refresh " +
                                    "(before=$refreshTokenBefore after=$refreshTokenAfter); skipping clearCredentials()"
                            )
                        } else {
                            service.clearCredentials()
                        }
                    }
                    is SocketTimeoutException,
                    is java.net.ConnectException,
                    is java.net.UnknownHostException,
                    is java.io.IOException -> {
                        // Network issue – do NOT clear tokens; let normal
                        // app-foreground or next schedule handle retry
                        Log.w(tag, "Network error during alarm refresh, keeping tokens")
                    }
                    else -> {
                        // Other errors – clear only access token and keep refresh for retry
                        Log.w(tag, "Non-auth error during alarm refresh, clearing access token only")
                        com.frontegg.android.services.FronteggState.accessToken.value = null
                        com.frontegg.android.services.FronteggState.isLoading.value = true
                    }
                }
            }
        }.start()
    }

    companion object {
        private val TAG = RefreshTokenAlarmReceiver::class.java.simpleName
    }
}


