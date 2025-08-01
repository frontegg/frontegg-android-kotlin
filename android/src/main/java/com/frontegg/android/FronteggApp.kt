package com.frontegg.android

import android.content.Context
import android.util.Log
import com.frontegg.android.FronteggApp.Companion.initWithRegions
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.FronteggAppService
import com.frontegg.android.utils.constants
import com.frontegg.android.utils.isActivityEnabled
import com.frontegg.debug.AndroidDebugConfigurationChecker

private var instance: FronteggApp? = null

/**
 * Initializes [FronteggApp].
 *
 * @param fronteggDomain The Frontegg domain. Can be found at [portal.frontegg.com].
 * @param clientId The Frontegg Client ID. Can be found at [portal.frontegg.com].
 * @param context The application context.
 * @param applicationId The ID of the Frontegg application. Can be found at [portal.frontegg.com].
 * @param useAssetsLinks Whether the Frontegg SDK should use asset links (default: `false`).
 * @param useChromeCustomTabs Whether the Frontegg SDK should use Chrome Custom Tabs (default: `false`).
 * @param mainActivityClass The Activity to navigate to after authorization (default: `null`).
 * @param useDiskCacheWebview Whether the Frontegg SDK should use disk cache for WebView (default: `false`).
 */
private fun init(
    fronteggDomain: String,
    clientId: String,
    context: Context,
    applicationId: String? = null,
    useAssetsLinks: Boolean = false,
    useChromeCustomTabs: Boolean = false,
    mainActivityClass: Class<*>? = null,
    deepLinkScheme: String? = null,
    useDiskCacheWebview: Boolean = false,
) {
    val baseUrl: String = if (fronteggDomain.startsWith("https")) {
        fronteggDomain
    } else {
        "https://$fronteggDomain"
    }

    val isEmbeddedMode = context.isActivityEnabled(EmbeddedAuthActivity::class.java.name)

    instance = FronteggAppService(
        context = context,
        baseUrl = baseUrl,
        clientId = clientId,
        deepLinkScheme = deepLinkScheme,
        applicationId = applicationId,
        isEmbeddedMode = isEmbeddedMode,
        useAssetsLinks = useAssetsLinks,
        useChromeCustomTabs = useChromeCustomTabs,
        mainActivityClass = mainActivityClass,
        useDiskCacheWebview = useDiskCacheWebview
    )

    val debugChecker = AndroidDebugConfigurationChecker(context, fronteggDomain, clientId)
    debugChecker.runChecks()
}

/**
 * Lazily initializes and returns the singleton [FronteggApp] instance for this [Context].
 *
 * Initialization parameters are retrieved from the `BuildConfig` class using reflection,
 * based on the package name and launch activity. This includes configuration such as:
 * - `FRONTEGG_DOMAIN`
 * - `FRONTEGG_CLIENT_ID`
 * - `FRONTEGG_APPLICATION_ID`
 * - `FRONTEGG_USE_ASSETS_LINKS`
 * - `FRONTEGG_USE_CHROME_CUSTOM_TABS`
 * - `FRONTEGG_DEEP_LINK_SCHEME`
 * - `FRONTEGG_USE_DISK_CACHE_WEBVIEW`
 * - `FRONTEGG_MAIN_ACTIVITY_CLASS`
 *
 * These constants are wrapped in a [FronteggConstants] object and used to initialize
 * the [FronteggApp] if it has not already been initialized.
 *
 * @receiver [Context] used to resolve package and resources.
 * @return A ready-to-use [FronteggApp] singleton instance.
 */
val Context.fronteggApp: FronteggApp
    get() {
        val constants = this.constants
        Log.d("SAME", constants.toMap().toString())
        if (instance == null) {
            init(
                fronteggDomain = constants.baseUrl,
                clientId = constants.clientId,
                context = this,
                applicationId = constants.applicationId,
                useAssetsLinks = constants.useAssetsLinks,
                useChromeCustomTabs = constants.useChromeCustomTabs,
                mainActivityClass = if (constants.mainActivityClass != null) Class.forName(constants.mainActivityClass) else null,
                deepLinkScheme = constants.deepLinkScheme,
                useDiskCacheWebview = constants.useDiskCacheWebview,
            )
        }

        return instance!!
    }

/**
 * Provides access to the [FronteggAuth] component associated with the [FronteggApp].
 *
 * Ensures that the [FronteggApp] is initialized before returning the `auth` module.
 * Initialization parameters are loaded from the app's `BuildConfig` using reflection.
 *
 * @receiver [Context] used to access the [FronteggApp].
 * @return The [FronteggAuth] instance for authentication flows.
 */
val Context.fronteggAuth: FronteggAuth
    get() {
        if (instance == null) {
            this.fronteggApp
        }

        return instance!!.auth
    }

/**
 * An initialization class of Frontegg SDK. Use [init] or [initWithRegions] static methods
 * to initialize the [FronteggApp].
 *
 * @property auth an authentication interface.
 */
interface FronteggApp {
    val auth: FronteggAuth

    companion object {
        /**
         * Initialization method of [FronteggApp] for multi-regions.
         * @param regions A list of [RegionConfig]. Could find at [portal.frontegg.com];
         * @param context The application context.
         * @param useAssetsLinks Whether the Frontegg SDK should use asset links (default: `false`).
         * @param useChromeCustomTabs Whether the Frontegg SDK should use Chrome Custom Tabs (default: `false`).
         * @param mainActivityClass The Activity to navigate to after authorization (default: `null`).
         * @param useDiskCacheWebview Whether the Frontegg SDK should use disk cache for WebView (default: `false`).
         */
        fun initWithRegions(
            regions: List<RegionConfig>,
            context: Context,
            useAssetsLinks: Boolean = false,
            useChromeCustomTabs: Boolean = false,
            mainActivityClass: Class<*>? = null,
            useDiskCacheWebview: Boolean = false,
        ): FronteggApp {

            val isEmbeddedMode = context.isActivityEnabled(EmbeddedAuthActivity::class.java.name)
            val selectedRegion = CredentialManager(context).getSelectedRegion()
            if (selectedRegion != null) {
                val regionConfig = regions.find { it.key == selectedRegion }

                if (regionConfig != null) {
                    val newInstance = FronteggAppService(
                        context = context,
                        baseUrl = regionConfig.baseUrl,
                        clientId = regionConfig.clientId,
                        applicationId = regionConfig.applicationId,
                        isEmbeddedMode = isEmbeddedMode,
                        regions = regions,
                        selectedRegion = regionConfig,
                        useAssetsLinks = useAssetsLinks,
                        useChromeCustomTabs = useChromeCustomTabs,
                        mainActivityClass = mainActivityClass,
                        useDiskCacheWebview = useDiskCacheWebview
                    )
                    instance = newInstance

                    val debugChecker = AndroidDebugConfigurationChecker(
                        context,
                        regionConfig.baseUrl,
                        regionConfig.clientId
                    )
                    debugChecker.runChecks()

                    return newInstance
                }
            }
            val newInstance = FronteggAppService(
                context = context,
                baseUrl = "",
                clientId = "",
                applicationId = null,
                isEmbeddedMode = isEmbeddedMode,
                regions = regions,
                useAssetsLinks = useAssetsLinks,
                useChromeCustomTabs = useChromeCustomTabs,
                mainActivityClass = mainActivityClass,
                useDiskCacheWebview = useDiskCacheWebview
            )
            instance = newInstance
            return newInstance
        }
    }

    /**
     * The switch method of the region by [regionKey].
     * To use this method you should initialize the [FronteggApp] with
     * the [initWithRegions] static method.
     * @param regionKey is one of the key of regions you pass into [initWithRegions].
     * @throws RuntimeException if you didn't pass any regions to [initWithRegions]
     * or [regionKey] does not exist in the regions.
     */
    fun initWithRegion(regionKey: String)
}
