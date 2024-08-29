package com.frontegg.android.services


import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.frontegg.android.FronteggApp
import com.frontegg.android.FronteggAuth
import java.net.SocketTimeoutException
import java.time.Instant

class RefreshTokenService : JobService() {

    companion object {
        private val TAG = RefreshTokenService::class.java.simpleName
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job started, (${Instant.now().toEpochMilli() - FronteggApp.getInstance().lastJobStart} ms)")
        performBackgroundTask(params)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job stopped before completion")
        return false
    }


    private fun performBackgroundTask(params: JobParameters?) {
        // Simulate a background task with a new thread or coroutine if needed
        Thread {
            var isError = false
            try {
                FronteggAuth.instance.sendRefreshToken()
                Log.d(TAG, "Job finished")
            }  catch (e: Exception) {
                Log.e(TAG, "Job unknown error occurred", e)
                // Catch unhandled exception
                FronteggAuth.instance.accessToken.value = null
                FronteggAuth.instance.isLoading.value = true
                isError = true
                if(e is SocketTimeoutException) {
                    FronteggAuth.instance.scheduleTimer(20000)
                }
            } finally {
                FronteggApp.getInstance().lastJobStart = Instant.now().toEpochMilli()
                // Notify the job manager that the job has been completed
                jobFinished(params, isError)
            }
        }.start()
    }
}
