package com.frontegg.android.services


import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.frontegg.android.FronteggAuth
import java.net.SocketTimeoutException

class RefreshTokenService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("MyJobService", "Job started")

        // Perform your background task here
        performBackgroundTask(params)

        // Return true if the job is still running, false if it is completed
        // This example returns false, indicating the job is finished
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d("MyJobService", "Job stopped before completion")

        // Cleanup if necessary. Return true to reschedule the job if needed
        return false
    }


    private fun performBackgroundTask(params: JobParameters?) {
        // Simulate a background task with a new thread or coroutine if needed
        Thread {
            try {
                FronteggAuth.instance.sendRefreshToken()
                Log.d("MyJobService", "Job finished")
            } catch (e: InterruptedException) {
                Log.e("MyJobService", "Job interrupted", e)
                FronteggAuth.instance.accessToken.value = null
                FronteggAuth.instance.isLoading.value = true
                jobFinished(params, true)
            } catch (e: SocketTimeoutException) {
                Log.e("MyJobService", "Job interrupted", e)
                FronteggAuth.instance.accessToken.value = null
                FronteggAuth.instance.isLoading.value = true
                jobFinished(params, true)
            } finally {
                // Notify the job manager that the job has been completed
                jobFinished(params, false)
            }
        }.start()
    }
}
