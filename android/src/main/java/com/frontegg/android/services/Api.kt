package com.frontegg.android.services

import org.json.JSONObject

class Api {
    private var baseUrl: String
    private var clientId: String
    private var credentialManager: CredentialManager


    constructor(baseUrl: String, clientId: String, credentialManager: CredentialManager) {
        this.baseUrl = baseUrl
        this.clientId = clientId
        this.credentialManager = credentialManager
    }


    private fun postRequest(accessToken:String, path:String, body: JSONObject){

    }
}