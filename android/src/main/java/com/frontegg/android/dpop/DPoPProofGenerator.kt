package com.frontegg.android.dpop

import android.util.Log
import com.google.gson.JsonObject
import okhttp3.HttpUrl
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.ProviderException
import java.security.Signature
import java.security.SignatureException
import java.security.interfaces.ECPublicKey
import java.util.UUID

internal class DPoPProofGenerator(
    private val keyProvider: DPoPKeyProvider,
    private val currentEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    @Volatile
    private var cachedThumbprint: String? = null

    @Throws(GeneralSecurityException::class, IOException::class)
    fun createProof(
        httpMethod: String,
        httpUrl: HttpUrl,
        accessToken: String? = null,
        nonce: String? = null,
    ): String {
        val keyPair = keyProvider.getOrCreateKeyPair()
        val publicKey = keyPair.public as? ECPublicKey ?: throw InvalidKeyException("DPoP key is not an EC key")

        val header = JsonObject().apply {
            addProperty("typ", "dpop+jwt")
            addProperty("alg", "ES256")
            add("jwk", publicJwk(publicKey))
        }
        val claims = JsonObject().apply {
            addProperty("jti", UUID.randomUUID().toString())
            addProperty("htm", httpMethod)
            addProperty("htu", httpUrl.newBuilder().query(null).fragment(null).build().toString())
            addProperty("iat", currentEpochSeconds())
            accessToken?.let { addProperty("ath", DPoPEncoding.sha256Base64Url(it)) }
            nonce?.let { addProperty("nonce", it) }
        }

        val signingInput = "${encodeSegment(header)}.${encodeSegment(claims)}"
        val derSignature = Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(keyPair.private)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            sign()
        }
        val joseSignature = try {
            DPoPEncoding.derSignatureToJose(derSignature)
        } catch (e: IllegalArgumentException) {
            throw SignatureException("Unexpected ECDSA signature encoding", e)
        }
        return "$signingInput.${DPoPEncoding.base64Url(joseSignature)}"
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    fun thumbprint(): String {
        cachedThumbprint?.let { return it }
        val publicKey = keyProvider.getOrCreateKeyPair().public as? ECPublicKey
            ?: throw InvalidKeyException("DPoP key is not an EC key")
        return jwkThumbprint(publicKey).also { cachedThumbprint = it }
    }

    /** Deletes the device key so the next session binds a new one; tokens bound to the old key stop working. */
    fun rotateKey() {
        cachedThumbprint = null
        try {
            keyProvider.deleteKeyPair()
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "Failed to rotate the DPoP key", e)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to rotate the DPoP key", e)
        } catch (e: ProviderException) {
            Log.w(TAG, "Failed to rotate the DPoP key", e)
        }
    }

    private fun encodeSegment(json: JsonObject): String =
        DPoPEncoding.base64Url(json.toString().toByteArray(Charsets.UTF_8))

    companion object {
        private const val TAG = "DPoPProofGenerator"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

        fun publicJwk(publicKey: ECPublicKey): JsonObject = JsonObject().apply {
            addProperty("kty", "EC")
            addProperty("crv", "P-256")
            addProperty("x", coordinate(publicKey, isX = true))
            addProperty("y", coordinate(publicKey, isX = false))
        }

        /** RFC 7638 JWK thumbprint: members in lexicographic order, no whitespace. */
        fun jwkThumbprint(publicKey: ECPublicKey): String {
            val x = coordinate(publicKey, isX = true)
            val y = coordinate(publicKey, isX = false)
            return DPoPEncoding.sha256Base64Url("""{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}""")
        }

        private fun coordinate(publicKey: ECPublicKey, isX: Boolean): String {
            val value = if (isX) publicKey.w.affineX else publicKey.w.affineY
            return DPoPEncoding.base64Url(DPoPEncoding.unsignedFixedLength(value))
        }
    }
}
