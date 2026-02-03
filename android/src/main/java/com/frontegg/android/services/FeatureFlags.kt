package com.frontegg.android.services

import android.content.Context
import android.util.Log
import com.frontegg.android.fronteggAuth
import com.frontegg.android.utils.SentryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException

/**
 * Feature flags manager for Frontegg SDK
 */
class FeatureFlags private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: FeatureFlags? = null
        
        fun getInstance(): FeatureFlags {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FeatureFlags().also { INSTANCE = it }
            }
        }
        
        private const val TAG = "FeatureFlags"
    }

    private val flags = mutableMapOf<String, Boolean>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(com.frontegg.android.utils.DefaultDispatcherProvider.io + SupervisorJob())

    /**
     * Check if a feature flag is enabled
     * @param flagName The name of the feature flag
     * @return true if the flag is enabled, false otherwise
     */
    suspend fun isOn(flagName: String): Boolean {
        return mutex.withLock {
            flags[flagName] ?: false
        }
    }

    /**
     * Check if a feature flag is enabled (synchronous version)
     * @param flagName The name of the feature flag
     * @return true if the flag is enabled, false otherwise
     */
    fun isOnSync(flagName: String): Boolean {
        return flags[flagName] ?: false
    }

    /**
     * Load feature flags from the server
     * @param context Android context
     */
    fun loadFlags(context: Context) {
        scope.launch {
            try {
                val authService = context.fronteggAuth as FronteggAuthService
                val flagsJson = authService.getFeatureFlags()
                val flagsMap = parseFlags(flagsJson)
                
                mutex.withLock {
                    flags.clear()
                    flags.putAll(flagsMap)
                }
                
                Log.d(TAG, "Feature flags loaded: $flagsMap")
                
                // Enable Sentry logging if the feature flag is on
                val enableLogging = flagsMap[SentryHelper.MOBILE_ENABLE_LOGGING_FLAG] ?: false
                SentryHelper.enableFromFeatureFlag(enableLogging)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load feature flags", e)
            }
        }
    }

    /**
     * Parse feature flags from JSON string
     * @param jsonString JSON string containing feature flags
     * @return Map of flag names to boolean values
     */
    private fun parseFlags(jsonString: String): Map<String, Boolean> {
        return try {
            val json = JSONObject(jsonString)
            val result = mutableMapOf<String, Boolean>()
            
            json.keys().forEach { key ->
                val value = json.optString(key, "off").trim().lowercase()
                result[key] = when (value) {
                    "on" -> true
                    "off" -> false
                    else -> false
                }
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse feature flags JSON", e)
            emptyMap()
        }
    }

    /**
     * Get all loaded feature flags
     * @return Map of all feature flags
     */
    fun getAllFlags(): Map<String, Boolean> {
        return flags.toMap()
    }
}
