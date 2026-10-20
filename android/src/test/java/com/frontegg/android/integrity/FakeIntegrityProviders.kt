package com.frontegg.android.integrity

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal class FakeTokenProvider(
    private val tokenPrefix: String = "token",
    private val failure: IntegrityAttestationException? = null,
) : IntegrityTokenProvider {
    val requestedHashes = CopyOnWriteArrayList<String>()

    override fun requestToken(requestHash: String): String {
        requestedHashes.add(requestHash)
        failure?.let { throw it }
        return "$tokenPrefix-$requestHash"
    }
}

internal class FakeProviderFactory(
    private val providers: List<IntegrityTokenProvider>,
    private val prepareFailure: IntegrityAttestationException? = null,
) : IntegrityProviderFactory {
    val prepareCount = AtomicInteger(0)

    override fun prepare(): IntegrityTokenProvider {
        val index = prepareCount.getAndIncrement()
        prepareFailure?.let { throw it }
        return providers[index.coerceAtMost(providers.lastIndex)]
    }
}
