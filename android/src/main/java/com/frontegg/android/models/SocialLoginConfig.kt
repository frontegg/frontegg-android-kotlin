package com.frontegg.android.models

import com.google.gson.annotations.SerializedName

/**
 * Social login configuration model
 * Matches iOS SocialLoginConfig implementation
 */
data class SocialLoginConfig(
    @SerializedName("facebook")
    val facebook: SocialLoginOption? = null,
    @SerializedName("google")
    val google: SocialLoginOption? = null,
    @SerializedName("microsoft")
    val microsoft: SocialLoginOption? = null,
    @SerializedName("github")
    val github: SocialLoginOption? = null,
    @SerializedName("slack")
    val slack: SocialLoginOption? = null,
    @SerializedName("apple")
    val apple: SocialLoginOption? = null,
    @SerializedName("linkedin")
    val linkedin: SocialLoginOption? = null
)

/**
 * Social login option for specific provider
 * Matches iOS SocialLoginOption implementation
 */
data class SocialLoginOption(
    @SerializedName("active")
    val active: Boolean = false,
    @SerializedName("clientId")
    val clientId: String? = null,
    @SerializedName("authorizationUrl")
    val authorizationUrl: String? = null,
    @SerializedName("additionalScopes")
    val additionalScopes: List<String> = emptyList()
)

/**
 * Provider details for OAuth configuration
 * Matches iOS ProviderDetails implementation
 */
data class ProviderDetails(
    val authorizeEndpoint: String,
    val defaultScopes: List<String>,
    val responseType: String,
    val responseMode: String?,
    val requiresPKCE: Boolean,
    val promptValueForConsent: String?
) {
    companion object {
        private val providerDetails = mapOf(
            SocialLoginProvider.FACEBOOK to ProviderDetails(
                authorizeEndpoint = "https://www.facebook.com/v10.0/dialog/oauth",
                defaultScopes = listOf("email"),
                responseType = "code",
                responseMode = null,
                requiresPKCE = false,
                promptValueForConsent = "reauthenticate"
            ),
            SocialLoginProvider.GOOGLE to ProviderDetails(
                authorizeEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
                defaultScopes = listOf(
                    "https://www.googleapis.com/auth/userinfo.profile",
                    "https://www.googleapis.com/auth/userinfo.email"
                ),
                responseType = "code",
                responseMode = null,
                requiresPKCE = true,
                promptValueForConsent = "select_account"
            ),
            SocialLoginProvider.GITHUB to ProviderDetails(
                authorizeEndpoint = "https://github.com/login/oauth/authorize",
                defaultScopes = listOf("read:user", "user:email"),
                responseType = "code",
                responseMode = null,
                requiresPKCE = false,
                promptValueForConsent = "consent"
            ),
            SocialLoginProvider.MICROSOFT to ProviderDetails(
                authorizeEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                defaultScopes = listOf("openid", "profile", "email"),
                responseType = "code",
                responseMode = "query",
                requiresPKCE = true,
                promptValueForConsent = "select_account"
            ),
            SocialLoginProvider.SLACK to ProviderDetails(
                authorizeEndpoint = "https://slack.com/openid/connect/authorize",
                defaultScopes = listOf("openid", "profile", "email"),
                responseType = "code",
                responseMode = null,
                requiresPKCE = false,
                promptValueForConsent = null
            ),
            SocialLoginProvider.APPLE to ProviderDetails(
                authorizeEndpoint = "https://appleid.apple.com/auth/authorize",
                defaultScopes = listOf("openid", "name", "email"),
                responseType = "code",
                responseMode = "form_post",
                requiresPKCE = false,
                promptValueForConsent = null
            ),
            SocialLoginProvider.LINKEDIN to ProviderDetails(
                authorizeEndpoint = "https://www.linkedin.com/oauth/v2/authorization",
                defaultScopes = listOf("r_liteprofile", "r_emailaddress"),
                responseType = "code",
                responseMode = null,
                requiresPKCE = false,
                promptValueForConsent = null
            )
        )

        fun forProvider(provider: SocialLoginProvider): ProviderDetails {
            return providerDetails[provider] ?: throw IllegalArgumentException("ProviderDetails not configured for ${provider.value}")
        }
    }
}
