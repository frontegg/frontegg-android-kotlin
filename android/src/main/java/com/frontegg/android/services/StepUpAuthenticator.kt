package com.frontegg.android.services

import android.app.Activity
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.utils.StepUpConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.time.Duration

class StepUpAuthenticator(
    private val api: Api,
    private val credentialManager: CredentialManager,
    private val multiFactorAuthenticator: MultiFactorAuthenticator = MultiFactorAuthenticatorProvider.getMultiFactorAuthenticator(),
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

    suspend fun stepUp(
        activity: Activity,
        maxAge: Duration? = null,
        callback: ((error: Exception?) -> Unit),
    ) {
        val mfaRequestData = withContext(Dispatchers.IO) {
            api.generateStepUp(maxAge?.inWholeSeconds)
        }

        val scope = ScopeProvider.mainScope
        scope.launch {
            multiFactorAuthenticator.start(activity, callback, mfaRequestData)
        }
    }
}