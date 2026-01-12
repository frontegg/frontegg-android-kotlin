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
import com.frontegg.android.models.SocialLoginPostLoginRequest
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
import java.net.HttpURLConnection
import java.net.URL

open class Api(
    private var credentialManager: CredentialManager
) {
    private var httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // For slow networks (EDGE)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)   // For slow networks (EDGE)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val storage = StorageProvider.getInnerStorage()
    private val baseUrl: String
        get() {
            val direct = storage.baseUrl
            if (direct.isNotBlank()) return direct
            val fromSelected = storage.selectedRegion?.baseUrl
            if (!fromSelected.isNullOrBlank()) return fromSelected
            return storage.regions.firstOrNull()?.baseUrl ?: ""
        }

    /**
     * Get the base URL for network probing
     */
    fun getServerUrl(): String = baseUrl
    private val clientId: String
        get() {
            val direct = storage.clientId
            if (direct.isNotBlank()) return direct
            val fromSelected = storage.selectedRegion?.clientId
            if (!fromSelected.isNullOrBlank()) return fromSelected
            return storage.regions.firstOrNull()?.clientId ?: ""
        }
    private val applicationId: String?
        get() = storage.applicationId

    private fun refreshCookieName(): String {
        val clientIdWithoutFirstDash = clientId.replaceFirst("-", "")
        return "fe_refresh_$clientIdWithoutFirstDash"
    }
    private fun deviceCookieName(): String = "fe_device_${clientId}".replace("-", "")

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

    private fun buildPostRequestWithTimeout(
        path: String,
        data: JsonObject?,
        timeoutMs: Int,
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

        // Create client with custom timeout like Swift version
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val request = requestBuilder.build()
        return client.newCall(request)
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

    private fun buildGetRequestWithTimeout(path: String, timeoutMs: Long): Call {
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
        
        // Create client with increased timeout
        val clientWithTimeout = httpClient.newBuilder()
            .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
            
        return clientWithTimeout.newCall(request)
    }


    @Throws(IllegalArgumentException::class, IOException::class)
    fun me(): User? {
        val meCall = buildGetRequestWithTimeout(ApiConstants.me, 120000)
        val meResponse = meCall.execute()
        val tenantsCall = buildGetRequestWithTimeout(ApiConstants.tenants, 120000)
        val tenantsResponse = tenantsCall.execute()

        if (meResponse.isSuccessful && tenantsResponse.isSuccessful) {
            // Parsing JSON strings into JsonObject
            val gson = Gson()
            val mapType = object : TypeToken<MutableMap<String, Any>>() {}.type

            val meJsonStr = meResponse.body!!.string()
            val tenantsJsonStr = tenantsResponse.body!!.string()

            Log.d(TAG, "api.me() successful - me length: ${meJsonStr.length}, tenants length: ${tenantsJsonStr.length}")

            val meJson: MutableMap<String, Any> = gson.fromJson(meJsonStr, mapType)
            val tenantsJson: MutableMap<String, Any> = gson.fromJson(tenantsJsonStr, mapType)

            meJson["tenants"] = tenantsJson["tenants"] as Any
            meJson["activeTenant"] = tenantsJson["activeTenant"] as Any

            val merged = Gson().toJson(meJson)
            val user = Gson().fromJson(merged, User::class.java)
            Log.d(TAG, "api.me() returning user: ${user != null}")
            return user
        } else {
            Log.w(TAG, "api.me() failed - me: ${meResponse.code} ${meResponse.message}, tenants: ${tenantsResponse.code} ${tenantsResponse.message}")
        }

        return null
    }

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class)
    fun refreshToken(refreshToken: String): AuthResponse {
        Log.d(TAG, "Starting refresh token request")
        val body = JsonObject()
        body.addProperty("grant_type", "refresh_token")
        body.addProperty("refresh_token", refreshToken)

        // Use reasonable timeout for refresh token (30 seconds)
        val call = buildPostRequestWithTimeout(ApiConstants.refreshToken, body, 30000)

        Log.d(TAG, "Executing refresh token request...")
        val response = call.execute()
        Log.d(TAG, "Refresh token response received: code=${response.code}")
        
        if (response.isSuccessful) {
            val responseBody = response.body!!.string()
            Log.d(TAG, "Refresh token successful, parsing response")
            return Gson().fromJson(responseBody, AuthResponse::class.java)
        }
        
        // Handle 401 specifically like Swift version
        if (response.code == 401) {
            val errorBody = response.body?.string() ?: "unknown error"
            Log.e(TAG, "failed to refresh token, error: $errorBody")
            throw FailedToAuthenticateException(
                response.headers,
                "Refresh token failed: 401 - $errorBody"
            )
        }
        
        // Handle other errors
        val errorBody = response.body?.string() ?: "Unknown error occurred"
        Log.e(TAG, "Refresh token failed: code=${response.code}, body=$errorBody")
        throw FailedToAuthenticateException(
            response.headers,
            "Refresh token failed: ${response.code} - $errorBody"
        )
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
        response.use { // Ensure body is closed to avoid connection leaks
            if (it.isSuccessful) {
                return Gson().fromJson(it.body!!.string(), AuthResponse::class.java)
            } else {
                val errorBody = it.body?.string()
                Log.e(TAG, "exchangeToken failed: code=${it.code}, body=$errorBody")
                return null
            }
        }
    }

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class)
    fun authorizeWithTokens(
        refreshToken: String,
        deviceTokenCookie: String?,
    ): AuthResponse {
        // Format refresh token cookie
        val refreshTokenCookie = "${refreshCookieName()}=$refreshToken"
        val deviceCookieFormatted = if (!deviceTokenCookie.isNullOrBlank()) "${deviceCookieName()}=$deviceTokenCookie" else ""

        try {
            // Call silentHostedLoginRefreshToken
            return silentHostedLoginRefreshToken(refreshTokenCookie, deviceCookieFormatted)
        } catch (e: FailedToAuthenticateException) {
            // Log and rethrow for handling at a higher level
            Log.e(TAG, "Failed to authorize with tokens: ${e.message}")
            throw e
        }
    }

    fun logout(refreshToken: String) {
        try {
            val cookieName = refreshCookieName()
            val cookieValue = "$cookieName=$refreshToken"
            
            Log.d(TAG, "Logging out with cookie: $cookieName=***")

            val call = buildPostRequest(
                ApiConstants.logout, JsonObject(), mapOf(
                    Pair("cookie", cookieValue),
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

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class, CookieNotFoundException::class)
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

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticatePasskeysException::class, CookieNotFoundException::class)
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


    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class, CookieNotFoundException::class)
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

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class)
    fun socialLoginPostLogin(
        provider: String,
        request: SocialLoginPostLoginRequest
    ): AuthResponse {
        val gson = Gson()
        val jsonObject = gson.toJsonTree(request).asJsonObject

        val endpoint = ApiConstants.socialLoginPostLogin.replace("{provider}", provider)
        val call = buildPostRequest(endpoint, jsonObject)
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

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class)
    fun getSocialLoginConfig(): com.frontegg.android.models.SocialLoginConfig {
        val call = buildGetRequest("identity/resources/auth/v1/sso/config")
        val response = call.execute()

        val body = response.body
        if (!response.isSuccessful || body == null) {
            throw FailedToAuthenticateException(
                response.headers,
                body?.string() ?: "Unknown error occurred"
            )
        }
        return Gson().fromJson(response.body!!.string(), com.frontegg.android.models.SocialLoginConfig::class.java)
    }

    @Throws(IllegalArgumentException::class, IOException::class, FailedToAuthenticateException::class)
    fun getFeatureFlags(): String {
        val call = buildGetRequest("identity/resources/auth/v1/feature-flags")
        val response = call.execute()

        val body = response.body
        if (!response.isSuccessful || body == null) {
            throw FailedToAuthenticateException(
                response.headers,
                body?.string() ?: "Unknown error occurred"
            )
        }
        return response.body!!.string()
    }
}
