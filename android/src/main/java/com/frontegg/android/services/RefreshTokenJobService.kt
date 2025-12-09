package com.frontegg.android.services


import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.frontegg.android.FronteggApp
import com.frontegg.android.fronteggApp
import com.frontegg.android.fronteggAuth
import java.net.SocketTimeoutException
import java.time.Instant

class RefreshTokenJobService : JobService() {
    companion object {
        private val TAG = RefreshTokenJobService::class.java.simpleName
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val appInstance: FronteggApp = fronteggApp

        if (appInstance !is FronteggAppService || appInstance.isAppInForeground()) {
            Log.d(
                TAG,
                "Either FronteggApp is not initialized, not of type FronteggAppService, or the app is not in the foreground. Skipping token refresh."
            )
            jobFinished(params, false)
            return false
        }

        performBackgroundTask(params)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }


    @VisibleForTesting
    internal fun performBackgroundTask(params: JobParameters?) {
        // Simulate a background task with a new thread or coroutine if needed
        Thread {
            var isError = false
            val service = fronteggAuth as FronteggAuthService
            try {
                service.sendRefreshToken()
            } catch (e: Exception) {
                isError = true
                
                when (e) {
                    is com.frontegg.android.exceptions.FailedToAuthenticateException -> {
                        // 401 / invalid refresh token – clear credentials
                        service.clearCredentials()
                    }
                    is SocketTimeoutException,
                    is java.net.ConnectException,
                    is java.net.UnknownHostException,
                    is java.io.IOException -> {
                        // Network errors (timeout, no connection, offline) – do NOT clear tokens;
                        // keep them for retry when network recovers. Supports offline mode.
                        Log.w(TAG, "Network error during JobService refresh, keeping tokens for retry")
                        // Reschedule for retry when network might be available
                        service.refreshTokenTimer.scheduleTimer(10000)
                    }
                    else -> {
                        // Other non-network errors – clear only access token, keep refresh token for retry
                        Log.w(TAG, "Non-network error during JobService refresh, clearing access token only")
                        FronteggState.accessToken.value = null
                        FronteggState.isLoading.value = true
                    }
                }
            } finally {
                FronteggRefreshTokenTimer.lastJobStart = Instant.now().toEpochMilli()
                // Notify the job manager that the job has been completed
                jobFinished(params, isError)
            }
        }.start()
    }
}


