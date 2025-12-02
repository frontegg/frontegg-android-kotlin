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
    private var enableSessionPerTenant: Boolean = false
    
    fun setEnableSessionPerTenant(enabled: Boolean) {
        this.enableSessionPerTenant = enabled
    }
    
    private fun getTenantScopedKey(key: String, tenantId: String?): String {
        if (!enableSessionPerTenant || tenantId == null) {
            return key
        }
        return "${key}_tenant_$tenantId"
    }
    
    fun trackSessionStart(tenantId: String? = null) {
        val currentTime = System.currentTimeMillis()
        val sessionStartKey = getTenantScopedKey(KEY_SESSION_START_TIME, tenantId)
        val lastRefreshKey = getTenantScopedKey(KEY_LAST_REFRESH_TIME, tenantId)
        
        prefs.edit()
            .putLong(sessionStartKey, currentTime)
            .putLong(lastRefreshKey, currentTime)
            .apply()
    }
    
    fun trackTokenRefresh(tenantId: String? = null) {
        val currentTime = System.currentTimeMillis()
        val lastRefreshKey = getTenantScopedKey(KEY_LAST_REFRESH_TIME, tenantId)
        
        prefs.edit()
            .putLong(lastRefreshKey, currentTime)
            .apply()
    }
    
    /**
     * Check if we have session data
     * @return true if we have session start time
     */
    fun hasSessionData(tenantId: String? = null): Boolean {
        val sessionStartKey = getTenantScopedKey(KEY_SESSION_START_TIME, tenantId)
        val sessionStartTime = prefs.getLong(sessionStartKey, 0L)
        return sessionStartTime != 0L
    }

    /**
     * Get session start time
     * @return Session start time in milliseconds since epoch, or 0 if not tracked
     */
    fun getSessionStartTime(tenantId: String? = null): Long {
        val sessionStartKey = getTenantScopedKey(KEY_SESSION_START_TIME, tenantId)
        return prefs.getLong(sessionStartKey, 0L)
    }
    
    /**
     * Get last refresh time
     * @return Last refresh time in milliseconds since epoch, or 0 if not tracked
     */
    fun getLastRefreshTime(tenantId: String? = null): Long {
        val lastRefreshKey = getTenantScopedKey(KEY_LAST_REFRESH_TIME, tenantId)
        return prefs.getLong(lastRefreshKey, 0L)
    }
    
    fun clearSessionData(tenantId: String? = null) {
        val sessionStartKey = getTenantScopedKey(KEY_SESSION_START_TIME, tenantId)
        val lastRefreshKey = getTenantScopedKey(KEY_LAST_REFRESH_TIME, tenantId)
        
        prefs.edit()
            .remove(sessionStartKey)
            .remove(lastRefreshKey)
            .apply()
    }
    
    /**
     * Get session duration in milliseconds
     * @return Session duration in milliseconds, or 0 if session not started
     */
    fun getSessionDuration(tenantId: String? = null): Long {
        val sessionStartTime = getSessionStartTime(tenantId)
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
    fun hasMinimumSessionDuration(minimumDurationMs: Long, tenantId: String? = null): Boolean {
        return getSessionDuration(tenantId) >= minimumDurationMs
    }
}