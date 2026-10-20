package com.frontegg.android.integrity

import android.util.Log

/**
 * Requests integrity tokens bound to a request hash, reusing one prepared provider.
 * Every failure is reported as `null` so sign-in proceeds without attestation.
 */
internal class DeviceIntegrityAttestor(
    private val providerFactory: IntegrityProviderFactory,
) {
    private val providerLock = Any()

    @Volatile
    private var cachedProvider: IntegrityTokenProvider? = null

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
        return try {
            requestWithProviderRefresh(requestHash)
        } catch (e: IntegrityAttestationException) {
            Log.w(TAG, "Play Integrity attestation skipped: ${describe(e)}")
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Play Integrity attestation skipped: ${e.javaClass.simpleName}")
            null
        }
    }

    @Throws(IntegrityAttestationException::class)
    private fun requestWithProviderRefresh(requestHash: String): String {
        val provider = preparedProvider()
        return try {
            provider.requestToken(requestHash)
        } catch (e: IntegrityProviderInvalidException) {
            discardProvider(provider)
            preparedProvider().requestToken(requestHash)
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
