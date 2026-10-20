package com.frontegg.android.integrity

internal open class IntegrityAttestationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

internal class IntegrityProviderInvalidException(cause: Throwable? = null) :
    IntegrityAttestationException("Integrity token provider is no longer valid", cause)

internal interface IntegrityTokenProvider {
    @Throws(IntegrityAttestationException::class)
    fun requestToken(requestHash: String): String
}

internal interface IntegrityProviderFactory {
    @Throws(IntegrityAttestationException::class)
    fun prepare(): IntegrityTokenProvider
}
