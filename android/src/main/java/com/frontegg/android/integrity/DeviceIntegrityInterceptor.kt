package com.frontegg.android.integrity

import com.frontegg.android.utils.FronteggEndpoints
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException

internal const val DEVICE_INTEGRITY_HEADER = "frontegg-device-integrity-token"

/**
 * Base64url (unpadded) SHA-256 of the request body; the server recomputes it to bind the token.
 */
internal fun integrityRequestHash(body: ByteArray): String = FronteggEndpoints.sha256Base64Url(body)

/**
 * Attaches a Play Integrity token to sign-in requests sent to the Frontegg base URL.
 * Token refreshes are never attested: they run in the background and would spend quota.
 */
internal class DeviceIntegrityInterceptor(
    private val baseUrlProvider: () -> String,
    private val attestorProvider: () -> DeviceIntegrityAttestor? = { DeviceIntegrity.attestor },
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val attestor = attestorProvider() ?: return chain.proceed(request)
        val baseUrl = baseUrlProvider()
        if (request.method != "POST" || !FronteggEndpoints.isFronteggHost(request.url, baseUrl)) {
            return chain.proceed(request)
        }

        val endpointPath = FronteggEndpoints.relativePath(request.url, baseUrl)
        if (!isSignInEndpoint(endpointPath)) return chain.proceed(request)

        val body = readBody(request) ?: return chain.proceed(request)
        if (endpointPath == FronteggEndpoints.TOKEN_PATH && !isAuthorizationCodeGrant(body)) {
            return chain.proceed(request)
        }

        val integrityToken = attestor.attest(integrityRequestHash(body)) ?: return chain.proceed(request)
        val attestedRequest = request.newBuilder()
            .header(DEVICE_INTEGRITY_HEADER, integrityToken)
            .build()
        return chain.proceed(attestedRequest)
    }

    private fun readBody(request: Request): ByteArray? {
        val requestBody = request.body ?: return ByteArray(0)
        return try {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            buffer.readByteArray()
        } catch (e: IOException) {
            null
        }
    }

    private fun isSignInEndpoint(endpointPath: String): Boolean {
        return when (endpointPath) {
            FronteggEndpoints.TOKEN_PATH,
            FronteggEndpoints.PASSKEY_POST_LOGIN_PATH -> true
            else -> FronteggEndpoints.isSocialLoginPostLogin(endpointPath)
        }
    }

    private fun isAuthorizationCodeGrant(body: ByteArray): Boolean {
        return try {
            val json = JsonParser.parseString(String(body, Charsets.UTF_8))
            json.isJsonObject && json.asJsonObject.get("grant_type")?.asString == "authorization_code"
        } catch (e: JsonParseException) {
            false
        } catch (e: IllegalStateException) {
            false
        } catch (e: UnsupportedOperationException) {
            false
        }
    }
}
