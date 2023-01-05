package com.frontegg.android

import android.content.Context
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
        private lateinit var instance: FronteggApp
        public fun getInstance(): FronteggApp {
            return instance
        }
    }

}