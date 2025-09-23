package com.frontegg.android.utils

import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PKCE (Proof Key for Code Exchange) utilities
 */
object PKCEUtils {
    private const val TAG = "PKCEUtils"
    private const val CODE_VERIFIER_LENGTH = 128
    private const val CODE_CHALLENGE_METHOD = "S256"

    /**
     * Generate a cryptographically random code verifier
     * @return Base64URL-encoded code verifier
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(CODE_VERIFIER_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Generate code challenge from code verifier using SHA256
     * @param codeVerifier The code verifier
     * @return Base64URL-encoded code challenge
     */
    fun generateCodeChallenge(codeVerifier: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(codeVerifier.toByteArray())
            Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate code challenge", e)
            ""
        }
    }

    /**
     * Get code verifier from WebView storage
     * @param context Android context
     * @return Code verifier from storage or generated new one
     */
    fun getCodeVerifierFromWebview(context: android.content.Context): String {
        return try {
            val credentialManager = com.frontegg.android.services.CredentialManager(context)
            val storedVerifier = credentialManager.get(com.frontegg.android.utils.CredentialKeys.CODE_VERIFIER)
            
            if (!storedVerifier.isNullOrEmpty()) {
                Log.d(TAG, "Using stored code verifier")
                storedVerifier
            } else {
                val newVerifier = generateCodeVerifier()
                credentialManager.save(com.frontegg.android.utils.CredentialKeys.CODE_VERIFIER, newVerifier)
                Log.d(TAG, "Generated new code verifier")
                newVerifier
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get code verifier from WebView", e)
            generateCodeVerifier()
        }
    }
}
