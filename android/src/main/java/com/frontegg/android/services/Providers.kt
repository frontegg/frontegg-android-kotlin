package com.frontegg.android.services

import android.app.Activity
import com.frontegg.android.embedded.CredentialManagerHandler
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