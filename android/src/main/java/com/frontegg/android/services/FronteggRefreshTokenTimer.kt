package com.frontegg.android.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
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
    private var alarmIntent: PendingIntent? = null

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
        cancelAlarm()
    }

    fun scheduleTimer(offset: Long) {
        lastJobStart = Instant.now().toEpochMilli()

        // Foreground: simple in-process Timer
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
            return
        }

        // Background: dual scheduling – AlarmManager (Doze-friendly) + JobScheduler fallback
        Log.d(TAG, "[background] Start Alarm + JobScheduler tasks (${offset} ms)")

        scheduleAlarm(offset)

        val jobScheduler =
            context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val jobInfo = buildJobInfo(offset)
        this.refreshTokenJob = jobInfo
        jobScheduler.schedule(jobInfo)
    }

    private fun scheduleAlarm(offset: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: run {
                Log.w(TAG, "AlarmManager not available; skipping alarm scheduling")
                return
            }

        val intent = Intent(context, RefreshTokenAlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)

        val pending = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            flags
        )

        val triggerAtMillis = System.currentTimeMillis() + offset

        try {
            // Use Doze-friendly exact alarm; falls back gracefully on older devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d(TAG, "Scheduling exact alarm with setExactAndAllowWhileIdle at $triggerAtMillis")
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pending
                )
            } else {
                Log.d(TAG, "Scheduling exact alarm with setExact at $triggerAtMillis")
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pending
                )
            }
            alarmIntent = pending
        } catch (e: SecurityException) {
            // Devices that disallow exact alarms without permission (Android 12+) may throw;
            // in that case we still have JobScheduler as a fallback.
            Log.w(TAG, "Failed to schedule exact alarm for refresh: ${e.message}", e)
        }
    }

    private fun cancelAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val pending = alarmIntent
        if (alarmManager != null && pending != null) {
            try {
                alarmManager.cancel(pending)
                Log.d(TAG, "Cancelled refresh alarm")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel refresh alarm: ${e.message}", e)
            } finally {
                alarmIntent = null
            }
        }
    }

    @VisibleForTesting
    internal fun buildJobInfo(offset: Long): JobInfo {
        return JobInfo.Builder(
            JOB_ID,
            ComponentName(context, RefreshTokenJobService::class.java),
        )
            .setMinimumLatency(offset) // Schedule the job to run after the offset
            .setOverrideDeadline(offset) // Add a buffer to the deadline
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Require network
            .setBackoffCriteria(10000, JobInfo.BACKOFF_POLICY_LINEAR)
            .build()
    }

    companion object {
        private val TAG = FronteggRefreshTokenTimer::class.java.simpleName
        const val JOB_ID = 1234 // Unique ID for the JobService
        const val ALARM_REQUEST_CODE = 4321
        var lastJobStart: Long = Instant.now().toEpochMilli()
    }
}
