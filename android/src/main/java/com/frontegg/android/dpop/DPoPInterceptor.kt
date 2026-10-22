package com.frontegg.android.dpop

import android.util.Log
import com.frontegg.android.utils.FronteggEndpoints
import com.frontegg.android.utils.JWTHelper
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
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
        val bearerToken = bearerToken(request)
        val bearerThumbprint = bearerToken?.let { cachedConfirmationThumbprint(it) }
        val boundAccessToken = bearerToken.takeIf { bearerThumbprint != null && isBoundToDevice(bearerThumbprint) }
        if (boundAccessToken == null && !isTokenRequest) return chain.proceed(request)

        val isBoundSessionTokenRequest = isTokenRequest && bearerThumbprint != null
        // RFC 9449 token requests carry only the proof, never a bound access token, whichever key it is bound to.
        val resourceAccessToken = boundAccessToken.takeUnless { isTokenRequest }
        val requiresProof = isBoundSessionTokenRequest || resourceAccessToken != null
        val baseRequest = if (isBoundSessionTokenRequest) {
            request.newBuilder().removeHeader(AUTHORIZATION_HEADER).build()
        } else {
            request
        }

        val host = request.url.host
        val sentNonce = noncesByHost[host]
        val firstAttempt = withProof(baseRequest, resourceAccessToken, sentNonce)
            ?: return proceedWithoutProof(chain, baseRequest, requiresProof)
        val response = chain.proceed(firstAttempt)

        val receivedNonce = response.header(DPOP_NONCE_HEADER)?.takeIf { it.isNotBlank() }
            ?: return response
        noncesByHost[host] = receivedNonce
        if (receivedNonce == sentNonce) return response
        val isNonceChallenge = try {
            isNonceChallenge(response)
        } catch (e: IOException) {
            Log.w(TAG, "Could not read the DPoP challenge body: ${e.javaClass.simpleName}")
            false
        }
        if (!isNonceChallenge) return response

        val retry = withProof(baseRequest, resourceAccessToken, receivedNonce) ?: return response
        response.close()
        val retryResponse = chain.proceed(retry)
        retryResponse.header(DPOP_NONCE_HEADER)?.takeIf { it.isNotBlank() }?.let { noncesByHost[host] = it }
        return retryResponse
    }

    // Without a proof a bound token's request would be rejected and could end the session, so it is not sent.
    @Throws(IOException::class)
    private fun proceedWithoutProof(chain: Interceptor.Chain, request: Request, requiresProof: Boolean): Response {
        if (requiresProof) {
            throw IOException("No DPoP proof could be created; the request for a bound token was not sent")
        }
        return chain.proceed(request)
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

    // An unreadable device key cannot rule out a match, so a token bound to any key is treated as bound here.
    private fun isBoundToDevice(confirmationThumbprint: String): Boolean {
        val deviceThumbprint = deviceThumbprint() ?: return true
        return deviceThumbprint == confirmationThumbprint
    }

    private fun deviceThumbprint(): String? {
        return try {
            proofGenerator.thumbprint()
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
        Log.w(TAG, "Could not use the DPoP key: ${error.javaClass.simpleName}")
    }

    private fun isTokenRequest(relativePath: String): Boolean {
        return relativePath == FronteggEndpoints.TOKEN_PATH ||
            relativePath == FronteggEndpoints.SILENT_AUTHORIZE_PATH ||
            FronteggEndpoints.isSocialLoginPostLogin(relativePath)
    }

    private fun bearerToken(request: Request): String? {
        val authorization = request.header(AUTHORIZATION_HEADER) ?: return null
        if (!authorization.startsWith("$BEARER_SCHEME ")) return null
        return authorization.substring(BEARER_SCHEME.length + 1).trim()
    }

    private fun cachedConfirmationThumbprint(accessToken: String): String? {
        lastConfirmation?.let { (token, thumbprint) -> if (token == accessToken) return thumbprint }
        return confirmationThumbprint(accessToken).also { lastConfirmation = accessToken to it }
    }

    // The body is read only for a possible challenge, so successful responses are never peeked.
    @Throws(IOException::class)
    private fun isNonceChallenge(response: Response): Boolean {
        if (!isChallengeStatus(response.code)) return false
        return isNonceChallenge(
            response.code,
            response.header(WWW_AUTHENTICATE_HEADER),
            response.peekBody(MAX_ERROR_BODY_BYTES).string(),
        )
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

        /** True when a 400 or 401 asks the client to retry with a server-provided DPoP nonce. */
        fun isNonceChallenge(code: Int, wwwAuthenticate: String?, body: String): Boolean =
            isChallengeStatus(code) &&
                (wwwAuthenticate.orEmpty().contains(USE_DPOP_NONCE_ERROR) || body.contains(USE_DPOP_NONCE_ERROR))

        private fun isChallengeStatus(code: Int): Boolean = code == HTTP_BAD_REQUEST || code == HTTP_UNAUTHORIZED

        /** Returns the `cnf.jkt` claim of a JWT access token, or null if it has none. */
        fun confirmationThumbprint(accessToken: String): String? =
            (JWTHelper.decodeClaims(accessToken)["cnf"] as? Map<*, *>)?.get("jkt") as? String
    }
}
