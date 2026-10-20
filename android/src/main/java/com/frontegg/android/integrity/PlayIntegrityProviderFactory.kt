package com.frontegg.android.integrity

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val PREPARE_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 3_000L

/**
 * Prepares Play Integrity Standard API token providers for [cloudProjectNumber].
 */
internal class PlayIntegrityProviderFactory(
    private val context: Context,
    private val cloudProjectNumber: Long,
) : IntegrityProviderFactory {

    @Throws(IntegrityAttestationException::class)
    override fun prepare(): IntegrityTokenProvider {
        val integrityManager = IntegrityManagerFactory.createStandard(context.applicationContext)
        val prepareRequest = PrepareIntegrityTokenRequest.builder()
            .setCloudProjectNumber(cloudProjectNumber)
            .build()
        val tokenProvider = awaitPlayTask(integrityManager.prepareIntegrityToken(prepareRequest), PREPARE_TIMEOUT_MS)
        return PlayIntegrityTokenProvider(tokenProvider)
    }
}

private class PlayIntegrityTokenProvider(
    private val tokenProvider: StandardIntegrityTokenProvider,
) : IntegrityTokenProvider {

    @Throws(IntegrityAttestationException::class)
    override fun requestToken(requestHash: String): String {
        val tokenRequest = StandardIntegrityTokenRequest.builder()
            .setRequestHash(requestHash)
            .build()
        return awaitPlayTask(tokenProvider.request(tokenRequest), REQUEST_TIMEOUT_MS).token()
    }
}

@Throws(IntegrityAttestationException::class)
private fun <T> awaitPlayTask(task: Task<T>, timeoutMs: Long): T {
    try {
        return Tasks.await(task, timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: ExecutionException) {
        val cause = e.cause
        if (cause is StandardIntegrityException &&
            cause.errorCode == StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID
        ) {
            throw IntegrityProviderInvalidException(cause)
        }
        throw IntegrityAttestationException("Play Integrity request failed", cause ?: e)
    } catch (e: TimeoutException) {
        throw IntegrityAttestationException("Play Integrity request timed out", e)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IntegrityAttestationException("Play Integrity request interrupted", e)
    }
}
