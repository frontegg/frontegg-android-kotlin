package com.frontegg.android.services

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.frontegg.android.utils.FronteggCallback
import java.time.Instant
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule

class FronteggRefreshTokenTimer(
    private val context: Context,
    private val appLifecycle: FronteggAppLifecycle
) {
    val refreshTokenIfNeeded = FronteggCallback()

    private var refreshTokenJob: JobInfo? = null
    private var timerTask: TimerTask? = null

    fun cancelLastTimer() {
        Log.d(TAG, "Cancel Last Timer")
        if (timerTask != null) {
            timerTask?.cancel()
            timerTask = null
        }
        if (refreshTokenJob != null) {

            val jobScheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            this.refreshTokenJob = null
        }
    }

    fun scheduleTimer(offset: Long) {
        lastJobStart = Instant.now().toEpochMilli()
        if (appLifecycle.appInForeground) {
            Log.d(TAG, "[foreground] Start Timer task (${offset} ms)")

            this.timerTask = Timer().schedule(offset) {
                Log.d(
                    TAG,
                    "[foreground] Job started, (${
                        Instant.now().toEpochMilli() - lastJobStart
                    } ms)"
                )
                refreshTokenIfNeeded.trigger()
            }

        } else {
            Log.d(TAG, "[background] Start Job Scheduler task (${offset} ms)")
            val jobScheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            // Schedule the job
            val jobInfo = JobInfo.Builder(
                JOB_ID, ComponentName(context, RefreshTokenJobService::class.java)
            )
                .setMinimumLatency(offset / 2) // Schedule the job to run after the offset
                .setOverrideDeadline(offset) // Add a buffer to the deadline
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Require network
                .setBackoffCriteria(10000, JobInfo.BACKOFF_POLICY_LINEAR)
                .build()
            this.refreshTokenJob = jobInfo
            jobScheduler.schedule(jobInfo)
        }
    }

    companion object {
        private val TAG = FronteggRefreshTokenTimer::class.java.simpleName
        const val JOB_ID = 1234 // Unique ID for the JobService
        var lastJobStart: Long = Instant.now().toEpochMilli()
    }

}