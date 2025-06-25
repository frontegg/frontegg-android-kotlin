package com.frontegg.android.embedded


import android.app.Activity
import android.util.Log
import androidx.credentials.*
import androidx.credentials.exceptions.*
import org.json.JSONObject

/**
 * A class that encapsulates the credential manager object and provides simplified APIs for
 * creating and retrieving public key credentials. For other types of credentials follow the
 * documentation https://developer.android.com/training/sign-in/passkeys
 */
class CredentialManagerHandler(private val activity: Activity) {

    private val mCredMan = CredentialManager.create(activity.applicationContext)
    private val TAG = "CredentialManagerHandler"

    /**
     * Encapsulates the create passkey API for credential manager in a less error-prone manner.
     *
     * @param request a create public key credential request JSON required by [CreatePublicKeyCredentialRequest].
     * @return [CreatePublicKeyCredentialResponse] containing the result of the credential creation.
     */
    suspend fun createPasskey(requestStr: String): CreatePublicKeyCredentialResponse {

        val json = JSONObject(requestStr)
        val authenticatorSelection = json.getJSONObject("authenticatorSelection");
        authenticatorSelection.put("residentKey", "preferred")
        authenticatorSelection.put("userVerification", "required")
        authenticatorSelection.put("authenticatorAttachment", "platform")
        authenticatorSelection.put("requireResidentKey", false)
        json.put("authenticatorSelection", authenticatorSelection)
        val request = json.toString()

        try {
            mCredMan.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }

        val createRequest = CreatePublicKeyCredentialRequest(request, null, true)
        try {
            return mCredMan.createCredential(
                activity,
                createRequest
            ) as CreatePublicKeyCredentialResponse
        } catch (e: CreateCredentialException) {
            // For error handling use guidance from https://developer.android.com/training/sign-in/passkeys
            Log.i(
                TAG,
                "Error creating credential: ErrMessage: ${e.errorMessage}, ErrType: ${e.type}"
            )
            throw e
        }
    }

    /**
     * Encapsulates the get passkey API for credential manager in a less error-prone manner.
     *
     * @param request a get public key credential request JSON required by [GetCredentialRequest].
     * @return [GetCredentialResponse] containing the result of the credential retrieval.
     */
    suspend fun getPasskey(request: String): GetCredentialResponse {
        val getRequest = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(request, null)))
        try {
            return mCredMan.getCredential(activity, getRequest)
        } catch (e: GetCredentialException) {
            // For error handling use guidance from https://developer.android.com/training/sign-in/passkeys
            Log.i(TAG, "Error retrieving credential: ${e.message}")
            throw e
        }
    }

    suspend fun saveCredential(username: String, password: String) {
        val request = CreatePasswordRequest(id = username, password = password)
        try {
            mCredMan.createCredential(
                request = request,
                context = activity,
            )
            Log.d(TAG, "Credential saved successfully")
        } catch (e: CreateCredentialException) {
            Log.e(TAG, "Failed to save credential: ${e.message}")
            throw e
        } catch (e: CreateCredentialProviderConfigurationException) {
            Log.e(TAG, "No provider dependencies found", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception", e)
            throw e
        }
    }

    suspend fun retrieveCredential(): Pair<String, String>? {
        val request = GetCredentialRequest(listOf(GetPasswordOption()))
        try {
            val result = mCredMan.getCredential(
                request = request,
                context = activity
            )

            val credential = result.credential
            if (credential is PasswordCredential) {
                val username = credential.id
                val password = credential.password

                Log.d(
                    TAG,
                    "Autofilled from CredentialManager: $username, ${password.length} characters"
                )

                return Pair(username, password)
            }
        } catch (e: GetCredentialException) {
            Log.d(TAG, "No saved credentials: ${e.message}")
        } catch (e: CreateCredentialProviderConfigurationException) {
            Log.e(TAG, "No provider dependencies found", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception", e)
            throw e
        }
        return null
    }
}
