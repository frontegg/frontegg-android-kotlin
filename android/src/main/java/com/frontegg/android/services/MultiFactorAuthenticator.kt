package com.frontegg.android.services

import android.app.Activity
import android.util.Base64
import com.frontegg.android.AuthenticationActivity
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.FronteggAuth
import com.frontegg.android.exceptions.FailedToAuthenticateException
import org.json.JSONObject

class MultiFactorAuthenticator {
    private val storage = StorageProvider.getInnerStorage()

    fun start(
        activity: Activity,
        callback: ((error: Exception?) -> Unit)?,
        mfaRequestData: String
    ) {

        val authCallback: () -> Unit = {
            if (FronteggAuth.instance.isAuthenticated.value) {
                callback?.invoke(null)
            } else {
                val error = FailedToAuthenticateException(error = "Failed to authenticate with MFA")
                callback?.invoke(error)
            }
        }

        val multiFactorStateBase64 =
            Base64.encodeToString(mfaRequestData.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val directLogin = mapOf(
            "type" to "direct",
            "data" to "${storage.baseUrl}/oauth/account/mfa-mobile-authenticator?state=$multiFactorStateBase64",
            "additionalQueryParams" to mapOf(
                "prompt" to "consent"
            )
        )
        val jsonData = JSONObject(directLogin).toString().toByteArray(Charsets.UTF_8)
        val loginDirectAction = Base64.encodeToString(jsonData, Base64.NO_WRAP)

        if (storage.isEmbeddedMode) {
            EmbeddedAuthActivity.authenticateWithMultiFactor(
                activity,
                loginDirectAction,
                authCallback
            )
        } else {
            AuthenticationActivity.authenticateWithMultiFactor(
                activity,
                loginDirectAction,
                authCallback
            )
        }
    }
}