package com.frontegg.android.integrity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

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
    fun `attest waits for a prepare already in flight`() {
        val factory = object : IntegrityProviderFactory {
            override fun prepare(): IntegrityTokenProvider {
                Thread.sleep(200)
                return FakeTokenProvider()
            }
        }
        val attestor = DeviceIntegrityAttestor(factory) { task -> thread { task() } }

        attestor.warmUp()

        assertEquals("token-hash", attestor.attest("hash"))
    }

    @Test
    fun `attest gives up on an in-flight prepare after the wait bound`() {
        val factory = FakeProviderFactory(listOf(FakeTokenProvider()))
        val launchedTasks = mutableListOf<() -> Unit>()
        val attestor = DeviceIntegrityAttestor(factory, inFlightPrepareWaitMs = 50) { task -> launchedTasks.add(task) }

        assertNull(attestor.attest("hash"))
        assertNull(attestor.attest("again"))
        assertEquals(0, factory.prepareCount.get())

        launchedTasks.single()()
        assertEquals("token-next", attestor.attest("next"))
    }

    @Test
    fun `warm-up and attest share one background prepare`() {
        val factory = FakeProviderFactory(listOf(FakeTokenProvider()))
        val launchedTasks = mutableListOf<() -> Unit>()
        val attestor = DeviceIntegrityAttestor(factory, inFlightPrepareWaitMs = 0) { task -> launchedTasks.add(task) }

        attestor.warmUp()
        attestor.warmUp()
        assertNull(attestor.attest("hash"))

        assertEquals(1, launchedTasks.size)
        assertEquals(0, factory.prepareCount.get())
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
    fun `discarding an invalid provider does not wait for the prepare replacing it`() {
        val bothRequestsStarted = CyclicBarrier(2)
        val replacementPrepareStarted = CountDownLatch(1)
        val releaseReplacement = CountDownLatch(1)
        val requestCount = AtomicInteger(0)
        val invalidProvider = object : IntegrityTokenProvider {
            override fun requestToken(requestHash: String): String {
                bothRequestsStarted.await(5, TimeUnit.SECONDS)
                if (requestCount.getAndIncrement() == 1) replacementPrepareStarted.await(5, TimeUnit.SECONDS)
                throw IntegrityProviderInvalidException()
            }
        }
        val prepareCount = AtomicInteger(0)
        val factory = object : IntegrityProviderFactory {
            override fun prepare(): IntegrityTokenProvider {
                if (prepareCount.getAndIncrement() == 0) return invalidProvider
                replacementPrepareStarted.countDown()
                releaseReplacement.await(5, TimeUnit.SECONDS)
                return FakeTokenProvider()
            }
        }
        val launchCount = AtomicInteger(0)
        val attestor = DeviceIntegrityAttestor(factory) { task ->
            if (launchCount.getAndIncrement() == 0) task() else thread { task() }
        }
        attestor.warmUp()

        val executor = Executors.newFixedThreadPool(2)
        try {
            val attestations = List(2) { executor.submit<String?> { attestor.attest("hash") } }
            attestations.forEach { assertNull(it.get(1, TimeUnit.SECONDS)) }
        } finally {
            releaseReplacement.countDown()
            executor.shutdownNow()
        }
        assertEquals(2, prepareCount.get())
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
    fun `a permanent Play Integrity error on prepare turns attestation off`() {
        val factory = FakeProviderFactory(
            providers = listOf(FakeTokenProvider()),
            prepareFailure = PlayIntegrityUnavailableException()
        )
        val attestor = attestor(factory)

        attestor.warmUp()
        assertNull(attestor.attest("hash"))
        attestor.warmUp()
        assertNull(attestor.attest("again"))

        assertEquals(1, factory.prepareCount.get())
    }

    @Test
    fun `a permanent Play Integrity error on a token request turns attestation off`() {
        val provider = FakeTokenProvider(failure = PlayIntegrityUnavailableException())
        val factory = FakeProviderFactory(listOf(provider))
        val attestor = attestor(factory)
        attestor.warmUp()

        assertNull(attestor.attest("hash"))
        assertNull(attestor.attest("again"))

        assertEquals(listOf("hash"), provider.requestedHashes)
        assertEquals(1, factory.prepareCount.get())
    }

    @Test
    fun `a transient token request error keeps the provider`() {
        val provider = FakeTokenProvider(failure = IntegrityAttestationException("Play Integrity request failed"))
        val factory = FakeProviderFactory(listOf(provider))
        val attestor = attestor(factory)
        attestor.warmUp()

        assertNull(attestor.attest("hash"))
        assertNull(attestor.attest("again"))

        assertEquals(listOf("hash", "again"), provider.requestedHashes)
        assertEquals(1, factory.prepareCount.get())
    }

    @Test
    fun `attest returns null on an unexpected runtime failure`() {
        val factory = object : IntegrityProviderFactory {
            override fun prepare(): IntegrityTokenProvider = throw IllegalStateException("main thread")
        }

        assertNull(attestor(factory).attest("hash"))
    }

    @Test
    fun `a missing Play Integrity library turns attestation off`() {
        val factory = FakeProviderFactory(
            providers = listOf(FakeTokenProvider()),
            prepareFailure = NoClassDefFoundError("com/google/android/play/core/integrity/IntegrityManagerFactory")
        )
        val attestor = attestor(factory)

        attestor.warmUp()

        assertNull(attestor.attest("hash"))
        assertEquals(1, factory.prepareCount.get())
    }

    @Test
    fun `an incompatible Play Integrity library fails the token request open`() {
        val provider = FakeTokenProvider(failure = NoSuchMethodError("request"))
        val attestor = attestor(FakeProviderFactory(listOf(provider)))
        attestor.warmUp()

        assertNull(attestor.attest("hash"))
        assertNull(attestor.attest("again"))

        assertEquals(listOf("hash"), provider.requestedHashes)
    }

    @Test
    fun `warmUp swallows an unexpected runtime failure`() {
        val factory = object : IntegrityProviderFactory {
            override fun prepare(): IntegrityTokenProvider = throw IllegalStateException("no Play services")
        }

        attestor(factory).warmUp()
    }
}
