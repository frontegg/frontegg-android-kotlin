package com.frontegg.android.services

import android.util.Log
import com.frontegg.android.utils.CredentialKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * A DNS failure while loading entitlements must not escape as an exception.
 * `.invalid` is reserved by RFC 2606 and never resolves, so `call.execute()`
 * throws `UnknownHostException` without needing a network.
 */
class EntitlementsNetworkFailureTest {

    private lateinit var api: Api
    private lateinit var credentialManager: CredentialManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        credentialManager = mockk(relaxed = true)
        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) } returns "access-token"

        val storage = mockk<FronteggInnerStorage>(relaxed = true)
        every { storage.baseUrl } returns "https://frontegg-dns-failure.invalid"
        every { storage.clientId } returns "client-id"
        every { storage.applicationId } returns null
        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() } returns storage

        api = Api(credentialManager)
    }

    @Test
    fun `getUserEntitlements returns null when the host cannot be resolved`() {
        assertNull(api.getUserEntitlements(accessTokenOverride = "access-token"))
    }

    @Test
    fun `EntitlementsService load returns false when the host cannot be resolved`() {
        val service = EntitlementsService(api = api, enabled = true)

        assertFalse(service.load("access-token"))
    }
}
