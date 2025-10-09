package com.frontegg.android.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.frontegg.android.services.FronteggInnerStorage

/**
 * Web authenticator for OAuth flows
 */
class WebAuthenticator private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: WebAuthenticator? = null
        
        fun getInstance(): WebAuthenticator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebAuthenticator().also { INSTANCE = it }
            }
        }
        
        private const val TAG = "WebAuthenticator"
    }

    private var currentSession: WebAuthSession? = null

    /**
     * Start OAuth authentication flow
     */
    suspend fun start(
        context: Context,
        authURL: String,
        ephemeralSession: Boolean = true,
        completionHandler: (String?, Exception?) -> Unit
    ) {
        val session = WebAuthSession(
            context = context,
            authURL = authURL,
            ephemeralSession = ephemeralSession,
            completionHandler = completionHandler
        )
        
        currentSession = session
        session.start()
    }

    /**
     * Cancel current session
     */
    fun cancel() {
        currentSession?.cancel()
        currentSession = null
    }

    /**
     * Get current session
     */
    val session: WebAuthSession?
        get() = currentSession

    /**
     * Web authentication session
     */
    inner class WebAuthSession(
        private val context: Context,
        private val authURL: String,
        private val ephemeralSession: Boolean,
        private val completionHandler: (String?, Exception?) -> Unit
    ) {
        private var isActive = false

        fun start() {
            if (isActive) return
            
            isActive = true
            Log.d(TAG, "Starting web authentication with URL: $authURL")
            
            try {
                val storage = FronteggInnerStorage()
                
                if (storage.useChromeCustomTabs) {
                    startWithCustomTabs()
                } else {
                    startWithBrowser()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start web authentication", e)
                completionHandler(null, e)
            }
        }

        private fun startWithCustomTabs() {
            try {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                
                val intent = customTabsIntent.intent.apply {
                    data = Uri.parse(authURL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                context.startActivity(intent)
                
                // Note: In a real implementation, you would need to handle the callback
                // This is a simplified version for demonstration
                Log.d(TAG, "Custom tabs launched")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch custom tabs", e)
                completionHandler(null, e)
            }
        }

        private fun startWithBrowser() {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authURL)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                context.startActivity(intent)
                Log.d(TAG, "Browser launched")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch browser", e)
                completionHandler(null, e)
            }
        }

        fun cancel() {
            if (isActive) {
                isActive = false
                Log.d(TAG, "Web authentication session cancelled")
            }
        }

    }
}
