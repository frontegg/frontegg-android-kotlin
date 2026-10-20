package com.frontegg.android.services

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CredentialManagerGoogleIdTokenSourceTest {
    private val activity = mockk<Activity>(relaxed = true)
    private val credentialManager = mockk<CredentialManager>()
    private val idTokenSource = CredentialManagerGoogleIdTokenSource(activity, credentialManager)

    private val googleIdToken = "header.payload.signature"
    private val googleCredentialResponse = GetCredentialResponse(
        GoogleIdTokenCredential.Builder()
            .setId("user@example.com")
            .setIdToken(googleIdToken)
            .build()
    )

    private fun GetCredentialRequest.googleIdOption(): GetGoogleIdOption =
        credentialOptions.single() as GetGoogleIdOption

    @Test
    fun `getIdToken returns the ID token of an already authorized account`() = runBlocking {
        val requests = mutableListOf<GetCredentialRequest>()
        coEvery { credentialManager.getCredential(activity, capture(requests)) } returns googleCredentialResponse

        val idToken = idTokenSource.getIdToken("web-client-id")

        assert(idToken == googleIdToken)
        val option = requests.single().googleIdOption()
        assert(option.serverClientId == "web-client-id")
        assert(option.filterByAuthorizedAccounts)
        assert(!option.nonce.isNullOrBlank())
    }

    @Test
    fun `getIdToken offers every account when none has authorized the app`() = runBlocking {
        val requests = mutableListOf<GetCredentialRequest>()
        coEvery { credentialManager.getCredential(activity, capture(requests)) } throws
            NoCredentialException() andThen googleCredentialResponse

        val idToken = idTokenSource.getIdToken("web-client-id")

        assert(idToken == googleIdToken)
        assert(requests.size == 2)
        assert(requests[0].googleIdOption().filterByAuthorizedAccounts)
        assert(!requests[1].googleIdOption().filterByAuthorizedAccounts)
    }

    @Test
    fun `getIdToken maps a dismissed account picker to CanceledByUserException`() {
        coEvery { credentialManager.getCredential(activity, any<GetCredentialRequest>()) } throws
            GetCredentialCancellationException()

        val thrown = runCatching { runBlocking { idTokenSource.getIdToken("web-client-id") } }.exceptionOrNull()

        assert(thrown is CanceledByUserException) { "Expected CanceledByUserException, got $thrown" }
        coVerify(exactly = 1) { credentialManager.getCredential(activity, any<GetCredentialRequest>()) }
    }

    @Test
    fun `getIdToken rejects a credential that is not a Google ID token`() {
        coEvery { credentialManager.getCredential(activity, any<GetCredentialRequest>()) } returns
            GetCredentialResponse(PublicKeyCredential("{}"))

        val thrown = runCatching { runBlocking { idTokenSource.getIdToken("web-client-id") } }.exceptionOrNull()

        assert(thrown is FailedToAuthenticateException) { "Expected FailedToAuthenticateException, got $thrown" }
    }
}
