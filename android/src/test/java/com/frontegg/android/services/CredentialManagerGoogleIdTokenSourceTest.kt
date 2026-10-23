package com.frontegg.android.services

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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

    private fun GetCredentialRequest.signInWithGoogleOption(): GetSignInWithGoogleOption =
        credentialOptions.single() as GetSignInWithGoogleOption

    @Test
    fun `getIdToken starts the Sign in with Google flow for the Frontegg web client`() = runBlocking {
        val requests = mutableListOf<GetCredentialRequest>()
        coEvery { credentialManager.getCredential(activity, capture(requests)) } returns googleCredentialResponse

        val idToken = idTokenSource.getIdToken("web-client-id")

        assert(idToken == googleIdToken)
        val option = requests.single().signInWithGoogleOption()
        assert(option.serverClientId == "web-client-id")
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
