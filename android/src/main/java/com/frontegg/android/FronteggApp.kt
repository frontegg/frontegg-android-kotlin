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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
         * Initialization method of [FronteggApp].
         * @param fronteggDomain is the Frontegg domain. Could be found at portal.frontegg.com;
         * @param clientId is the Frontegg Client ID. Could be found at portal.frontegg.com;
         * @param context is the application context;
         * @param applicationId is the id of Frontegg application. Could be found at portal.frontegg.com;
         * @param useAssetsLinks is the flag which says if Frontegg SDK uses assets links;
         * @param useChromeCustomTabs is the flag which says if Frontegg SDK uses chrome custom tabs;
         * @param mainActivityClass is the MainActivity.
         */
        fun init(
            fronteggDomain: String,
            clientId: String,
            context: Context,
            applicationId: String? = null,
            useAssetsLinks: Boolean = false,
            useChromeCustomTabs: Boolean = false,
            mainActivityClass: Class<*>? = null
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
         * @param regions is a list of [RegionConfig]. Could find at portal.frontegg.com;
         * @param context is the application context;
         * @param useAssetsLinks is the flag which says if Frontegg SDK uses assets links;
         * @param useChromeCustomTabs is the flag which says if Frontegg SDK uses chrome custom tabs;
         * @param mainActivityClass is the MainActivity.
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
