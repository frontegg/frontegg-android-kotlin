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

    private fun attestor(factory: IntegrityProviderFactory) = DeviceIntegrityAttestor(factory) { task -> task() }

    @Test
    fun `attest reuses the provider prepared by warm-up`() {
        val provider = FakeTokenProvider()
        val factory = FakeProviderFactory(listOf(provider))
        val attestor = attestor(factory)

        attestor.warmUp()
        assertEquals("token-first", attestor.attest("first"))
        assertEquals("token-second", attestor.attest("second"))

        assertEquals(1, factory.prepareCount.get())
        assertEquals(listOf("first", "second"), provider.requestedHashes)
    }

    @Test
    fun `attest does not wait for a provider that is not prepared yet`() {
        val factory = FakeProviderFactory(listOf(FakeTokenProvider()))
        val launchedTasks = mutableListOf<() -> Unit>()
        val attestor = DeviceIntegrityAttestor(factory) { task -> launchedTasks.add(task) }

        assertNull(attestor.attest("hash"))
        assertNull(attestor.attest("again"))
        assertEquals(0, factory.prepareCount.get())

        launchedTasks.single()()
        assertEquals("token-next", attestor.attest("next"))
    }

    @Test
    fun `an invalid provider is replaced in the background`() {
        val invalidProvider = FakeTokenProvider(failure = IntegrityProviderInvalidException())
        val freshProvider = FakeTokenProvider(tokenPrefix = "fresh")
        val factory = FakeProviderFactory(listOf(invalidProvider, freshProvider))
        val attestor = attestor(factory)

        attestor.warmUp()
        assertNull(attestor.attest("hash"))
        assertEquals("fresh-next", attestor.attest("next"))

        assertEquals(2, factory.prepareCount.get())
    }

    @Test
    fun `attest returns null when the token request times out`() {
        val timeout = IntegrityAttestationException("Play Integrity request timed out", TimeoutException())
        val attestor = attestor(FakeProviderFactory(listOf(FakeTokenProvider(failure = timeout))))

        attestor.warmUp()

        assertNull(attestor.attest("hash"))
    }

    @Test
    fun `attest returns null and retries preparing when prepare fails`() {
        val factory = FakeProviderFactory(
            providers = listOf(FakeTokenProvider()),
            prepareFailure = IntegrityAttestationException("Play Integrity request failed")
        )
        val attestor = attestor(factory)

        attestor.warmUp()

        assertNull(attestor.attest("hash"))
        assertEquals(2, factory.prepareCount.get())
    }

    @Test
    fun `attest returns null on an unexpected runtime failure`() {
        val factory = object : IntegrityProviderFactory {
            override fun prepare(): IntegrityTokenProvider = throw IllegalStateException("main thread")
        }

        assertNull(attestor(factory).attest("hash"))
    }

    @Test
    fun `warmUp swallows an unexpected runtime failure`() {
        val factory = object : IntegrityProviderFactory {
            override fun prepare(): IntegrityTokenProvider = throw IllegalStateException("no Play services")
        }

        attestor(factory).warmUp()
    }
}
