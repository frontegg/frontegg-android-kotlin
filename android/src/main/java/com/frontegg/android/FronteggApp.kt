package com.frontegg.android

import android.content.Context
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.exceptions.FronteggException.Companion.FRONTEGG_APP_MUST_BE_INITIALIZED
import com.frontegg.android.services.*

class FronteggApp(context: Context) {

    val auth: FronteggAuth
    val baseUrl: String
    val clientId: String
    private val api: Api
    private val credentialManager: CredentialManager

    init {
        instance = this
        baseUrl = "https://auth.davidantoon.me"
        clientId = "f7a3f13d-4cb5-4078-b785-e0189c287d4b"

        credentialManager = CredentialManager(context)
        api = Api(baseUrl, clientId, credentialManager)
        auth = FronteggAuth(baseUrl, clientId, api, credentialManager)
    }

    companion object {
        private var instance: FronteggApp? = null
        public fun getInstance(): FronteggApp? {
            if(instance == null){
                throw FronteggException(FRONTEGG_APP_MUST_BE_INITIALIZED)
            }
            return instance
        }
    }

}