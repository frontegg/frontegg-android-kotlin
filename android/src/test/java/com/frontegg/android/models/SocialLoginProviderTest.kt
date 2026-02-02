package com.frontegg.android.models

import org.junit.Test

class SocialLoginProviderTest {

    @Test
    fun `FACEBOOK has correct value`() {
        assert(SocialLoginProvider.FACEBOOK.value == "facebook")
    }

    @Test
    fun `GOOGLE has correct value`() {
        assert(SocialLoginProvider.GOOGLE.value == "google")
    }

    @Test
    fun `GITHUB has correct value`() {
        assert(SocialLoginProvider.GITHUB.value == "github")
    }

    @Test
    fun `MICROSOFT has correct value`() {
        assert(SocialLoginProvider.MICROSOFT.value == "microsoft")
    }

    @Test
    fun `SLACK has correct value`() {
        assert(SocialLoginProvider.SLACK.value == "slack")
    }

    @Test
    fun `APPLE has correct value`() {
        assert(SocialLoginProvider.APPLE.value == "apple")
    }

    @Test
    fun `LINKEDIN has correct value`() {
        assert(SocialLoginProvider.LINKEDIN.value == "linkedin")
    }

    @Test
    fun `all providers have unique values`() {
        val values = SocialLoginProvider.values().map { it.value }
        val uniqueValues = values.toSet()
        
        assert(values.size == uniqueValues.size) { "Some providers have duplicate values" }
    }

    @Test
    fun `all providers have non-empty values`() {
        SocialLoginProvider.values().forEach { provider ->
            assert(provider.value.isNotEmpty()) { "${provider.name} has empty value" }
        }
    }

    @Test
    fun `SocialLoginAction LOGIN has correct value`() {
        assert(SocialLoginAction.LOGIN.value == "login")
    }

    @Test
    fun `SocialLoginAction SIGNUP has correct value`() {
        assert(SocialLoginAction.SIGNUP.value == "signUp")
    }

    @Test
    fun `all actions have unique values`() {
        val values = SocialLoginAction.values().map { it.value }
        val uniqueValues = values.toSet()
        
        assert(values.size == uniqueValues.size) { "Some actions have duplicate values" }
    }

    @Test
    fun `providers can be used in when expression`() {
        fun getProviderName(provider: SocialLoginProvider): String {
            return when (provider) {
                SocialLoginProvider.FACEBOOK -> "Facebook"
                SocialLoginProvider.GOOGLE -> "Google"
                SocialLoginProvider.GITHUB -> "GitHub"
                SocialLoginProvider.MICROSOFT -> "Microsoft"
                SocialLoginProvider.SLACK -> "Slack"
                SocialLoginProvider.APPLE -> "Apple"
                SocialLoginProvider.LINKEDIN -> "LinkedIn"
            }
        }
        
        assert(getProviderName(SocialLoginProvider.GOOGLE) == "Google")
        assert(getProviderName(SocialLoginProvider.FACEBOOK) == "Facebook")
    }

    @Test
    fun `provider enum has expected count`() {
        // Currently 7 providers
        assert(SocialLoginProvider.values().size == 7)
    }

    @Test
    fun `action enum has expected count`() {
        // Currently 2 actions: LOGIN and REGISTER
        assert(SocialLoginAction.values().size == 2)
    }
}
