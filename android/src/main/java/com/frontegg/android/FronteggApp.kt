package com.frontegg.android

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.MATCH_ALL
import android.util.Log
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.exceptions.FronteggException.Companion.FRONTEGG_APP_MUST_BE_INITIALIZED
import com.frontegg.android.exceptions.FronteggException.Companion.FRONTEGG_DOMAIN_MUST_NOT_START_WITH_HTTPS
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.*
import java.lang.RuntimeException

class FronteggApp private constructor(
    val context: Context,
    var baseUrl: String,
    var clientId: String,
    val isEmbeddedMode: Boolean = true,
    val regions: List<RegionConfig> = listOf(),
    val selectedRegion: RegionConfig? = null,
    val handleLoginWithSocialLogin: Boolean = true,
    val handleLoginWithSSO: Boolean = false
) {

    val credentialManager: CredentialManager = CredentialManager(context)
    val auth: FronteggAuth =
        FronteggAuth(baseUrl, clientId, credentialManager, regions, selectedRegion)
    val packageName: String = context.packageName

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var instance: FronteggApp? = null

        public val TAG: String = FronteggApp::class.java.simpleName

        public fun getInstance(): FronteggApp {
            if (instance == null) {
                throw FronteggException(FRONTEGG_APP_MUST_BE_INITIALIZED)
            }
            return instance!!
        }

        public fun init(
            fronteggDomain: String,
            clientId: String,
            context: Context
        ) {
            val baseUrl: String = if (fronteggDomain.startsWith("https")) {
                throw FronteggException(FRONTEGG_DOMAIN_MUST_NOT_START_WITH_HTTPS)
            } else {
                "https://$fronteggDomain"
            }

            val isEmbeddedMode = isActivityEnabled(context, EmbeddedAuthActivity::class.java.name)
            instance = FronteggApp(context, baseUrl, clientId, isEmbeddedMode)
        }

        public fun initWithRegions(regions: List<RegionConfig>, context: Context): FronteggApp {

            val isEmbeddedMode = isActivityEnabled(context, EmbeddedAuthActivity::class.java.name)
            val selectedRegion = CredentialManager(context).getSelectedRegion()
            if (selectedRegion != null) {
                val regionConfig = regions.find { it.key == selectedRegion }

                if (regionConfig != null) {
                    val newInstance = FronteggApp(
                        context,
                        regionConfig.baseUrl,
                        regionConfig.clientId,
                        isEmbeddedMode,
                        regions,
                        regionConfig
                    )
                    instance = newInstance
                    return newInstance
                }
            }
            val newInstance = FronteggApp(context, "", "", isEmbeddedMode, regions)
            instance = newInstance
            return newInstance
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
        this.auth.reinitWithRegion(config)


        Log.i(TAG, "Frontegg Initialized successfully (region: ${regionKey})")
    }


}