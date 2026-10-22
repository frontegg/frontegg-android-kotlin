package com.frontegg.android.services

import android.app.Activity
import android.util.Base64
import com.frontegg.android.AuthenticationActivity
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.exceptions.MfaRequiredException
import com.frontegg.android.fronteggAuth
import com.frontegg.android.utils.AuthorizeUrlGenerator
import org.json.JSONObject

class MultiFactorAuthenticator {
    private val storage = StorageProvider.getInnerStorage()

    fun createMFALoginAction(
        mfaRequestData: String
    ): String {
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
        return Base64.encodeToString(jsonData, Base64.NO_WRAP)
    }

    fun start(
        activity: Activity,
        callback: ((error: Exception?) -> Unit)?,
        mfaRequestData: String,
        continueInLoginBox: Boolean = false,
    ) {

        val authCallback: (error: Exception?) -> Unit = { exception ->
            if (activity.fronteggAuth.isAuthenticated.value) {
                callback?.invoke(exception)
            } else {
                val error = FailedToAuthenticateException(error = "Failed to authenticate with MFA")
                callback?.invoke(error)
            }
        }

        val loginDirectAction = createMFALoginAction(mfaRequestData)

        // MFA continues in the open login box, except in a step-up, which completes only through its own flow.
        val reusesLoginBox = continueInLoginBox && storage.isEmbeddedMode && !FronteggState.isStepUpAuthorization.value
        if (reusesLoginBox && activity is EmbeddedAuthActivity) {
            val loginBox = EmbeddedAuthActivity.liveLoginBox(activity)
            if (loginBox == null) {
                // The login box closed during sign-in, so the sign-in ends instead of opening a new login box.
                callback?.invoke(CanceledByUserException())
                return
            }
            loginBox.loadAuthorizeUrl(AuthorizeUrlGenerator(loginBox).generate(loginAction = loginDirectAction).first)
            callback?.invoke(MfaRequiredException(mfaRequestData))
            return
        }

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