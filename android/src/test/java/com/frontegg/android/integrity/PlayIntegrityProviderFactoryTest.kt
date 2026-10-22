package com.frontegg.android.integrity

import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayIntegrityProviderFactoryTest {

    private val playError = RuntimeException("play error")

    @Test
    fun `API_NOT_AVAILABLE is transient because a Play Store update clears it`() {
        val failure = playFailureFor(StandardIntegrityErrorCode.API_NOT_AVAILABLE, playError)

        assertFalse(failure is PlayIntegrityUnavailableException)
        assertFalse(failure is IntegrityProviderInvalidException)
        assertSame(playError, failure.cause)
    }

    @Test
    fun `errors that do not clear for this install turn attestation off`() {
        val permanentErrorCodes = listOf(
            StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
            StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
            StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID,
            StandardIntegrityErrorCode.APP_NOT_INSTALLED,
            StandardIntegrityErrorCode.APP_UID_MISMATCH,
        )

        permanentErrorCodes.forEach { errorCode ->
            assertTrue("$errorCode", playFailureFor(errorCode, playError) is PlayIntegrityUnavailableException)
        }
    }

    @Test
    fun `an invalid provider is reported so a new one is prepared`() {
        val failure = playFailureFor(StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID, playError)

        assertTrue(failure is IntegrityProviderInvalidException)
    }

    @Test
    fun `network and server errors are transient`() {
        val transientErrorCodes = listOf(
            StandardIntegrityErrorCode.NETWORK_ERROR,
            StandardIntegrityErrorCode.TOO_MANY_REQUESTS,
            StandardIntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE,
        )

        transientErrorCodes.forEach { errorCode ->
            val failure = playFailureFor(errorCode, playError)
            assertFalse("$errorCode", failure is PlayIntegrityUnavailableException)
            assertFalse("$errorCode", failure is IntegrityProviderInvalidException)
        }
    }
}
