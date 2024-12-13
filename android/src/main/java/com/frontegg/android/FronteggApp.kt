package com.frontegg.android

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.MATCH_ALL
import android.os.Handler
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.exceptions.FronteggException.Companion.FRONTEGG_APP_MUST_BE_INITIALIZED
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.*
import org.jetbrains.annotations.VisibleForTesting
import java.time.Instant

class FronteggApp private constructor(
    val context: Context,
    var baseUrl: String,
    var clientId: String,
    var applicationId: String?,
    val isEmbeddedMode: Boolean = true,
    val regions: List<RegionConfig> = listOf(),
    val selectedRegion: RegionConfig? = null,
    var handleLoginWithSocialLogin: Boolean = true,
    var customUserAgent: String? = null,
    var handleLoginWithSSO: Boolean = false,
    var shouldPromptSocialLoginConsent: Boolean = true,
    val useAssetsLinks: Boolean = false,
    var useChromeCustomTabs: Boolean = false,
    var mainActivityClass: Class<*>? = null
) {

    val credentialManager: CredentialManager = CredentialManager(context)
    val auth: FronteggAuth =
        FronteggAuth(baseUrl, clientId, applicationId, credentialManager, regions, selectedRegion)
    val packageName: String = context.packageName
    var appInForeground = true

    var lastJobStart: Long = Instant.now().toEpochMilli();

    companion object {

        @SuppressLint("StaticFieldLeak")
        @VisibleForTesting
        private var instance: FronteggApp? = null

        public val TAG: String = FronteggApp::class.java.simpleName

        public fun getInstance(): FronteggApp {
            if (instance == null) {
                throw FronteggException(FRONTEGG_APP_MUST_BE_INITIALIZED)
            }
            return instance!!
        }

        @JvmStatic
        @VisibleForTesting
        internal fun setTestInstance(value: FronteggApp) {
            instance = value
        }

        public fun init(
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

            val isEmbeddedMode = isActivityEnabled(context, EmbeddedAuthActivity::class.java.name)



            instance = FronteggApp(
                context = context,
                baseUrl = baseUrl,
                clientId = clientId,
                applicationId = applicationId,
                isEmbeddedMode = isEmbeddedMode,
                useAssetsLinks = useAssetsLinks,
                useChromeCustomTabs = useChromeCustomTabs,
                mainActivityClass = mainActivityClass
            )

            Handler(context.mainLooper).post {
                ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)
            }
        }

        public fun initWithRegions(
            regions: List<RegionConfig>,
            context: Context,
            useAssetsLinks: Boolean = false,
            useChromeCustomTabs: Boolean = false,
            mainActivityClass: Class<*>? = null
        ): FronteggApp {

            val isEmbeddedMode = isActivityEnabled(context, EmbeddedAuthActivity::class.java.name)
            val selectedRegion = CredentialManager(context).getSelectedRegion()
            if (selectedRegion != null) {
                val regionConfig = regions.find { it.key == selectedRegion }

                if (regionConfig != null) {
                    val newInstance = FronteggApp(
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
                    return newInstance
                }
            }
            val newInstance = FronteggApp(
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

        private var lifecycleEventObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    Log.d(TAG, "ON_STOP")
                    getInstance().appInForeground = false
                    getInstance().auth.refreshTokenWhenNeeded()
                }

                Lifecycle.Event.ON_START -> {
                    Log.d(TAG, "ON_START")
                    getInstance().appInForeground = true
                    getInstance().auth.refreshTokenWhenNeeded()
                }

                else -> {}
            }
        }

        private fun isActivityEnabled(context: Context, activityClassName: String): Boolean {
            return try {
                val componentName = ComponentName(context, activityClassName)
                val packageManager = context.packageManager
                packageManager.getActivityInfo(componentName, MATCH_ALL).isEnabled
            } catch (e: Exception) {
                false
            }
        }
    }

    fun initWithRegion(regionKey: String) {
        if (this.regions.isEmpty()) {
            throw RuntimeException("illegal state. Frontegg.plist does not contains regions array")
        }

        val keys = this.regions.joinToString(",") { it.key }

        val config = regions.find { it.key == regionKey }
            ?: throw RuntimeException("invalid region key ${regionKey}. available regions: $keys")


        credentialManager.saveSelectedRegion(regionKey)

        this.baseUrl = config.baseUrl
        this.clientId = config.clientId
        this.applicationId = config.applicationId
        this.auth.reinitWithRegion(config)


        Log.i(TAG, "Frontegg Initialized successfully (region: ${regionKey})")
    }


}