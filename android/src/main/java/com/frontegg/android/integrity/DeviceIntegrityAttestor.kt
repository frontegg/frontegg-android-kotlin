package com.frontegg.android.integrity

import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val IN_FLIGHT_PREPARE_WAIT_MS = 2_000L

/**
 * Requests integrity tokens bound to a request hash, reusing one prepared provider.
 * Providers are prepared one at a time by [launchInBackground]. Sign-in waits at most
 * [inFlightPrepareWaitMs] for a prepare already in flight; otherwise, until a provider is ready or
 * after Play reports it invalid, the request is sent unattested.
 * Every failure is reported as `null` so sign-in proceeds without attestation.
 */
internal class DeviceIntegrityAttestor @JvmOverloads constructor(
    private val providerFactory: IntegrityProviderFactory,
    private val inFlightPrepareWaitMs: Long = IN_FLIGHT_PREPARE_WAIT_MS,
    private val launchInBackground: (() -> Unit) -> Unit,
) {
    private val cachedProvider = AtomicReference<IntegrityTokenProvider?>(null)

    private val inFlightPrepare = AtomicReference<CountDownLatch?>(null)

    private val playIntegrityUnavailable = AtomicBoolean(false)

    fun warmUp() {
        if (playIntegrityUnavailable.get()) return
        val prepareDone = CountDownLatch(1)
        if (!inFlightPrepare.compareAndSet(null, prepareDone)) return
        launchInBackground {
            try {
                prepareProvider()
            } finally {
                inFlightPrepare.set(null)
                prepareDone.countDown()
            }
        }
    }

    fun attest(requestHash: String): String? {
        if (playIntegrityUnavailable.get()) return null
        val provider = cachedProvider.get() ?: awaitInFlightPrepare()
        if (provider == null) {
            warmUp()
            return null
        }
        return try {
            provider.requestToken(requestHash)
        } catch (e: IntegrityProviderInvalidException) {
            cachedProvider.compareAndSet(provider, null)
            warmUp()
            null
        } catch (e: PlayIntegrityUnavailableException) {
            disablePlayIntegrity(describe(e))
            null
        } catch (e: IntegrityAttestationException) {
            Log.w(TAG, "Play Integrity attestation skipped: ${describe(e)}")
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Play Integrity attestation skipped: ${e.javaClass.simpleName}")
            null
        } catch (e: LinkageError) {
            disablePlayIntegrity(e.javaClass.simpleName)
            null
        }
    }

    private fun awaitInFlightPrepare(): IntegrityTokenProvider? {
        // A prepare that finished after the caller read the cache has already stored its provider.
        val prepareDone = inFlightPrepare.get() ?: return cachedProvider.get()
        try {
            prepareDone.await(inFlightPrepareWaitMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return cachedProvider.get()
    }

    private fun prepareProvider() {
        try {
            if (cachedProvider.get() == null) cachedProvider.set(providerFactory.prepare())
        } catch (e: PlayIntegrityUnavailableException) {
            disablePlayIntegrity(describe(e))
        } catch (e: IntegrityAttestationException) {
            Log.w(TAG, "Play Integrity warm-up failed: ${describe(e)}")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Play Integrity warm-up failed: ${e.javaClass.simpleName}")
        } catch (e: LinkageError) {
            disablePlayIntegrity(e.javaClass.simpleName)
        }
    }

    // Play Integrity is missing, incompatible or permanently refused, so attestation stays off for this process.
    private fun disablePlayIntegrity(reason: String) {
        if (playIntegrityUnavailable.getAndSet(true)) return
        Log.w(TAG, "Play Integrity is unavailable, attestation is off: $reason")
    }

    private fun describe(e: IntegrityAttestationException): String =
        "${e.message} (${e.cause?.javaClass?.simpleName ?: "no cause"})"

    companion object {
        private const val TAG = "DeviceIntegrity"
    }
}
