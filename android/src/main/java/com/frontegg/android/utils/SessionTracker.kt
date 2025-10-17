package com.frontegg.android.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Utility class for tracking session lifetime.
 * Tracks session start time and last refresh time for basic session management.
 */
class SessionTracker(private val context: Context) {
    
    companion object {
        private const val TAG = "SessionTracker"
        private const val PREFS_NAME = "frontegg_session_tracker"
        private const val KEY_SESSION_START_TIME = "session_start_time"
        private const val KEY_LAST_REFRESH_TIME = "last_refresh_time"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Track the start of a new session (call this on successful login)
     */
    fun trackSessionStart() {
        val currentTime = System.currentTimeMillis()
        
        prefs.edit()
            .putLong(KEY_SESSION_START_TIME, currentTime)
            .putLong(KEY_LAST_REFRESH_TIME, currentTime)
            .apply()
        
        Log.d(TAG, "Session started at: $currentTime")
    }
    
    /**
     * Track a successful token refresh
     */
    fun trackTokenRefresh() {
        val currentTime = System.currentTimeMillis()
        
        prefs.edit()
            .putLong(KEY_LAST_REFRESH_TIME, currentTime)
            .apply()
        
        Log.d(TAG, "Token refreshed at: $currentTime")
    }
    
    /**
     * Check if we have session data
     * @return true if we have session start time
     */
    fun hasSessionData(): Boolean {
        val sessionStartTime = prefs.getLong(KEY_SESSION_START_TIME, 0L)
        return sessionStartTime != 0L
    }

    /**
     * Get session start time
     * @return Session start time in milliseconds since epoch, or 0 if not tracked
     */
    fun getSessionStartTime(): Long {
        return prefs.getLong(KEY_SESSION_START_TIME, 0L)
    }
    
    /**
     * Get last refresh time
     * @return Last refresh time in milliseconds since epoch, or 0 if not tracked
     */
    fun getLastRefreshTime(): Long {
        return prefs.getLong(KEY_LAST_REFRESH_TIME, 0L)
    }
    
    /**
     * Clear session tracking data (call this on logout)
     */
    fun clearSessionData() {
        prefs.edit()
            .remove(KEY_SESSION_START_TIME)
            .remove(KEY_LAST_REFRESH_TIME)
            .apply()
        
        Log.d(TAG, "Session data cleared")
    }
    
    /**
     * Get session duration in milliseconds
     * @return Session duration in milliseconds, or 0 if session not started
     */
    fun getSessionDuration(): Long {
        val sessionStartTime = prefs.getLong(KEY_SESSION_START_TIME, 0L)
        if (sessionStartTime == 0L) {
            return 0L
        }
        
        return System.currentTimeMillis() - sessionStartTime
    }
    
    /**
     * Check if session has been active for a minimum duration
     * @param minimumDurationMs Minimum duration in milliseconds
     * @return true if session has been active for at least the minimum duration
     */
    fun hasMinimumSessionDuration(minimumDurationMs: Long): Boolean {
        return getSessionDuration() >= minimumDurationMs
    }
}