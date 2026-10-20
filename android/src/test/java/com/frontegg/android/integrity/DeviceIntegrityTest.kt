package com.frontegg.android.integrity

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeviceIntegrityTest {

    @After
    fun tearDown() {
        DeviceIntegrity.configure(0L) { error("not used") }
    }

    @Test
    fun `a zero project number disables attestation`() {
        DeviceIntegrity.configure(0L) { error("not used") }

        assertNull(DeviceIntegrity.attestor)
    }

    @Test
    fun `configuring a project number warms the provider up in the background`() {
        val prepared = CountDownLatch(1)
        val factory = object : IntegrityProviderFactory {
            override fun prepare(): IntegrityTokenProvider {
                prepared.countDown()
                return FakeTokenProvider()
            }
        }

        DeviceIntegrity.configure(123L) { factory }

        assertNotNull(DeviceIntegrity.attestor)
        assertTrue(prepared.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `reconfiguring keeps the attestor for the same project number only`() {
        val factoryCalls = mutableListOf<Long>()
        DeviceIntegrity.configure(123L) { factoryCalls.add(123L); FakeProviderFactory(listOf(FakeTokenProvider())) }
        val first = DeviceIntegrity.attestor

        DeviceIntegrity.configure(123L) { factoryCalls.add(123L); FakeProviderFactory(listOf(FakeTokenProvider())) }
        assertSame(first, DeviceIntegrity.attestor)

        DeviceIntegrity.configure(456L) { factoryCalls.add(456L); FakeProviderFactory(listOf(FakeTokenProvider())) }
        assertNotSame(first, DeviceIntegrity.attestor)
        assertEquals(listOf(123L, 456L), factoryCalls)
    }
}
