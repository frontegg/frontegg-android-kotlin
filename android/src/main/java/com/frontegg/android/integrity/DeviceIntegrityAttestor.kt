package com.frontegg.android.integrity

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Requests integrity tokens bound to a request hash, reusing one prepared provider.
 * Sign-in never waits for a provider to be prepared: until one is ready, or after Play reports it
 * invalid, the request is sent unattested while [launchInBackground] prepares the next one.
 * Every failure is reported as `null` so sign-in proceeds without attestation.
 */
internal class DeviceIntegrityAttestor(
    private val providerFactory: IntegrityProviderFactory,
    private val launchInBackground: (() -> Unit) -> Unit,
) {
    private val providerLock = Any()

    @Volatile
    private var cachedProvider: IntegrityTokenProvider? = null

    private val backgroundPrepareRunning = AtomicBoolean(false)

    fun warmUp() {
        try {
            preparedProvider()
        } catch (e: IntegrityAttestationException) {
            Log.w(TAG, "Play Integrity warm-up failed: ${describe(e)}")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Play Integrity warm-up failed: ${e.javaClass.simpleName}")
        }
    }

    fun attest(requestHash: String): String? {
        val provider = cachedProvider
        if (provider == null) {
            prepareInBackground()
            return null
        }
        return try {
            provider.requestToken(requestHash)
        } catch (e: IntegrityProviderInvalidException) {
            discardProvider(provider)
            prepareInBackground()
            null
        } catch (e: IntegrityAttestationException) {
            Log.w(TAG, "Play Integrity attestation skipped: ${describe(e)}")
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Play Integrity attestation skipped: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun prepareInBackground() {
        if (!backgroundPrepareRunning.compareAndSet(false, true)) return
        launchInBackground {
            try {
                warmUp()
            } finally {
                backgroundPrepareRunning.set(false)
            }
        }
    }

    @Throws(IntegrityAttestationException::class)
    private fun preparedProvider(): IntegrityTokenProvider {
        cachedProvider?.let { return it }
        synchronized(providerLock) {
            cachedProvider?.let { return it }
            return providerFactory.prepare().also { cachedProvider = it }
        }
    }

    private fun discardProvider(provider: IntegrityTokenProvider) {
        synchronized(providerLock) {
            if (cachedProvider === provider) cachedProvider = null
        }
    }

    private fun describe(e: IntegrityAttestationException): String =
        "${e.message} (${e.cause?.javaClass?.simpleName ?: "no cause"})"

    companion object {
        private const val TAG = "DeviceIntegrity"
    }
}
