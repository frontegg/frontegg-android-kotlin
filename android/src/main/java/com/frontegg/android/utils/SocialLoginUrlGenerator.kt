package com.frontegg.android.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.frontegg.android.fronteggAuth
import com.frontegg.android.models.ProviderDetails
import com.frontegg.android.models.SocialLoginAction
import com.frontegg.android.models.SocialLoginConfig
import com.frontegg.android.models.SocialLoginOption
import com.frontegg.android.models.SocialLoginProvider
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.StorageProvider
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Social login URL generator
 * Matches iOS SocialLoginUrlGenerator implementation
 */
class SocialLoginUrlGenerator private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: SocialLoginUrlGenerator? = null
        
        fun getInstance(): SocialLoginUrlGenerator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SocialLoginUrlGenerator().also { INSTANCE = it }
            }
        }
        
        private const val TAG = "SocialLoginUrlGenerator"
    }

    private var socialLoginConfig: SocialLoginConfig? = null
    private val gson = Gson()

    /**
     * Generate authorization URL for social login provider
     * Matches iOS authorizeURL method
     */
    suspend fun authorizeURL(
        context: Context,
        provider: SocialLoginProvider,
        action: SocialLoginAction = SocialLoginAction.LOGIN
    ): String? {
        val option = configuration(context, provider)
        
        // If no config available, create a default option for the provider
        val finalOption = option ?: SocialLoginOption(
            active = true,
            clientId = null,
            authorizationUrl = null,
            additionalScopes = emptyList()
        )
        
        if (!finalOption.active) {
            Log.d(TAG, "Provider inactive: ${provider.value}")
            return null
        }

        // Handle tenant-specific authorization URL if available
        if (!finalOption.authorizationUrl.isNullOrEmpty()) {
            val url = Uri.parse(finalOption.authorizationUrl)
            val builder = url.buildUpon()
            
            val details = ProviderDetails.forProvider(provider)
            if (details.promptValueForConsent != null) {
                builder.appendQueryParameter("prompt", details.promptValueForConsent)
            }
            
            return builder.build().toString()
        }

        return buildProviderURL(context, provider, finalOption, action)
    }

    /**
     * Reload social login configurations
     * Matches iOS reloadConfigs method
     */
    suspend fun reloadConfigs(context: Context): Boolean {
        return try {
            val authService = context.fronteggAuth as FronteggAuthService
            val config = try {
                authService.getSocialLoginConfig()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get social login config from API: ${e.message}", e)
                return false
            }
            this.socialLoginConfig = config
            Log.i(TAG, "Loaded social login configs: $config")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load social login configs: ${e.message}", e)
            false
        }
    }

    /**
     * Get configuration for specific provider
     * Matches iOS configuration method
     */
    suspend fun configuration(context: Context, provider: SocialLoginProvider): SocialLoginOption? {
        if (socialLoginConfig == null) {
            reloadConfigs(context)
        }
        
        return when (provider) {
            SocialLoginProvider.FACEBOOK -> socialLoginConfig?.facebook
            SocialLoginProvider.GOOGLE -> socialLoginConfig?.google
            SocialLoginProvider.MICROSOFT -> socialLoginConfig?.microsoft
            SocialLoginProvider.GITHUB -> socialLoginConfig?.github
            SocialLoginProvider.SLACK -> socialLoginConfig?.slack
            SocialLoginProvider.APPLE -> socialLoginConfig?.apple
            SocialLoginProvider.LINKEDIN -> socialLoginConfig?.linkedin
        }
    }

    /**
     * Get default redirect URI
     * Matches iOS defaultRedirectUri method
     */
    @Suppress("UNUSED_PARAMETER")
    fun defaultRedirectUri(context: Context): String {
        val storage = StorageProvider.getInnerStorage()
        val baseUrl = storage.baseUrl
        val baseRedirectUri = "$baseUrl/oauth/account/social/success"
        
        return try {
            Uri.parse(baseRedirectUri)
                .buildUpon()
                .build()
                .toString()
        } catch (e: Exception) {
            baseRedirectUri
        }
    }

    /**
     * Build provider URL with all required parameters
     * Matches iOS buildProviderURL method
     */
    private fun buildProviderURL(
        context: Context,
        provider: SocialLoginProvider,
        option: SocialLoginOption,
        action: SocialLoginAction
    ): String? {
        val details = ProviderDetails.forProvider(provider)
        val baseUri = Uri.parse(details.authorizeEndpoint) ?: return null
        
        val builder = baseUri.buildUpon()
        
        // 1. Client ID - use default if not provided
        val clientId = option.clientId ?: "default_client_id"
        builder.appendQueryParameter("client_id", clientId)
        
        // 2. Response Type & Mode
        builder.appendQueryParameter("response_type", details.responseType)
        details.responseMode?.let { responseMode ->
            builder.appendQueryParameter("response_mode", responseMode)
        }
        
        // 3. Redirect URI and State
        val redirectUri = defaultRedirectUri(context)
        val state = createState(provider, action, context)
        
        builder.appendQueryParameter("redirect_uri", redirectUri)
        builder.appendQueryParameter("state", state)
        
        // 4. Scopes
        val baseScopes = if (provider == SocialLoginProvider.LINKEDIN && option.additionalScopes.isNotEmpty()) {
            emptyList()
        } else {
            details.defaultScopes
        }
        val allScopes = (baseScopes + option.additionalScopes).joinToString(" ")
        
        if (allScopes.isNotEmpty()) {
            builder.appendQueryParameter("scope", allScopes)
        }
        
        // 5. PKCE Challenge (commented out as in iOS)
        // if (details.requiresPKCE) {
        //     // PKCE implementation would go here
        // }
        
        // 6. Prompt for consent
        details.promptValueForConsent?.let { prompt ->
            val promptKey = if (provider == SocialLoginProvider.FACEBOOK) "auth_type" else "prompt"
            builder.appendQueryParameter(promptKey, prompt)
        }
        
        // 7. Provider-specific extras
        if (provider == SocialLoginProvider.GOOGLE || provider == SocialLoginProvider.LINKEDIN) {
            builder.appendQueryParameter("include_granted_scopes", "true")
        }
        
        val url = builder.build().toString()
        Log.v(TAG, "Built URL for ${provider.value}: $url")
        return url
    }

    /**
     * Create OAuth state parameter
     * Matches iOS createState method
     */
    @Suppress("UNUSED_PARAMETER")
    private fun createState(
        provider: SocialLoginProvider,
        action: SocialLoginAction,
        context: Context
    ): String {
        val storage = StorageProvider.getInnerStorage()
        val applicationId = storage.applicationId ?: ""
        val packageName = storage.packageName
        
        val stateObject = JsonObject().apply {
            addProperty("provider", provider.value)
            addProperty("appId", applicationId)
            addProperty("action", action.value)
            addProperty("bundleId", packageName)
            addProperty("platform", "android")
        }
        
        return gson.toJson(stateObject)
    }
}
