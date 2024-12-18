package com.frontegg.android.utils

import com.frontegg.android.FronteggApp
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import org.junit.Before
import org.junit.Test

class ConstantsTest {
    private lateinit var fronteggApp: FronteggApp

    private val host = "base.url.com"
    private val baseUrl = "https://$host"
    private val packageName = "test.com"
    private val useAssetLinksOAuthCallbackUrl = "https://$host/oauth/account/redirect/android/${packageName}"
    private val oAuthCallbackUrl = "${packageName}://$host/android/oauth/callback"
    private val socialLoginRedirectUrl = "$baseUrl/oauth/account/social/success"

    @Before
    fun setUp() {
        mockkObject(Constants.Companion)
        fronteggApp = mockkClass(FronteggApp::class)
        mockkObject(FronteggApp.Companion)
        every { FronteggApp.getInstance() }.returns(fronteggApp)
        every { fronteggApp.packageName }.returns(packageName)
    }

    @Test
    fun `oauthCallbackUrl should return useAssetLinksOAuthCallbackUrl if useAssetsLinks is true`() {
        every { fronteggApp.useAssetsLinks }.returns(true)

        val oauthCallbackUrl = Constants.oauthCallbackUrl(baseUrl)

        assert(oauthCallbackUrl == useAssetLinksOAuthCallbackUrl)
    }
    @Test
    fun `oauthCallbackUrl should return oAuthCallbackUrl if useAssetsLinks is false`() {
        every { fronteggApp.useAssetsLinks }.returns(false)

        val oauthCallbackUrl = Constants.oauthCallbackUrl(baseUrl)

        assert(oauthCallbackUrl == oAuthCallbackUrl)
    }

    @Test
    fun `socialLoginRedirectUrl should return socialLoginRedirectUrl`() {
        val oauthCallbackUrl = Constants.socialLoginRedirectUrl(baseUrl)

        assert(oauthCallbackUrl == socialLoginRedirectUrl)
    }

}