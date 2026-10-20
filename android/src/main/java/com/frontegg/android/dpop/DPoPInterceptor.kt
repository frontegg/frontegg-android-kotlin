package com.frontegg.android.dpop

import android.util.Log
import com.frontegg.android.utils.FronteggEndpoints
import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Adds RFC 9449 DPoP proofs to Frontegg token requests and to requests carrying a device-bound token. */
internal class DPoPInterceptor(
    private val baseUrlProvider: () -> String,
    private val isEnabled: () -> Boolean = { DPoPProvider.isEnabled },
    private val proofGeneratorProvider: () -> DPoPProofGenerator = { DPoPProvider.proofGenerator },
) : Interceptor {

    private val noncesByHost = ConcurrentHashMap<String, String>()

    @Volatile
    private var lastConfirmation: Pair<String, String?>? = null

    private val proofGenerator: DPoPProofGenerator
        get() = proofGeneratorProvider()

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val baseUrl = baseUrlProvider()
        if (!isEnabled() || !FronteggEndpoints.isFronteggHost(request.url, baseUrl)) return chain.proceed(request)

        val isTokenRequest = isTokenRequest(FronteggEndpoints.relativePath(request.url, baseUrl))
        val boundAccessToken = boundAccessToken(request)
        if (boundAccessToken == null && !isTokenRequest) return chain.proceed(request)

        // RFC 9449 token requests carry only the proof, never a bound access token.
        val resourceAccessToken = boundAccessToken.takeUnless { isTokenRequest }
        val baseRequest = if (isTokenRequest && boundAccessToken != null) {
            request.newBuilder().removeHeader(AUTHORIZATION_HEADER).build()
        } else {
            request
        }

        val host = request.url.host
        val sentNonce = noncesByHost[host]
        val firstAttempt = withProof(baseRequest, resourceAccessToken, sentNonce)
            ?: return chain.proceed(baseRequest)
        val response = chain.proceed(firstAttempt)

        val receivedNonce = response.header(DPOP_NONCE_HEADER)?.takeIf { it.isNotBlank() }
            ?: return response
        noncesByHost[host] = receivedNonce
        if (receivedNonce == sentNonce) return response
        val isNonceChallenge = try {
            isNonceChallenge(response)
        } catch (e: IOException) {
            response.close()
            throw e
        }
        if (!isNonceChallenge) return response

        val retry = withProof(baseRequest, resourceAccessToken, receivedNonce) ?: return response
        response.close()
        return chain.proceed(retry)
    }

    private fun withProof(request: Request, resourceAccessToken: String?, nonce: String?): Request? {
        val proof = createProofOrNull(request, resourceAccessToken, nonce) ?: return null
        val builder = request.newBuilder().header(DPOP_HEADER, proof)
        if (resourceAccessToken != null) {
            builder.header(AUTHORIZATION_HEADER, "$DPOP_SCHEME $resourceAccessToken")
        }
        return builder.build()
    }

    private fun createProofOrNull(request: Request, boundAccessToken: String?, nonce: String?): String? {
        return try {
            proofGenerator.createProof(request.method, request.url, boundAccessToken, nonce)
        } catch (e: GeneralSecurityException) {
            logProofFailure(e)
            null
        } catch (e: IOException) {
            logProofFailure(e)
            null
        } catch (e: ProviderException) {
            logProofFailure(e)
            null
        } catch (e: IllegalArgumentException) {
            logProofFailure(e)
            null
        }
    }

    private fun logProofFailure(error: Exception) {
        Log.w(TAG, "Sending request without a DPoP proof: ${error.javaClass.simpleName}")
    }

    private fun isTokenRequest(relativePath: String): Boolean {
        return relativePath == FronteggEndpoints.TOKEN_PATH ||
            relativePath == FronteggEndpoints.SILENT_AUTHORIZE_PATH ||
            FronteggEndpoints.isSocialLoginPostLogin(relativePath)
    }

    private fun boundAccessToken(request: Request): String? {
        val authorization = request.header(AUTHORIZATION_HEADER) ?: return null
        if (!authorization.startsWith("$BEARER_SCHEME ")) return null
        val accessToken = authorization.substring(BEARER_SCHEME.length + 1).trim()
        val boundThumbprint = cachedConfirmationThumbprint(accessToken) ?: return null

        val deviceThumbprint = try {
            proofGenerator.thumbprint()
        } catch (e: GeneralSecurityException) {
            logProofFailure(e)
            return null
        } catch (e: IOException) {
            logProofFailure(e)
            return null
        } catch (e: ProviderException) {
            logProofFailure(e)
            return null
        } catch (e: IllegalArgumentException) {
            logProofFailure(e)
            return null
        }
        return accessToken.takeIf { boundThumbprint == deviceThumbprint }
    }

    private fun cachedConfirmationThumbprint(accessToken: String): String? {
        lastConfirmation?.let { (token, thumbprint) -> if (token == accessToken) return thumbprint }
        return confirmationThumbprint(accessToken).also { lastConfirmation = accessToken to it }
    }

    @Throws(IOException::class)
    private fun isNonceChallenge(response: Response): Boolean {
        if (response.code != HTTP_BAD_REQUEST && response.code != HTTP_UNAUTHORIZED) return false
        val challenge = response.header(WWW_AUTHENTICATE_HEADER).orEmpty()
        if (challenge.contains(USE_DPOP_NONCE_ERROR)) return true
        return response.peekBody(MAX_ERROR_BODY_BYTES).string().contains(USE_DPOP_NONCE_ERROR)
    }

    companion object {
        private const val TAG = "DPoPInterceptor"
        const val DPOP_HEADER = "DPoP"
        const val DPOP_NONCE_HEADER = "DPoP-Nonce"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val WWW_AUTHENTICATE_HEADER = "WWW-Authenticate"
        private const val BEARER_SCHEME = "Bearer"
        private const val DPOP_SCHEME = "DPoP"
        private const val USE_DPOP_NONCE_ERROR = "use_dpop_nonce"
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val MAX_ERROR_BODY_BYTES = 4096L

        /** Returns the `cnf.jkt` claim of a JWT access token, or null if it has none. */
        fun confirmationThumbprint(accessToken: String): String? = runCatching {
            val payloadSegment = accessToken.split('.').getOrNull(1) ?: return null
            val payload = String(Base64.getUrlDecoder().decode(payloadSegment), Charsets.UTF_8)
            JsonParser.parseString(payload).asJsonObject
                .getAsJsonObject("cnf")
                ?.get("jkt")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
        }.getOrNull()
    }
}
