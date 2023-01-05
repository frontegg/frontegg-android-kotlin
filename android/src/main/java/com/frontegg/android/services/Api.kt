package com.frontegg.android.services

import com.frontegg.android.utils.CredentialKeys
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

open class Api {
    private var baseUrl: String
    private var clientId: String
    private var credentialManager: CredentialManager
    private var httpClient: OkHttpClient

    constructor(baseUrl: String, clientId: String, credentialManager: CredentialManager) {
        this.baseUrl = baseUrl
        this.clientId = clientId
        this.credentialManager = credentialManager
        this.httpClient = OkHttpClient()
    }


    private fun prepareHeaders(): Headers {

        val headers: Map<String, String> = mapOf()

        headers.plus(Pair("Content-Type", "application/json"))
        headers.plus(Pair("Accept", "application/json"))
        headers.plus(Pair("Origin", this.baseUrl))

        val accessToken = this.credentialManager.get(CredentialKeys.ACCESS_TOKEN)
        if (accessToken != null) {
            headers.plus(Pair("authorization", "Bearer $accessToken"))
        }
        return headers.toHeaders()
    }

    private fun postRequest(path: String, body: JsonObject) {
        val url = "${this.baseUrl}/$path".toHttpUrl()
        val requestBuilder = Request.Builder()
        val bodyRequest =
            Gson().toJson(body).toRequestBody("application/json; charset=utf-8".toMediaType())
        val headers = this.prepareHeaders();

        requestBuilder.method("POST", bodyRequest)
        requestBuilder.headers(headers);
        requestBuilder.url(url)

        val request = requestBuilder.build()
        this.httpClient.newCall(request)
    }

    private fun getRequest(path: String) {
        val url = "${this.baseUrl}/$path".toHttpUrl()
        val requestBuilder = Request.Builder()
        val headers = this.prepareHeaders();

        requestBuilder.method("GET", null)
        requestBuilder.headers(headers);
        requestBuilder.url(url)

        val request = requestBuilder.build()
        this.httpClient.newCall(request)
    }
}