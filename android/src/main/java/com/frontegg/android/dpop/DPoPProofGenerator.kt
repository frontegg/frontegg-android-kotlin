package com.frontegg.android.dpop

import android.util.Log
import com.google.gson.JsonObject
import okhttp3.HttpUrl
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyPair
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Signature
import java.security.SignatureException
import java.security.interfaces.ECPublicKey
import java.util.UUID

internal class DPoPProofGenerator(
    private val keyProvider: DPoPKeyProvider,
    private val currentEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private class DeviceKey(val keyPair: KeyPair, val publicKey: ECPublicKey, val thumbprint: String)

    private val keyLock = Any()
    private var cachedKey: DeviceKey? = null

    @Throws(GeneralSecurityException::class, IOException::class)
    fun createProof(
        httpMethod: String,
        httpUrl: HttpUrl,
        accessToken: String? = null,
        nonce: String? = null,
    ): String {
        val deviceKey = deviceKey()

        val header = JsonObject().apply {
            addProperty("typ", "dpop+jwt")
            addProperty("alg", "ES256")
            add("jwk", publicJwk(deviceKey.publicKey))
        }
        val claims = JsonObject().apply {
            addProperty("jti", UUID.randomUUID().toString())
            addProperty("htm", httpMethod)
            addProperty("htu", proofTargetUri(httpUrl))
            addProperty("iat", currentEpochSeconds())
            accessToken?.let { addProperty("ath", DPoPEncoding.sha256Base64Url(it)) }
            nonce?.let { addProperty("nonce", it) }
        }

        val signingInput = "${encodeSegment(header)}.${encodeSegment(claims)}"
        val derSignature = try {
            sign(deviceKey.keyPair.private, signingInput)
        } catch (e: InvalidKeyException) {
            // Only the key that failed is rotated, never one a concurrent logout and login have replaced it with.
            synchronized(keyLock) { if (cachedKey === deviceKey) rotateKey() }
            throw e
        } catch (e: ProviderException) {
            // A stale Keystore handle may be transient: reload the key next time and keep tokens bound to it.
            synchronized(keyLock) { if (cachedKey === deviceKey) cachedKey = null }
            throw e
        }
        val joseSignature = try {
            DPoPEncoding.derSignatureToJose(derSignature)
        } catch (e: IllegalArgumentException) {
            throw SignatureException("Unexpected ECDSA signature encoding", e)
        }
        return "$signingInput.${DPoPEncoding.base64Url(joseSignature)}"
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    fun thumbprint(): String = deviceKey().thumbprint

    /** Deletes the device key so the next session binds a new one; tokens bound to the old key stop working. */
    fun rotateKey() {
        synchronized(keyLock) {
            cachedKey = null
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
    }

    // Loaded once per key: every Keystore read is an IPC call to the system service.
    @Throws(GeneralSecurityException::class, IOException::class)
    private fun deviceKey(): DeviceKey = synchronized(keyLock) {
        cachedKey ?: run {
            val keyPair = keyProvider.getOrCreateKeyPair()
            val publicKey = keyPair.public as? ECPublicKey ?: throw InvalidKeyException("DPoP key is not an EC key")
            DeviceKey(keyPair, publicKey, jwkThumbprint(publicKey)).also { cachedKey = it }
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun sign(privateKey: PrivateKey, signingInput: String): ByteArray =
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            sign()
        }

    // A base URL ending in "/" puts "//" in request paths, and servers compare htu with the canonical URI.
    private fun proofTargetUri(httpUrl: HttpUrl): String =
        httpUrl.newBuilder()
            .encodedPath(httpUrl.encodedPath.replace(REPEATED_SLASHES, "/"))
            .query(null)
            .fragment(null)
            .build()
            .toString()

    private fun encodeSegment(json: JsonObject): String =
        DPoPEncoding.base64Url(json.toString().toByteArray(Charsets.UTF_8))

    companion object {
        private const val TAG = "DPoPProofGenerator"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private val REPEATED_SLASHES = Regex("/{2,}")

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
