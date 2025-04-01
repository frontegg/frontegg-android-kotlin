package com.frontegg.android.services

import android.app.Activity
import com.frontegg.android.AuthenticationActivity
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.utils.StepUpConstants
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration

class StepUpAuthenticator(
    private val credentialManager: CredentialManager,
    private val storage: FronteggInnerStorage = StorageProvider.getInnerStorage()
) {

    fun isSteppedUp(
        maxAge: Duration? = null
    ): Boolean {
        val accessToken = credentialManager.get(CredentialKeys.ACCESS_TOKEN) ?: return false

        val jwt = JWTHelper.decode(accessToken)

        val authTime = jwt.auth_time
        if (maxAge != null && authTime != null) {
            val nowInSeconds = Instant.now().toEpochMilli() / 1000
            val isMaxAgeValid = nowInSeconds - authTime <= maxAge.inWholeSeconds
            if (!isMaxAgeValid) return false
        }

        val isACRValid = jwt.acr == StepUpConstants.ACR_VALUE
        val isAMRIncludesMFA = jwt.amr.indexOf(StepUpConstants.AMR_MFA_VALUE) != -1
        val isAMRIncludesMethod = StepUpConstants.AMR_ADDITIONAL_VALUE.firstOrNull { method ->
            jwt.amr.indexOf(method) != -1
        } != null

        return isACRValid && isAMRIncludesMFA && isAMRIncludesMethod;
    }


    @OptIn(DelicateCoroutinesApi::class)
    fun stepUp(
        activity: Activity,
        maxAge: Duration? = null,
        callback: ((Exception?) -> Unit)?,
    ) {
        embeddedReStepUp = false
        startOpenAuthActivityInEmbeddedMode = false
        val updatedCallback: ((Exception?) -> Unit) = { exception ->
            if (FronteggState.isStepUpAuthorization.value) {
                FronteggState.isStepUpAuthorization.value = false
            }

            FronteggState.showLoader.value = false
            GlobalScope.launch(Dispatchers.Main) {
                callback?.invoke(exception)
            }
            StepUpAuthenticator.callback = null
            StepUpAuthenticator.maxAge = null
        }

        StepUpAuthenticator.maxAge = maxAge
        StepUpAuthenticator.callback = updatedCallback

        FronteggState.showLoader.value = true
        FronteggState.isStepUpAuthorization.value = true
        authenticateWithStepUp(activity, maxAge, updatedCallback)
    }

    private fun authenticateWithStepUp(
        activity: Activity,
        maxAge: Duration?,
        callback: ((Exception?) -> Unit),
    ) {
        if (storage.isEmbeddedMode) {
            EmbeddedAuthActivity.authenticateWithStepUp(activity, maxAge, callback)
        } else {
            AuthenticationActivity.authenticateWithStepUp(activity, maxAge, callback)
        }
    }

    fun handleHostedLoginCallback(
        activity: Activity?,
    ): Boolean {
        if (!FronteggState.isStepUpAuthorization.value) return false
        if (activity != null && activity is AuthenticationActivity &&
            !startOpenAuthActivityInEmbeddedMode &&
            storage.isEmbeddedMode &&
            !isSteppedUp(maxAge)
        ) {
            AuthenticationActivity.authenticateWithStepUp(
                activity,
                maxAge,
                callback,
            )
            startOpenAuthActivityInEmbeddedMode = true
            return true
        }
        embeddedReStepUp = false
        FronteggState.isStepUpAuthorization.value = false
        return false
    }

    companion object {
        private var embeddedReStepUp = false
        private var startOpenAuthActivityInEmbeddedMode = false
        private var maxAge: Duration? = null
        private var callback: ((Exception?) -> Unit)? = null

        fun resumeAuthenticationActivity() {
            if (startOpenAuthActivityInEmbeddedMode) {
                embeddedReStepUp = true
            }
        }

        fun resumeEmbeddedActivity() {
            if (embeddedReStepUp) {
                callback?.invoke(CanceledByUserException())
                EmbeddedAuthActivity.onAuthFinishedCallback = null
                FronteggState.isStepUpAuthorization.value = false
                embeddedReStepUp = false
                startOpenAuthActivityInEmbeddedMode = false
            }
        }
    }
}