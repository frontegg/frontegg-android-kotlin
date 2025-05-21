package com.frontegg.android.services

import kotlin.jvm.Throws
import android.util.Log
import com.frontegg.android.exceptions.CookieNotFoundException
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.exceptions.FailedToAuthenticatePasskeysException
import com.frontegg.android.exceptions.FailedToRegisterWebAuthnDevice
import com.frontegg.android.exceptions.MfaRequiredException
import com.frontegg.android.exceptions.NotAuthenticatedException
import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.User
import com.frontegg.android.models.WebAuthnAssertionRequest
import com.frontegg.android.models.WebAuthnRegistrationRequest
import com.frontegg.android.utils.ApiConstants
import com.frontegg.android.utils.CredentialKeys
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

open class Api(
    private var credentialManager: CredentialManager
) {
    private var httpClient: OkHttpClient = OkHttpClient()
    private val storage = StorageProvider.getInnerStorage()
    private val baseUrl: String
        get() = storage.baseUrl
    private val clientId: String
        get() = storage.clientId
    private val applicationId: String?
        get() = storage.applicationId

    private val cookieName: String = "fe_refresh_$clientId".replaceFirst("-", "")

    companion object {
        val TAG: String = Api::class.java.simpleName
    }

    private fun prepareHeaders(additionalHeaders: Map<String, String> = mapOf()): Headers {

        val headers: MutableMap<String, String> = mutableMapOf(
            Pair("Content-Type", "application/json"),
            Pair("Accept", "application/json"),
            Pair("Origin", this.baseUrl)
        )

        val applicationId = this.applicationId
        if (applicationId != null) {
            headers["frontegg-requested-application-id"] = applicationId
        }

        additionalHeaders.forEach {
            headers[it.key] = it.value
        }

        val accessToken = this.credentialManager.get(CredentialKeys.ACCESS_TOKEN)
        if (accessToken != null) {
            headers["Authorization"] = "Bearer $accessToken"
        }
        return headers.toHeaders()
    }

    private fun buildPostRequest(
        path: String,
        data: JsonObject?,
        additionalHeaders: Map<String, String> = mapOf()
    ): Call {
        val url = "${this.baseUrl}/$path".toHttpUrl()
        val requestBuilder = Request.Builder()
        val bodyRequest =
            data?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val headers = this.prepareHeaders(additionalHeaders)

        requestBuilder.method("POST", bodyRequest)
        requestBuilder.headers(headers)
        requestBuilder.url(url)

        val request = requestBuilder.build()
        return this.httpClient.newCall(request)
    }

    private fun buildPutRequest(
        path: String,
        data: JsonObject,
        additionalHeaders: Map<String, String> = mapOf()
    ): Call {
        val url = "${this.baseUrl}/$path".toHttpUrl()
        val requestBuilder = Request.Builder()
        val bodyRequest =
            data.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val headers = this.prepareHeaders(additionalHeaders)

        requestBuilder.method("PUT", bodyRequest)
        requestBuilder.headers(headers)
        requestBuilder.url(url)

        val request = requestBuilder.build()
        return this.httpClient.newCall(request)
    }

    private fun buildGetRequest(path: String): Call {

        val url = if (path.startsWith("http")) {
            path.toHttpUrl()
        } else {
            "$baseUrl/$path".toHttpUrl()
        }
        val requestBuilder = Request.Builder()
        val headers = prepareHeaders()

        requestBuilder.method("GET", null)
        requestBuilder.headers(headers)
        requestBuilder.url(url)

        val request = requestBuilder.build()
        return this.httpClient.newCall(request)
    }

    @Throws(IllegalArgumentException::class, IOException::class)
    fun me(): User? {
        val meCall = buildGetRequest(ApiConstants.me)
        val meResponse = meCall.execute()
        val tenantsCall = buildGetRequest(ApiConstants.tenants)
        val tenantsResponse = tenantsCall.execute()

        if (meResponse.isSuccessful && tenantsResponse.isSuccessful) {
            // Parsing JSON strings into JsonObject
            val gson = Gson()
            val mapType = object : TypeToken<MutableMap<String, Any>>() {}.type

            val meJsonStr = meResponse.body!!.string()
            val tenantsJsonStr = tenantsResponse.body!!.string()

            val meJson: MutableMap<String, Any> = gson.fromJson(meJsonStr, mapType)
            val tenantsJson: MutableMap<String, Any> = gson.fromJson(tenantsJsonStr, mapType)

            meJson["tenants"] = tenantsJson["tenants"] as Any
            meJson["activeTenant"] = tenantsJson["activeTenant"] as Any

            val merged = Gson().toJson(meJson)
            return Gson().fromJson(merged, User::class.java)
        }

        return null
    }

    @Throws(IllegalArgumentException::class, IOException::class)
    fun refreshToken(refreshToken: String): AuthResponse? {
        val body = JsonObject()
        body.addProperty("grant_type", "refresh_token")
        body.addProperty("refresh_token", refreshToken)

        val call = buildPostRequest(ApiConstants.refreshToken, body)
        val response = call.execute()
        if (response.isSuccessful) {
            return Gson().fromJson(response.body!!.string(), AuthResponse::class.java)
        }
        return null
    }

    @Throws(IllegalArgumentException::class, IOException::class)
    fun exchangeToken(
        code: String, redirectUrl: String, codeVerifier: String
    ): AuthResponse? {

        val body = JsonObject()
        body.addProperty("code", code)
        body.addProperty("redirect_uri", redirectUrl)
        body.addProperty("code_verifier", codeVerifier)
        body.addProperty("grant_type", "authorization_code")


        val call = buildPostRequest(ApiConstants.exchangeToken, body)
        val response = call.execute()
        if (response.isSuccessful) {
            return Gson().fromJson(response.body!!.string(), AuthResponse::class.java)
        }
        return null
    }

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class)
    fun authorizeWithTokens(
        refreshToken: String,
        deviceTokenCookie: String?,
    ): AuthResponse {
        // Format refresh token cookie
        val refreshTokenCookie = "$cookieName=$refreshToken"

        try {
            // Call silentHostedLoginRefreshToken
            return silentHostedLoginRefreshToken(refreshTokenCookie, deviceTokenCookie ?: "")
        } catch (e: FailedToAuthenticateException) {
            // Log and rethrow for handling at a higher level
            Log.e(TAG, "Failed to authorize with tokens: ${e.message}")
            throw e
        }
    }

    fun logout(cookies: String, accessToken: String) {
        try {

            val call = buildPostRequest(
                ApiConstants.logout, JsonObject(), mapOf(
                    Pair("authorization", "Bearer $accessToken"),
                    Pair("cookie", cookies),
                    Pair("accept", "*/*"),
                    Pair("content-type", "application/json"),
                    Pair("origin", baseUrl),
                    Pair("referer", "$baseUrl/oauth/account/logout")
                )
            )
            val res = call.execute()


            Log.d(TAG, "logged out, ${res.body?.string()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to logout user", e)
        }
    }

    @Throws(IllegalArgumentException::class, IOException::class)
    fun switchTenant(tenantId: String) {
        val data = JsonObject()
        data.addProperty("tenantId", tenantId)
        val call = buildPutRequest(ApiConstants.switchTenant, data)
        call.execute()
    }

    @Throws(CookieNotFoundException::class)
    private fun getSetCookieValue(prefix: String, response: Response): String {

        val cookies = response.headers("set-cookie")
        val prefixedCookie = cookies.find { cookie ->
            cookie.startsWith(prefix)
        } ?: throw CookieNotFoundException(prefix)

        return prefixedCookie.split(';')[0]
    }

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class)
    fun webAuthnPrelogin(): WebAuthnAssertionRequest {

        val call = buildPostRequest(ApiConstants.webauthnPrelogin, JsonObject())
        val response = call.execute()
        val body = response.body

        if (!response.isSuccessful || body == null) {
            throw FailedToAuthenticateException(
                response.headers,
                body?.string() ?: "Unknown error occurred"
            )
        }

        val gson = Gson()
        val mapType = object : TypeToken<MutableMap<String, Any>>() {}.type
        val jsonRequest: MutableMap<String, Any> = gson.fromJson(response.body!!.string(), mapType)
        val requestOptions = jsonRequest["options"]

        val webAuthnCookie = getSetCookieValue("fe_webauthn_", response)

        return WebAuthnAssertionRequest(
            webAuthnCookie, gson.toJson(requestOptions)
        )
    }

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticatePasskeysException::class)
    fun webAuthnPostlogin(sessionCookie: String, challengeResponse: String): AuthResponse {

        val gson = Gson()
        val mapType = object : TypeToken<MutableMap<String, Any>>() {}.type
        val jsonRequest: MutableMap<String, Any> = gson.fromJson(challengeResponse, mapType)
        val jsonObject = gson.toJsonTree(jsonRequest).asJsonObject

        val call = buildPostRequest(
            ApiConstants.webauthnPostlogin, jsonObject, mapOf(
                "cookie" to sessionCookie,
            )
        )
        val response = call.execute()
        val body = response.body
        if (!response.isSuccessful || body == null) {
            throw FailedToAuthenticatePasskeysException(response.message)
        }

        val authResponseStr = body.string()
        val authResponse = gson.fromJson<MutableMap<String, Any>>(authResponseStr, mapType)
        val isMfaRequired = authResponse.getOrDefault("mfaRequired", false) as Boolean
        if (isMfaRequired) {
            throw MfaRequiredException(authResponseStr)
        }

        val refreshToken = getSetCookieValue("fe_refresh_", response)
        val deviceId = getSetCookieValue("fe_device_", response)

        return silentHostedLoginRefreshToken(refreshToken, deviceId)
    }


    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class)
    fun getWebAuthnRegisterChallenge(): WebAuthnRegistrationRequest {
        val accessToken = this.credentialManager.get(CredentialKeys.ACCESS_TOKEN)
        if (accessToken == null) {
            throw NotAuthenticatedException()
        }

        val call = buildPostRequest(ApiConstants.registerWebauthnDevice, JsonObject())
        val response = call.execute()
        val body = response.body

        if (!response.isSuccessful || body == null) {
            throw FailedToAuthenticateException(
                response.headers,
                body?.string() ?: "Unknown error occurred"
            )
        }


        val gson = Gson()
        val mapType = object : TypeToken<MutableMap<String, Any>>() {}.type
        val jsonRequest: MutableMap<String, Any> = gson.fromJson(response.body!!.string(), mapType)
        val requestOptions = jsonRequest["options"]

        val webAuthnCookie = getSetCookieValue("fe_webauthn_", response)

        return WebAuthnRegistrationRequest(
            webAuthnCookie, gson.toJson(requestOptions)
        )
    }

    @Throws(IllegalArgumentException::class, IOException::class, FailedToRegisterWebAuthnDevice::class, NotAuthenticatedException::class)
    fun verifyWebAuthnDevice(
        sessionCookie: String,
        challengeResponse: String
    ) {
        this.credentialManager.get(CredentialKeys.ACCESS_TOKEN) ?: throw NotAuthenticatedException()

        val gson = Gson()
        val mapType = object : TypeToken<MutableMap<String, Any>>() {}.type
        val jsonRequest: MutableMap<String, Any> = gson.fromJson(challengeResponse, mapType)
        val jsonObject = gson.toJsonTree(jsonRequest).asJsonObject


        val call = buildPostRequest(
            ApiConstants.verifyWebauthnDevice, jsonObject, mapOf(
                "cookie" to sessionCookie
            )
        )
        val response = call.execute()
        val body = response.body

        if (!response.isSuccessful || body == null) {
            throw FailedToRegisterWebAuthnDevice(
                response.headers,
                body?.string() ?: "Unknown error occurred"
            )
        }
    }

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class)
    private fun silentHostedLoginRefreshToken(
        refreshTokenCookie: String,
        deviceIdCookie: String
    ): AuthResponse {

        val call = buildPostRequest(
            ApiConstants.silentRefreshToken, JsonObject(), mapOf(
                "cookie" to "${refreshTokenCookie};${deviceIdCookie}"
            )
        )
        val response = call.execute()

        val body = response.body
        if (!response.isSuccessful || body == null) {
            throw FailedToAuthenticateException(
                response.headers,
                body?.string() ?: "Unknown error occurred"
            )
        }
        return Gson().fromJson(response.body!!.string(), AuthResponse::class.java)
    }
}
