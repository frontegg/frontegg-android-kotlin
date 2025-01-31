package com.frontegg.android.utils

import com.frontegg.android.FronteggApp
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.StorageProvider
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import org.junit.Before
import org.junit.Test

class ConstantsTest {
    private lateinit var fronteggApp: FronteggApp
    private lateinit var mockStorage: FronteggInnerStorage

    private val host = "base.url.com"
    private val baseUrl = "https://$host"
    private val packageName = "test.com"
    private val useAssetLinksOAuthCallbackUrl =
        "https://$host/oauth/account/redirect/android/${packageName}"
    private val oAuthCallbackUrl = "${packageName}://$host/android/oauth/callback"
    private val socialLoginRedirectUrl = "$baseUrl/oauth/account/social/success"

    @Before
    fun setUp() {
        mockkObject(Constants.Companion)
        fronteggApp = mockkClass(FronteggApp::class)
        mockkObject(FronteggApp.Companion)
        every { FronteggApp.getInstance() }.returns(fronteggApp)
        mockStorage = mockkClass(FronteggInnerStorage::class)
        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(mockStorage)
        every { mockStorage.packageName }.returns(packageName)
    }

    @Test
    fun `oauthCallbackUrl should return useAssetLinksOAuthCallbackUrl if useAssetsLinks is true`() {
        every { mockStorage.useAssetsLinks }.returns(true)

        val oauthCallbackUrl = Constants.oauthCallbackUrl(baseUrl)

        assert(oauthCallbackUrl == useAssetLinksOAuthCallbackUrl)
    }

    @Test
    fun `oauthCallbackUrl should return oAuthCallbackUrl if useAssetsLinks is false`() {
        every { mockStorage.useAssetsLinks }.returns(false)

        val oauthCallbackUrl = Constants.oauthCallbackUrl(baseUrl)

        assert(oauthCallbackUrl == oAuthCallbackUrl)
    }

    @Test
    fun `socialLoginRedirectUrl should return socialLoginRedirectUrl`() {
        val oauthCallbackUrl = Constants.socialLoginRedirectUrl(baseUrl)

        assert(oauthCallbackUrl == socialLoginRedirectUrl)
    }

}