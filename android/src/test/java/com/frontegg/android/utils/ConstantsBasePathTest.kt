package com.frontegg.android.utils

import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.StorageProvider
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage for OAuth callbacks on a base URL that carries a path (FR-26743).
 *
 * A vendor can expose Frontegg under a path prefix on a shared domain -- an edge
 * worker translating `api.example.com/fe-auth/oauth/...` onto the real Frontegg
 * host, rewriting the association files it serves so the published callback paths
 * carry the prefix.
 *
 * `oauthCallbackUrl` rebuilt the URI from host and port only, so the prefix was
 * dropped in both branches and the app sent a callback matching neither the
 * vendor's assetlinks binding nor the allow-list entry derived from it. The
 * redirect then dies in the browser on a path the shared domain does not route to
 * Frontegg, after the user has already authenticated.
 */
class ConstantsBasePathTest {
    private lateinit var mockStorage: FronteggInnerStorage

    private val host = "api.example.com"
    private val basePath = "/fe-auth"
    private val prefixedBaseUrl = "https://$host$basePath"
    private val packageName = "com.example.app"

    @Before
    fun setUp() {
        mockStorage = mockkClass(FronteggInnerStorage::class)
        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(mockStorage)
        every { mockStorage.packageName }.returns(packageName)
        every { mockStorage.baseUrl }.returns(prefixedBaseUrl)
    }

    @Test
    fun `app link callback carries the base path when the base url has one`() {
        every { mockStorage.useAssetsLinks }.returns(true)

        assertEquals(
            "https://$host$basePath/oauth/account/redirect/android/$packageName",
            Constants.oauthCallbackUrl(prefixedBaseUrl)
        )
    }

    @Test
    fun `custom scheme callback carries the base path when the base url has one`() {
        every { mockStorage.useAssetsLinks }.returns(false)

        assertEquals(
            "$packageName://$host$basePath/android/oauth/callback",
            Constants.oauthCallbackUrl(prefixedBaseUrl)
        )
    }

    @Test
    fun `a base url without a path is unaffected`() {
        every { mockStorage.useAssetsLinks }.returns(true)
        every { mockStorage.baseUrl }.returns("https://$host")

        assertEquals(
            "https://$host/oauth/account/redirect/android/$packageName",
            Constants.oauthCallbackUrl("https://$host")
        )
    }

    @Test
    fun `a trailing slash does not double up in the callback`() {
        every { mockStorage.useAssetsLinks }.returns(true)

        assertEquals(
            "https://$host$basePath/oauth/account/redirect/android/$packageName",
            Constants.oauthCallbackUrl("$prefixedBaseUrl/")
        )
    }

    @Test
    fun `a port is still preserved alongside the base path`() {
        every { mockStorage.useAssetsLinks }.returns(true)

        assertEquals(
            "https://$host:8443$basePath/oauth/account/redirect/android/$packageName",
            Constants.oauthCallbackUrl("https://$host:8443$basePath")
        )
    }

    /**
     * `defaultRedirectUri()` builds the social-login callback from the whole base
     * URL, so it already carried the prefix while `oauthCallbackUrl` dropped it --
     * the two disagreed for any vendor with a base path. They must line up now.
     *
     * They still differ deliberately when `useAssetsLinks` is off: social login
     * always needs the App-Link form because it appends /{provider} and the browser
     * has to follow it, so only the App-Link branch is comparable here.
     */
    @Test
    fun `the app link callback agrees with the social login redirect uri`() {
        every { mockStorage.useAssetsLinks }.returns(true)

        val fromConstants = Constants.oauthCallbackUrl(prefixedBaseUrl)
        val fromBaseUrl = "$prefixedBaseUrl/oauth/account/redirect/android/$packageName"

        assertEquals(fromBaseUrl, fromConstants)
    }

    @Test
    fun `the callback matcher accepts the prefixed app link path`() {
        assertTrue(
            "the SDK must recognise the callback it now generates",
            Constants.isGeneratedCallbackPath("$basePath/oauth/account/redirect/android/$packageName", basePath)
        )
    }

    @Test
    fun `the callback matcher still accepts the root form`() {
        assertTrue(
            "callbacks issued before this fix must keep matching",
            Constants.isGeneratedCallbackPath("/oauth/account/redirect/android/$packageName", basePath)
        )
    }
}
