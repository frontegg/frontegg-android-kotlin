package com.frontegg.android.services

import android.app.Activity
import com.frontegg.android.embedded.CredentialManagerHandler
import com.frontegg.android.utils.AuthorizeUrlGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

object CredentialManagerHandlerProvider {
    fun getCredentialManagerHandler(activity: Activity): CredentialManagerHandler {
        return CredentialManagerHandler(activity)
    }
}

object ScopeProvider {
    val mainScope: CoroutineScope
        get() = MainScope()
}

object AuthorizeUrlGeneratorProvider {
    fun getAuthorizeUrlGenerator(): AuthorizeUrlGenerator {
        return AuthorizeUrlGenerator()
    }
}

object StorageProvider {
    fun getInnerStorage(): FronteggInnerStorage {
        return FronteggInnerStorage()
    }
}

object ApiProvider {
    fun getApi(credentialManager: CredentialManager): Api {
        return Api(credentialManager)
    }
}

object MultiFactorAuthenticatorProvider {
    fun getMultiFactorAuthenticator(): MultiFactorAuthenticator {
        return MultiFactorAuthenticator()
    }
}

object StepUpAuthenticatorProvider {
    fun getStepUpAuthenticator(
        credentialManager: CredentialManager,
    ): StepUpAuthenticator {
        return StepUpAuthenticator(
            credentialManager
        )
    }
}