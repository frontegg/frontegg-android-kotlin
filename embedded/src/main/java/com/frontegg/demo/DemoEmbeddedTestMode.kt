package com.frontegg.demo

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * E2E / instrumented-test hooks for the embedded demo (parity with Swift DemoEmbeddedTestMode).
 * Tests write [BOOTSTRAP_FILE_NAME] before launching the app; [App] consumes it on startup.
 */
object DemoEmbeddedTestMode {
    private const val TAG = "DemoEmbeddedTestMode"
    const val BOOTSTRAP_FILE_NAME = "e2e_embedded_bootstrap.json"

    const val REQUEST_AUTHORIZE_REFRESH_TOKEN = "signup-refresh-token"
    const val EMBEDDED_PASSWORD_EMAIL = "test@frontegg.com"
    const val EMBEDDED_SAML_EMAIL = "test@saml-domain.com"
    const val EMBEDDED_OIDC_EMAIL = "test@oidc-domain.com"

    fun customSsoUrl(baseUrl: String): String = "${baseUrl.trimEnd('/')}/idp/custom-sso"
    fun directSocialLoginUrl(baseUrl: String): String =
        "${baseUrl.trimEnd('/')}/idp/social/mock-social-provider"
    private const val PREFS = "demo_embedded_e2e_runtime"
    private const val KEY_OFFLINE_OVERRIDE = "enable_offline_mode_override"

    @Volatile
    var isEnabled: Boolean = false
        private set

    /** When non-null, overrides default offline UI behavior (mirrors FRONTEGG_E2E_ENABLE_OFFLINE_MODE). */
    var enableOfflineModeOverride: Boolean? = null
        private set

    data class BootstrapConfig(
        val baseUrl: String,
        val clientId: String,
        val resetState: Boolean,
        val forceNetworkPathOffline: Boolean,
        val enableOfflineMode: Boolean?,
    )

    /**
     * Read and remove bootstrap file written by androidTest. Safe to call on every process start.
     */
    fun consumeBootstrapIfPresent(context: Context): BootstrapConfig? {
        val file = File(context.filesDir, BOOTSTRAP_FILE_NAME)
        if (!file.exists()) {
            isEnabled = false
            loadRuntimeOverrides(context)
            return null
        }
        return try {
            val json = JSONObject(file.readText())
            file.delete()
            val enableOffline = when {
                !json.has("enableOfflineMode") || json.isNull("enableOfflineMode") -> null
                else -> json.optBoolean("enableOfflineMode")
            }
            val cfg = BootstrapConfig(
                baseUrl = json.getString("baseUrl"),
                clientId = json.getString("clientId"),
                resetState = json.optBoolean("resetState", true),
                forceNetworkPathOffline = json.optBoolean("forceNetworkPathOffline", false),
                enableOfflineMode = enableOffline,
            )
            isEnabled = true
            persistRuntimeOfflineOverride(context, cfg.enableOfflineMode)
            loadRuntimeOverrides(context)
            Log.d(TAG, "Consumed E2E bootstrap: baseUrl=${cfg.baseUrl} reset=${cfg.resetState}")
            cfg
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse E2E bootstrap", e)
            runCatching { file.delete() }
            isEnabled = false
            null
        }
    }

    private fun persistRuntimeOfflineOverride(context: Context, mode: Boolean?) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (mode == null) {
            sp.remove(KEY_OFFLINE_OVERRIDE)
        } else {
            sp.putBoolean(KEY_OFFLINE_OVERRIDE, mode)
        }
        sp.apply()
        enableOfflineModeOverride = mode
    }

    private fun loadRuntimeOverrides(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.contains(KEY_OFFLINE_OVERRIDE)) {
            enableOfflineModeOverride = sp.getBoolean(KEY_OFFLINE_OVERRIDE, true)
        } else {
            enableOfflineModeOverride = null
        }
    }

    /** Whether the demo should mimic Swift's "offline mode enabled" product flag. */
    fun isOfflineModeFeatureEnabled(context: Context): Boolean {
        if (!isEnabled) return true
        return enableOfflineModeOverride ?: true
    }
}
