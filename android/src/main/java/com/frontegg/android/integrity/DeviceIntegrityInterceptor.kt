package com.frontegg.android.integrity

import com.frontegg.android.utils.ApiConstants
import com.frontegg.android.utils.Constants
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

internal const val DEVICE_INTEGRITY_HEADER = "frontegg-device-integrity-token"

/**
 * Base64url (unpadded) SHA-256 of the request body; the server recomputes it to bind the token.
 */
internal fun integrityRequestHash(body: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(body)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

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
        if (request.method != "POST" || !isFronteggHost(request)) return chain.proceed(request)

        val body = readBody(request) ?: return chain.proceed(request)
        if (!isSignInRequest(request, body)) return chain.proceed(request)

        val integrityToken = attestor.attest(integrityRequestHash(body)) ?: return chain.proceed(request)
        val attestedRequest = request.newBuilder()
            .header(DEVICE_INTEGRITY_HEADER, integrityToken)
            .build()
        return chain.proceed(attestedRequest)
    }

    private fun isFronteggHost(request: Request): Boolean {
        val baseHost = baseUrlProvider().toHttpUrlOrNull()?.host ?: return false
        return request.url.host.equals(baseHost, ignoreCase = true)
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

    private fun isSignInRequest(request: Request, body: ByteArray): Boolean {
        val endpointPath = endpointPath(request.url.encodedPath)
        return when {
            endpointPath == "/${ApiConstants.exchangeToken}" -> isAuthorizationCodeGrant(body)
            endpointPath == "/${ApiConstants.webauthnPostlogin}" -> true
            endpointPath == "/${ApiConstants.silentRefreshToken}" -> true
            else -> isSocialLoginPostLogin(endpointPath)
        }
    }

    private fun endpointPath(encodedPath: String): String {
        val path = encodedPath.replace(Regex("/+"), "/").trimEnd('/')
        val basePath = Constants.normalizedBasePath(baseUrlProvider())
        return if (basePath.isNotEmpty() && path.startsWith("$basePath/")) path.removePrefix(basePath) else path
    }

    private fun isSocialLoginPostLogin(endpointPath: String): Boolean {
        val template = "/${ApiConstants.socialLoginPostLogin}"
        val prefix = template.substringBefore(PROVIDER_PLACEHOLDER)
        val suffix = template.substringAfter(PROVIDER_PLACEHOLDER)
        if (endpointPath.length <= prefix.length + suffix.length) return false
        if (!endpointPath.startsWith(prefix) || !endpointPath.endsWith(suffix)) return false
        val provider = endpointPath.substring(prefix.length, endpointPath.length - suffix.length)
        return !provider.contains('/')
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

    companion object {
        private const val PROVIDER_PLACEHOLDER = "{provider}"
    }
}
