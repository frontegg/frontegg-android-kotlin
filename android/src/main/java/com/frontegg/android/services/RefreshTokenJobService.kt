package com.frontegg.android.services


import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.net.SocketTimeoutException
import java.time.Instant
import com.frontegg.android.FronteggApp
import com.frontegg.android.exceptions.FronteggException

class RefreshTokenJobService : JobService() {
    companion object {
        private val TAG = RefreshTokenJobService::class.java.simpleName
    }   

    override fun onStartJob(params: JobParameters?): Boolean {
        val appInstance: FronteggApp? = try {
            FronteggApp.getInstance()
        } catch (e: FronteggException) {
            null
        }
    
        if (appInstance == null || appInstance !is FronteggAppService || appInstance.isAppInForeground()) {
            Log.d(TAG, "Either FronteggApp is not initialized, not of type FronteggAppService, or the app is not in the foreground. Skipping token refresh.")
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
            try {
                FronteggAuthService.instance.sendRefreshToken()
                Log.d(TAG, "Job finished")
            } catch (e: Exception) {
                Log.e(TAG, "Job unknown error occurred", e)
                // Catch unhandled exception
                FronteggAuthService.instance.accessToken.value = null
                FronteggAuthService.instance.isLoading.value = true
                isError = true
                if (e is SocketTimeoutException) {
                    FronteggAuthService.instance.refreshTokenTimer.scheduleTimer(20000)
                }
            } finally {
                FronteggRefreshTokenTimer.lastJobStart = Instant.now().toEpochMilli()
                // Notify the job manager that the job has been completed
                jobFinished(params, isError)
            }
        }.start()
    }
}


