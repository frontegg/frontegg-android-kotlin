package com.frontegg.android

import android.content.Context
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.VisibleForTesting
import com.frontegg.android.FronteggApp.Companion.init
import com.frontegg.android.FronteggApp.Companion.initWithRegions
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.FronteggAppService
import com.frontegg.android.utils.FronteggConstantsProvider
import com.frontegg.android.init.ConfigCache
import com.frontegg.android.init.ConfigCache.RegionsInitFlags
import com.frontegg.android.utils.isActivityEnabled
import com.frontegg.debug.AndroidDebugConfigurationChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.frontegg.android.utils.DispatcherProvider
import com.frontegg.android.utils.DefaultDispatcherProvider

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
        if (FronteggApp.instance == null) {
            val constants = FronteggConstantsProvider.fronteggConstants(this)
            Log.d(FronteggApp.TAG, "Initializing Frontegg SDK with constants: ${constants.toMap()}")
            var mainClassActivityClass: Class<*>? = null
            try {
                mainClassActivityClass =
                    if (constants.mainActivityClass != null) Class.forName(constants.mainActivityClass) else null
            } catch (e: ClassNotFoundException) {
                Log.e(
                    FronteggApp.TAG,
                    "mainActivityClass (${constants.mainActivityClass}) Not Found",
                    e
                )
            }

            init(
                fronteggDomain = constants.baseUrl,
                clientId = constants.clientId,
                context = this,
                applicationId = constants.applicationId,
                useAssetsLinks = constants.useAssetsLinks,
                useChromeCustomTabs = constants.useChromeCustomTabs,
                mainActivityClass = mainClassActivityClass,
                deepLinkScheme = constants.deepLinkScheme,
                useDiskCacheWebview = constants.useDiskCacheWebview,
            )
        }

        return FronteggApp.instance!!
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
        if (FronteggApp.instance == null) {
            this.fronteggApp
        }

        return FronteggApp.instance!!.auth
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
        val TAG = FronteggApp::class.java.simpleName


        @VisibleForTesting
        internal var instance: FronteggApp? = null

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
        @VisibleForTesting
        internal fun init(
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
            runDebugChecksSafe(context, fronteggDomain, clientId)
        }


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

                    runDebugChecksSafe(context, regionConfig.baseUrl, regionConfig.clientId)

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
            // Persist parameters to allow retry when network becomes available
            ConfigCache.saveLastRegionsInit(
                context = context,
                regions = regions,
                flags = RegionsInitFlags(
                    useAssetsLinks = useAssetsLinks,
                    useChromeCustomTabs = useChromeCustomTabs,
                    mainActivityClassName = mainActivityClass?.name,
                    useDiskCacheWebview = useDiskCacheWebview
                )
            )
            return newInstance
        }

        private var dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        fun setDispatcherProviderForTesting(provider: DispatcherProvider) {
            dispatcherProvider = provider
        }

        private fun runDebugChecksSafe(context: Context, baseUrl: String, clientId: String) {
            // Only run when network is validated; execute in SupervisorJob to isolate failures
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val n = cm.activeNetwork
            val caps = if (n != null) cm.getNetworkCapabilities(n) else null
            val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            if (!validated) {
                Log.d(TAG, "Skip debug checks: network not validated")
                return
            }

            CoroutineScope(SupervisorJob() + dispatcherProvider.io).launch {
                try {
                    AndroidDebugConfigurationChecker(context, baseUrl, clientId).runChecks()
                } catch (e: java.net.UnknownHostException) {
                    Log.d(TAG, "Debug checks offline: ${e.message}")
                } catch (e: java.io.IOException) {
                    Log.d(TAG, "Debug checks transient IO: ${e.message}")
                } catch (t: Throwable) {
                    Log.d(TAG, "Debug checks error ignored: ${t.message}")
                }
            }
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
