package com.frontegg.android.services

import android.content.Context
import android.util.Log
import com.frontegg.android.FronteggApp
import com.frontegg.android.FronteggAuth
import com.frontegg.android.regions.RegionConfig

class FronteggAppService(
    private val context: Context,
    private var baseUrl: String,
    private var clientId: String,
    private var applicationId: String?,
    private val isEmbeddedMode: Boolean = true,
    private val regions: List<RegionConfig> = listOf(),
    private var selectedRegion: RegionConfig? = null,
    private val handleLoginWithSocialLogin: Boolean = true,
    private val handleLoginWithSocialLoginProvider: Boolean = true,
    private val handleLoginWithCustomSocialLoginProvider: Boolean = true,
    private val customUserAgent: String? = null,
    private val handleLoginWithSSO: Boolean = false,
    private val shouldPromptSocialLoginConsent: Boolean = true,
    private val useAssetsLinks: Boolean = false,
    private var useChromeCustomTabs: Boolean = false,
    private var mainActivityClass: Class<*>? = null
) : FronteggApp {

    private val storage = StorageProvider.getInnerStorage()

    private val credentialManager = CredentialManager(context)
    private val appLifecycle = FronteggAppLifecycle(context)
    private val refreshTokenManager = FronteggRefreshTokenTimer(context, appLifecycle)

    override val auth: FronteggAuth

    init {
        fillStorage()
        auth =
            FronteggAuthService(
                credentialManager,
                appLifecycle,
                refreshTokenManager
            )
    }

    private fun fillStorage() {
        storage.fill(
            baseUrl,
            clientId,
            applicationId,
            isEmbeddedMode,
            regions,
            selectedRegion,
            handleLoginWithSocialLogin,
            handleLoginWithSocialLoginProvider,
            handleLoginWithCustomSocialLoginProvider,
            customUserAgent,
            handleLoginWithSSO,
            shouldPromptSocialLoginConsent,
            useAssetsLinks,
            useChromeCustomTabs,
            mainActivityClass,
            context.packageName,
        )
    }

    override fun initWithRegion(regionKey: String) {
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
        this.selectedRegion = config
        fillStorage()

        FronteggAuthService.instance.reinitWithRegion()

        Log.i(TAG, "Frontegg Initialized successfully (region: ${regionKey})")
    }

    companion object {
        val TAG: String = FronteggAppService::class.java.simpleName
    }
}