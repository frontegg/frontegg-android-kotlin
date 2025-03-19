package com.frontegg.android.services

import android.app.Activity
import com.frontegg.android.AuthenticationActivity
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.exceptions.MFANotEnrolledException
import com.frontegg.android.exceptions.NotAuthenticatedException
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
    private val api: Api,
    private val credentialManager: CredentialManager,
    private val multiFactorAuthenticator: MultiFactorAuthenticator = MultiFactorAuthenticatorProvider.getMultiFactorAuthenticator(),
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

    private var maxAge: Duration? = null
    private var callback: ((Exception?) -> Unit)? = null

    @OptIn(DelicateCoroutinesApi::class)
    fun stepUp(
        activity: Activity,
        maxAge: Duration? = null,
        callback: ((Exception?) -> Unit)?,
    ) {
        val updatedCallback: ((Exception?) -> Unit) = { exception ->
            if (FronteggState.isStepUpAuthorization.value) {
                FronteggState.isStepUpAuthorization.value = false
            }

            FronteggState.showLoader.value = false
            GlobalScope.launch(Dispatchers.Main) {
                callback?.invoke(exception)
            }
        }

        this.maxAge = maxAge
        this.callback = updatedCallback

        FronteggState.showLoader.value = true
        FronteggState.isStepUpAuthorization.value = true

        GlobalScope.launch(Dispatchers.IO) {


            try {
                val scope = ScopeProvider.mainScope
//                    val mfaRequestData = api.generateStepUp(maxAge?.inWholeSeconds)
                scope.launch {
                    authenticateWithStepUp(activity, maxAge, updatedCallback)

//                    multiFactorAuthenticator.start(activity, updatedCallback, mfaRequestData)
                }
            } catch (e: Exception) {
                exceptionHandler(e, activity, maxAge, updatedCallback)
            }
        }
    }

    private fun exceptionHandler(
        e: Exception,
        activity: Activity,
        maxAge: Duration?,
        callback: ((Exception?) -> Unit)
    ) = when (e) {
        is NotAuthenticatedException -> {
            authenticateWithStepUp(activity, maxAge, callback)
        }

        is MFANotEnrolledException -> {
            authenticateWithStepUp(activity, maxAge, callback)
        }

        else -> {
            callback(e)
        }
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

    @OptIn(DelicateCoroutinesApi::class)
    fun handleHostedLoginCallback(
        activity: Activity?,
    ) {
        if (!FronteggState.isStepUpAuthorization.value) return
        if (storage.isEmbeddedMode && activity != null && !isSteppedUp(maxAge)) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val mfaRequestData = api.generateStepUp(maxAge?.inWholeSeconds)
                    val loginAction = multiFactorAuthenticator.createMFALoginAction(mfaRequestData)
                    EmbeddedAuthActivity.authenticateWithStepUpMultiFactor(
                        activity,
                        loginAction
                    )
                } catch (e: Exception) {
                    callback?.let { it(e) }
                }
            }
        } else {
            FronteggState.isStepUpAuthorization.value = false
        }
    }
}