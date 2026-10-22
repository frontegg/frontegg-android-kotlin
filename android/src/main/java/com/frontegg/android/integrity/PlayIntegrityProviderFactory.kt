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

// Prepare runs only in the background, so it gets long enough for a slow Play; sign-in waits on it separately.
private const val PREPARE_TIMEOUT_MS = 30_000L
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
        throw playFailure(e.cause ?: e)
    } catch (e: TimeoutException) {
        throw IntegrityAttestationException("Play Integrity request timed out", e)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IntegrityAttestationException("Play Integrity request interrupted", e)
    }
}

private fun playFailure(cause: Throwable): IntegrityAttestationException {
    if (cause is StandardIntegrityException) return playFailureFor(cause.errorCode, cause)
    return IntegrityAttestationException("Play Integrity request failed", cause)
}

// Errors that will not go away for this install turn attestation off; API_NOT_AVAILABLE clears on a Play Store update.
internal fun playFailureFor(errorCode: Int, cause: Throwable): IntegrityAttestationException {
    return when (errorCode) {
        StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID -> IntegrityProviderInvalidException(cause)
        StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
        StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
        StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID,
        StandardIntegrityErrorCode.APP_NOT_INSTALLED,
        StandardIntegrityErrorCode.APP_UID_MISMATCH -> PlayIntegrityUnavailableException(cause)
        else -> IntegrityAttestationException("Play Integrity request failed", cause)
    }
}
