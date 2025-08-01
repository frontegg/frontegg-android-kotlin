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

        Log.d(
            TAG,
            "Job started, (${
                Instant.now().toEpochMilli() - FronteggRefreshTokenTimer.lastJobStart
            } ms)"
        )
        performBackgroundTask(params)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job stopped before completion")
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
                Log.d(TAG, "Job finished")
            } catch (e: Exception) {
                Log.e(TAG, "Job unknown error occurred", e)
                // Catch unhandled exception
                FronteggState.accessToken.value = null
                FronteggState.isLoading.value = true
                isError = true
                if (e is SocketTimeoutException) {
                    service.refreshTokenTimer.scheduleTimer(20000)
                }
            } finally {
                FronteggRefreshTokenTimer.lastJobStart = Instant.now().toEpochMilli()
                // Notify the job manager that the job has been completed
                jobFinished(params, isError)
            }
        }.start()
    }
}


