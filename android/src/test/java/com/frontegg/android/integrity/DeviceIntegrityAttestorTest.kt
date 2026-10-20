package com.frontegg.android.integrity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeoutException

class DeviceIntegrityAttestorTest {

    @Test
    fun `integrityRequestHash is unpadded base64url SHA-256 of the body`() {
        assertEquals("47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU", integrityRequestHash(ByteArray(0)))
        assertEquals(
            "ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0",
            integrityRequestHash("abc".toByteArray(Charsets.UTF_8))
        )
    }

    @Test
    fun `attest prepares the provider once and reuses it`() {
        val provider = FakeTokenProvider()
        val factory = FakeProviderFactory(listOf(provider))
        val attestor = DeviceIntegrityAttestor(factory)

        assertEquals("token-first", attestor.attest("first"))
        assertEquals("token-second", attestor.attest("second"))

        assertEquals(1, factory.prepareCount.get())
        assertEquals(listOf("first", "second"), provider.requestedHashes)
    }

    @Test
    fun `warmUp prepares the provider ahead of the first request`() {
        val factory = FakeProviderFactory(listOf(FakeTokenProvider()))
        val attestor = DeviceIntegrityAttestor(factory)

        attestor.warmUp()
        attestor.attest("hash")

        assertEquals(1, factory.prepareCount.get())
    }

    @Test
    fun `attest prepares a new provider once when the provider is invalid`() {
        val invalidProvider = FakeTokenProvider(failure = IntegrityProviderInvalidException())
        val freshProvider = FakeTokenProvider(tokenPrefix = "fresh")
        val factory = FakeProviderFactory(listOf(invalidProvider, freshProvider))
        val attestor = DeviceIntegrityAttestor(factory)

        assertEquals("fresh-hash", attestor.attest("hash"))
        assertEquals("fresh-next", attestor.attest("next"))

        assertEquals(2, factory.prepareCount.get())
    }

    @Test
    fun `attest gives up when the refreshed provider is invalid too`() {
        val invalidProvider = FakeTokenProvider(failure = IntegrityProviderInvalidException())
        val factory = FakeProviderFactory(listOf(invalidProvider))
        val attestor = DeviceIntegrityAttestor(factory)

        assertNull(attestor.attest("hash"))
        assertEquals(2, factory.prepareCount.get())
    }

    @Test
    fun `attest returns null when the token request times out`() {
        val timeout = IntegrityAttestationException("Play Integrity request timed out", TimeoutException())
        val attestor = DeviceIntegrityAttestor(FakeProviderFactory(listOf(FakeTokenProvider(failure = timeout))))

        assertNull(attestor.attest("hash"))
    }

    @Test
    fun `attest returns null and retries preparing when prepare fails`() {
        val factory = FakeProviderFactory(
            providers = listOf(FakeTokenProvider()),
            prepareFailure = IntegrityAttestationException("Play Integrity request failed")
        )
        val attestor = DeviceIntegrityAttestor(factory)

        attestor.warmUp()

        assertNull(attestor.attest("hash"))
        assertEquals(2, factory.prepareCount.get())
    }

    @Test
    fun `attest returns null on an unexpected runtime failure`() {
        val factory = object : IntegrityProviderFactory {
            override fun prepare(): IntegrityTokenProvider = throw IllegalStateException("main thread")
        }

        assertNull(DeviceIntegrityAttestor(factory).attest("hash"))
    }

    @Test
    fun `warmUp swallows an unexpected runtime failure`() {
        val factory = object : IntegrityProviderFactory {
            override fun prepare(): IntegrityTokenProvider = throw IllegalStateException("no Play services")
        }

        DeviceIntegrityAttestor(factory).warmUp()
    }
}
