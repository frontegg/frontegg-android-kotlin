package com.frontegg.android.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.i(TAG, "Exact alarms permission not granted; requesting permission")
            
            val permissionIntent = getRequestExactAlarmPermissionIntent()
            if (permissionIntent != null) {
                try {
                    permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(permissionIntent)
                    Log.d(TAG, "Started permission request dialog for SCHEDULE_EXACT_ALARM")
                    
                    if (alarmManager.canScheduleExactAlarms()) {
                        Log.d(TAG, "Permission granted immediately; proceeding with alarm scheduling")
                    } else {
                        Log.i(TAG, "Permission not yet granted; JobScheduler will be used as fallback")
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start permission request: ${e.message}", e)
                    return
                }
            } else {
                Log.i(TAG, "Cannot request permission on this Android version; relying on JobScheduler")
                return
            }
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
            Log.d(TAG, "Alarm scheduled successfully")
        } catch (e: SecurityException) {
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
            .setMinimumLatency(offset)
            .setOverrideDeadline(offset)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(10000, JobInfo.BACKOFF_POLICY_LINEAR)
            .build()
    }

    companion object {
        private val TAG = FronteggRefreshTokenTimer::class.java.simpleName
        const val JOB_ID = 1234 // Unique ID for the JobService
        const val ALARM_REQUEST_CODE = 4321
        var lastJobStart: Long = Instant.now().toEpochMilli()

        /**
         * Checks if the app can schedule exact alarms.
         * On Android 12+ (API 31+), this requires the SCHEDULE_EXACT_ALARM permission.
         *
         * @param context The application context
         * @return true if exact alarms can be scheduled, false otherwise
         */
        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            return alarmManager?.canScheduleExactAlarms() ?: false
        }

        /**
         * Returns an Intent that can be used to request the SCHEDULE_EXACT_ALARM permission.
         * This should be used with startActivity() from an Activity context.
         *
         * Example usage:
         * ```
         * if (!FronteggRefreshTokenTimer.canScheduleExactAlarms(context)) {
         *     startActivity(FronteggRefreshTokenTimer.getRequestExactAlarmPermissionIntent())
         * }
         * ```
         *
         * @return Intent to request exact alarm permission, or null on Android 11 and below
         */
        fun getRequestExactAlarmPermissionIntent(): Intent? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            } else {
                null
            }
        }
    }
}
