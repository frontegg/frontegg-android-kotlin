package com.frontegg.android.services


import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.frontegg.android.FronteggAuth
import java.net.SocketTimeoutException

class RefreshTokenService : JobService() {

    companion object {
        private val TAG = RefreshTokenService::class.java.simpleName
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job started")

        // Perform your background task here
        performBackgroundTask(params)

        // Return true if the job is still running, false if it is completed
        // This example returns false, indicating the job is finished
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job stopped before completion")

        // Cleanup if necessary. Return true to reschedule the job if needed
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
            } finally {
                // Notify the job manager that the job has been completed
                jobFinished(params, isError)
            }
        }.start()
    }
}
