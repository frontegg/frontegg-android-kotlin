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
     */
    suspend fun authorizeURL(
        context: Context,
        provider: SocialLoginProvider,
        @Suppress("UNUSED_PARAMETER") action: SocialLoginAction = SocialLoginAction.LOGIN
    ): String? {
        return try {
            // Generate Frontegg OAuth URL with login_direct_action parameter (like iOS)
            val storage = StorageProvider.getInnerStorage()
            val baseUrl = storage.baseUrl
            
            // Create login_direct_action JSON
            val loginDirectAction = mapOf(
                "type" to "social-login",
                "data" to provider.value,
                "additionalQueryParams" to mapOf(
                    "prompt" to "consent"
                )
            )
            
            val jsonString = com.google.gson.Gson().toJson(loginDirectAction)
            val encodedAction = android.util.Base64.encodeToString(
                jsonString.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            
            // Build Frontegg OAuth URL
            val authService = context.fronteggAuth as FronteggAuthService
            val redirectUri = authService.defaultRedirectUri()
            
            // Get Frontegg client_id from storage
            val clientId = storage.clientId.ifBlank { 
                storage.selectedRegion?.clientId ?: storage.regions.firstOrNull()?.clientId ?: ""
            }
            Log.d(TAG, "Using Frontegg client_id: $clientId")
            Log.d(TAG, "Using OAuth redirect_uri: $redirectUri")
            
            val builder = Uri.parse("$baseUrl/oauth/authorize").buildUpon()
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("scope", "openid email profile")
                .appendQueryParameter("prompt", "login")
                .appendQueryParameter("login_direct_action", encodedAction)
            
            // Add PKCE if enabled
            try {
                if (authService.featureFlags.isOnSync("identity-sso-force-pkce")) {
                    val codeVerifier = PKCEUtils.getCodeVerifierFromWebview(context)
                    val codeChallenge = PKCEUtils.generateCodeChallenge(codeVerifier)
                    
                    builder.appendQueryParameter("code_challenge", codeChallenge)
                    builder.appendQueryParameter("code_challenge_method", "S256")
                    
                    Log.d(TAG, "Added PKCE parameters for ${provider.value}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add PKCE parameters for ${provider.value}", e)
            }
            
            val url = builder.build().toString()
            Log.v(TAG, "Generated Frontegg OAuth URL for ${provider.value}: $url")
            url
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate authorization URL for ${provider.value}", e)
            null
        }
    }

    /**
     * Reload social login configurations
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
     */
    suspend fun configuration(context: Context, provider: SocialLoginProvider): SocialLoginOption? {
        if (socialLoginConfig == null) {
            try {
                reloadConfigs(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load social login config, using defaults: ${e.message}")
                // Continue with null config - will use defaults
            }
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
     * Build provider URL with all required parameters
     */
    private fun buildProviderURL(
        context: Context,
        provider: SocialLoginProvider,
        option: SocialLoginOption,
        action: SocialLoginAction
    ): String? {
        return try {
            val details = ProviderDetails.forProvider(provider)
            val baseUri = Uri.parse(details.authorizeEndpoint) ?: return null
            
            val builder = baseUri.buildUpon()
            
            // 1. Client ID - use from option or generate fallback
            val clientId = option.clientId ?: run {
                val storage = StorageProvider.getInnerStorage()
                "${storage.packageName}.client"
            }
            builder.appendQueryParameter("client_id", clientId)
            
            // 2. Response Type & Mode
            builder.appendQueryParameter("response_type", details.responseType)
            details.responseMode?.let { responseMode ->
                builder.appendQueryParameter("response_mode", responseMode)
            }
            
            // 3. Redirect URI and State
            val authService = context.fronteggAuth as com.frontegg.android.services.FronteggAuthService
            val redirectUri = authService.defaultSocialLoginRedirectUri()
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
            
            // 5. PKCE Challenge
            if (details.requiresPKCE) {
                try {
                    val authService = context.fronteggAuth as com.frontegg.android.services.FronteggAuthService
                    if (authService.featureFlags.isOnSync("identity-sso-force-pkce")) {
                        val codeVerifier = com.frontegg.android.utils.PKCEUtils.getCodeVerifierFromWebview(context)
                        val codeChallenge = com.frontegg.android.utils.PKCEUtils.generateCodeChallenge(codeVerifier)
                        
                        builder.appendQueryParameter("code_challenge", codeChallenge)
                        builder.appendQueryParameter("code_challenge_method", "S256")
                        
                        Log.d(TAG, "Added PKCE parameters for ${provider.value}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add PKCE parameters for ${provider.value}", e)
                }
            }
            
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
            url
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build provider URL for ${provider.value}", e)
            null
        }
    }

    /**
     * Create OAuth state parameter
     */
    @Suppress("UNUSED_PARAMETER")
    private fun createState(
        provider: SocialLoginProvider,
        action: SocialLoginAction,
        context: Context
    ): String {
        return try {
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
            
            gson.toJson(stateObject)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create state for ${provider.value}", e)
            // Return minimal state as fallback
            JsonObject().apply {
                addProperty("provider", provider.value)
                addProperty("action", action.value)
                addProperty("platform", "android")
            }.toString()
        }
    }
}
