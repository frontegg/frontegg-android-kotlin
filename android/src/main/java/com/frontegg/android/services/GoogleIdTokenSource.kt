package com.frontegg.android.services

import android.app.Activity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Supplies a Google ID token for native Google sign-in.
 */
interface GoogleIdTokenSource {
    /**
     * Asks the user to pick a Google account and returns its ID token.
     * @param serverClientId the OAuth web client ID configured for Google social login in Frontegg.
     * @throws CanceledByUserException if the user dismisses the account picker.
     */
    suspend fun getIdToken(serverClientId: String): String
}

/**
 * [GoogleIdTokenSource] backed by Credential Manager. Offers the accounts that already signed
 * in to the app first, and every Google account on the device when there are none.
 */
class CredentialManagerGoogleIdTokenSource(
    private val activity: Activity,
    private val credentialManager: CredentialManager = CredentialManager.create(activity.applicationContext),
) : GoogleIdTokenSource {

    override suspend fun getIdToken(serverClientId: String): String {
        val response = try {
            requestGoogleCredential(serverClientId, onlyAuthorizedAccounts = true)
        } catch (e: NoCredentialException) {
            requestGoogleCredential(serverClientId, onlyAuthorizedAccounts = false)
        }
        return extractIdToken(response.credential)
    }

    private suspend fun requestGoogleCredential(
        serverClientId: String,
        onlyAuthorizedAccounts: Boolean,
    ): GetCredentialResponse {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(onlyAuthorizedAccounts)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            credentialManager.getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw CanceledByUserException()
        }
    }

    private fun extractIdToken(credential: Credential): String {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw FailedToAuthenticateException(error = "Unexpected credential type: ${credential.type}")
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw FailedToAuthenticateException(error = "Invalid Google ID token credential")
        }
    }
}
