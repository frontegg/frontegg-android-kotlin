package com.frontegg.android.services

import android.content.Context
import android.util.Log
import com.frontegg.android.FronteggApp

object FronteggAppReInitializer {
    private const val TAG = "FronteggAppReInitializer"

    private const val PREF_NAME = "frontegg_init_data"
    private const val KEY_DOMAIN = "domain"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_APP_ID = "app_id"
    private const val KEY_USE_ASSETS_LINKS = "use_assets_links"
    private const val KEY_USE_CUSTOM_TABS = "use_chrome_custom_tabs"
    private const val KEY_DEEP_LINK_SCHEME = "deep_link_scheme"
    private const val MAIN_ACTIVITY_CLASS = "main_activity_class"

    /**
     * Saves initialization parameters for Frontegg SDK into SharedPreferences
     * so they can be restored later (e.g. after process death).
     *
     * @param context Application context.
     * @param fronteggDomain Frontegg domain.
     * @param clientId Frontegg client ID.
     * @param applicationId Optional application ID.
     * @param useAssetsLinks Whether to use Android App Links.
     * @param useChromeCustomTabs Whether to use Chrome Custom Tabs for auth.
     * @param mainActivityClass The class of the main activity (saved by canonical name).
     * @param deepLinkScheme Optional deep link scheme.
     */
    fun saveInitData(
        context: Context,
        fronteggDomain: String,
        clientId: String,
        applicationId: String? = null,
        useAssetsLinks: Boolean = false,
        useChromeCustomTabs: Boolean = false,
        mainActivityClass: Class<*>? = null,
        deepLinkScheme: String? = null,
    ) {
        Log.d(TAG, "Saving Frontegg init data")

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DOMAIN, fronteggDomain)
            .putString(KEY_CLIENT_ID, clientId)
            .putString(KEY_APP_ID, applicationId)
            .putBoolean(KEY_USE_ASSETS_LINKS, useAssetsLinks)
            .putBoolean(KEY_USE_CUSTOM_TABS, useChromeCustomTabs)
            .putString(KEY_DEEP_LINK_SCHEME, deepLinkScheme)
            .putString(MAIN_ACTIVITY_CLASS, mainActivityClass?.canonicalName)
            .apply()

        Log.d(TAG, "Frontegg init data saved successfully")
    }

    /**
     * Attempts to re-initialize the Frontegg SDK if it has not been initialized yet.
     * Reads the previously saved parameters from SharedPreferences.
     *
     * @param context Application context.
     */
    fun tryReinitialize(context: Context) {
        if (FronteggApp.isInitialized()) {
            Log.d(TAG, "FronteggApp is already initialized. Skipping re-initialization.")
            return
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val domain = prefs.getString(KEY_DOMAIN, null)
        val clientId = prefs.getString(KEY_CLIENT_ID, null)

        if (domain.isNullOrEmpty() || clientId.isNullOrEmpty()) {
            Log.w(TAG, "Missing required init data: domain or clientId is null")
            return
        }

        val appId = prefs.getString(KEY_APP_ID, null)
        val useAssetsLinks = prefs.getBoolean(KEY_USE_ASSETS_LINKS, false)
        val useChromeTabs = prefs.getBoolean(KEY_USE_CUSTOM_TABS, false)
        val deepLinkScheme = prefs.getString(KEY_DEEP_LINK_SCHEME, null)
        val className = prefs.getString(MAIN_ACTIVITY_CLASS, null)

        val mainActivityClass: Class<*>? = try {
            className?.let { Class.forName(it) }
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Main activity class not found: $className", e)
            null
        }

        Log.d(TAG, "Reinitializing FronteggApp with saved data")
        FronteggApp.init(
            context = context,
            fronteggDomain = domain,
            clientId = clientId,
            applicationId = appId,
            useAssetsLinks = useAssetsLinks,
            useChromeCustomTabs = useChromeTabs,
            mainActivityClass = mainActivityClass,
            deepLinkScheme = deepLinkScheme
        )

        Log.d(TAG, "FronteggApp re-initialized successfully")
    }
}
