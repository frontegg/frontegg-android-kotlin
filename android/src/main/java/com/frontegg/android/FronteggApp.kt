package com.frontegg.android

import android.annotation.SuppressLint
import android.content.Context
import com.frontegg.android.FronteggApp.Companion.init
import com.frontegg.android.FronteggApp.Companion.initWithRegions
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.exceptions.FronteggException.Companion.FRONTEGG_APP_MUST_BE_INITIALIZED
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.FronteggAppService
import com.frontegg.android.utils.isActivityEnabled
import com.frontegg.debug.AndroidDebugConfigurationChecker

/**
 * An initialization class of Frontegg SDK. Use [init] or [initWithRegions] static methods
 * to initialize the [FronteggApp]. To get access to an instance use the [getInstance] method.
 *
 * @property auth an authentication interface.
 */
interface FronteggApp {
    val auth: FronteggAuth

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var instance: FronteggApp? = null

        /**
         * Provide [FronteggApp] instance.
         * @return [FronteggApp] object if was initialized.
         * @throws FronteggException with the message `frontegg.error.app_must_be_initialized`
         * if FronteggApp wasn't initialized before. Use `init` or `initWithRegions` static methods
         * to initialize the FronteggApp.
         */
        fun getInstance(): FronteggApp {
            if (instance == null) {
                throw FronteggException(FRONTEGG_APP_MUST_BE_INITIALIZED)
            }
            return instance!!
        }

        /**
         * Checks whether the [FronteggApp] has been initialized.
         *
         * This method returns `true` if the [FronteggApp.init] or [FronteggApp.initWithRegions]
         * method has been called successfully and an instance is available.
         *
         * @return `true` if the SDK has been initialized, `false` otherwise.
         */
        fun isInitialized(): Boolean {
            return instance != null
        }

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
         */
        fun init(
            fronteggDomain: String,
            clientId: String,
            context: Context,
            applicationId: String? = null,
            useAssetsLinks: Boolean = false,
            useChromeCustomTabs: Boolean = false,
            mainActivityClass: Class<*>? = null,
            deepLinkScheme: String? = null,
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
                mainActivityClass = mainActivityClass
            )
            
            val debugChecker = AndroidDebugConfigurationChecker(context, fronteggDomain, clientId)
            debugChecker.runChecks()
            
        }

        /**
         * Initialization method of [FronteggApp] for multi-regions.
         * @param regions A list of [RegionConfig]. Could find at [portal.frontegg.com];
         * @param context The application context.
         * @param useAssetsLinks Whether the Frontegg SDK should use asset links (default: `false`).
         * @param useChromeCustomTabs Whether the Frontegg SDK should use Chrome Custom Tabs (default: `false`).
         * @param mainActivityClass The Activity to navigate to after authorization (default: `null`).
         */
        fun initWithRegions(
            regions: List<RegionConfig>,
            context: Context,
            useAssetsLinks: Boolean = false,
            useChromeCustomTabs: Boolean = false,
            mainActivityClass: Class<*>? = null
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
                        mainActivityClass = mainActivityClass
                    )
                    instance = newInstance
                    
                    val debugChecker = AndroidDebugConfigurationChecker(context, regionConfig.baseUrl, regionConfig.clientId)
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
                mainActivityClass = mainActivityClass
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
