package com.frontegg.android.models

import com.google.gson.Gson
import org.junit.Test

class SocialLoginConfigTest {

    private val gson = Gson()

    @Test
    fun `SocialLoginConfig deserializes correctly from JSON`() {
        val json = """
            {
                "facebook": {"active": true, "clientId": "fb-123"},
                "google": {"active": true, "clientId": "google-456"},
                "microsoft": {"active": false},
                "github": {"active": true, "clientId": "gh-789"},
                "slack": null,
                "apple": {"active": true},
                "linkedin": {"active": false, "additionalScopes": ["r_basicprofile"]}
            }
        """.trimIndent()
        
        val config = gson.fromJson(json, SocialLoginConfig::class.java)
        
        assert(config.facebook?.active == true)
        assert(config.facebook?.clientId == "fb-123")
        assert(config.google?.active == true)
        assert(config.microsoft?.active == false)
        assert(config.github?.clientId == "gh-789")
        assert(config.slack == null)
        assert(config.apple?.active == true)
        assert(config.linkedin?.active == false)
    }

    @Test
    fun `SocialLoginConfig handles empty JSON`() {
        val json = "{}"
        
        val config = gson.fromJson(json, SocialLoginConfig::class.java)
        
        assert(config.facebook == null)
        assert(config.google == null)
        assert(config.microsoft == null)
        assert(config.github == null)
        assert(config.slack == null)
        assert(config.apple == null)
        assert(config.linkedin == null)
    }

    @Test
    fun `SocialLoginOption default values`() {
        val option = SocialLoginOption()
        
        assert(!option.active)
        assert(option.clientId == null)
        assert(option.authorizationUrl == null)
        assert(option.additionalScopes.isEmpty())
    }

    @Test
    fun `SocialLoginOption deserializes with all fields`() {
        val json = """
            {
                "active": true,
                "clientId": "test-client-id",
                "authorizationUrl": "https://auth.example.com/authorize",
                "additionalScopes": ["scope1", "scope2", "scope3"]
            }
        """.trimIndent()
        
        val option = gson.fromJson(json, SocialLoginOption::class.java)
        
        assert(option.active)
        assert(option.clientId == "test-client-id")
        assert(option.authorizationUrl == "https://auth.example.com/authorize")
        assert(option.additionalScopes.size == 3)
        assert(option.additionalScopes.contains("scope1"))
        assert(option.additionalScopes.contains("scope2"))
        assert(option.additionalScopes.contains("scope3"))
    }

    @Test
    fun `SocialLoginOption handles missing optional fields`() {
        val json = """{"active": true}"""
        
        val option = gson.fromJson(json, SocialLoginOption::class.java)
        
        assert(option.active)
        assert(option.clientId == null)
        assert(option.authorizationUrl == null)
    }

    @Test
    fun `ProviderDetails forProvider returns correct details for Facebook`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.FACEBOOK)
        
        assert(details.authorizeEndpoint == "https://www.facebook.com/v10.0/dialog/oauth")
        assert(details.defaultScopes.contains("email"))
        assert(details.responseType == "code")
        assert(details.responseMode == null)
        assert(!details.requiresPKCE)
        assert(details.promptValueForConsent == "reauthenticate")
    }

    @Test
    fun `ProviderDetails forProvider returns correct details for Google`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.GOOGLE)
        
        assert(details.authorizeEndpoint == "https://accounts.google.com/o/oauth2/v2/auth")
        assert(details.defaultScopes.contains("https://www.googleapis.com/auth/userinfo.profile"))
        assert(details.defaultScopes.contains("https://www.googleapis.com/auth/userinfo.email"))
        assert(details.responseType == "code")
        assert(details.responseMode == null)
        assert(details.requiresPKCE)
        assert(details.promptValueForConsent == "select_account")
    }

    @Test
    fun `ProviderDetails forProvider returns correct details for Microsoft`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.MICROSOFT)
        
        assert(details.authorizeEndpoint == "https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
        assert(details.defaultScopes.containsAll(listOf("openid", "profile", "email")))
        assert(details.responseType == "code")
        assert(details.responseMode == "query")
        assert(details.requiresPKCE)
        assert(details.promptValueForConsent == "select_account")
    }

    @Test
    fun `ProviderDetails forProvider returns correct details for GitHub`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.GITHUB)
        
        assert(details.authorizeEndpoint == "https://github.com/login/oauth/authorize")
        assert(details.defaultScopes.contains("read:user"))
        assert(details.defaultScopes.contains("user:email"))
        assert(details.responseType == "code")
        assert(details.responseMode == null)
        assert(!details.requiresPKCE)
        assert(details.promptValueForConsent == "consent")
    }

    @Test
    fun `ProviderDetails forProvider returns correct details for Slack`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.SLACK)
        
        assert(details.authorizeEndpoint == "https://slack.com/openid/connect/authorize")
        assert(details.defaultScopes.containsAll(listOf("openid", "profile", "email")))
        assert(details.responseType == "code")
        assert(details.responseMode == null)
        assert(!details.requiresPKCE)
        assert(details.promptValueForConsent == null)
    }

    @Test
    fun `ProviderDetails forProvider returns correct details for Apple`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.APPLE)
        
        assert(details.authorizeEndpoint == "https://appleid.apple.com/auth/authorize")
        assert(details.defaultScopes.containsAll(listOf("openid", "name", "email")))
        assert(details.responseType == "code")
        assert(details.responseMode == "form_post")
        assert(!details.requiresPKCE)
        assert(details.promptValueForConsent == null)
    }

    @Test
    fun `ProviderDetails forProvider returns correct details for LinkedIn`() {
        val details = ProviderDetails.forProvider(SocialLoginProvider.LINKEDIN)
        
        assert(details.authorizeEndpoint == "https://www.linkedin.com/oauth/v2/authorization")
        assert(details.defaultScopes.contains("r_liteprofile"))
        assert(details.defaultScopes.contains("r_emailaddress"))
        assert(details.responseType == "code")
        assert(details.responseMode == null)
        assert(!details.requiresPKCE)
        assert(details.promptValueForConsent == null)
    }

    @Test
    fun `all SocialLoginProvider values have ProviderDetails`() {
        SocialLoginProvider.values().forEach { provider ->
            val details = ProviderDetails.forProvider(provider)
            assert(details.authorizeEndpoint.isNotEmpty())
            assert(details.responseType.isNotEmpty())
        }
    }
}
