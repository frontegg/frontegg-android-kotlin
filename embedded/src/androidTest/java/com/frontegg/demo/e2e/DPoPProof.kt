package com.frontegg.demo.e2e

import android.util.Base64
import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

/** An RFC 9449 DPoP proof JWS, decoded the way a server reads it. */
class DPoPProof private constructor(
    val header: JSONObject,
    val claims: JSONObject,
    private val signingInput: String,
    private val signatureSegment: String,
) {
    val jwk: JSONObject
        get() = header.getJSONObject("jwk")

    /** RFC 7638 thumbprint of the proof's public key, the value a server puts in `cnf.jkt`. */
    val jwkThumbprint: String
        get() {
            val members = listOf("crv", "kty", "x", "y").joinToString(",") { """"$it":"${jwk.getString(it)}"""" }
            return sha256Base64Url("{$members}")
        }

    /** Verifies the ES256 signature with the proof's own `jwk`. */
    fun hasValidSignature(): Boolean {
        val joseSignature = decode(signatureSegment)
        if (joseSignature.size != 2 * P256_COORDINATE_LENGTH) return false
        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey())
            update(signingInput.toByteArray(Charsets.US_ASCII))
            verify(joseToDer(joseSignature))
        }
    }

    private fun publicKey(): PublicKey {
        val point = ECPoint(BigInteger(1, decode(jwk.getString("x"))), BigInteger(1, decode(jwk.getString("y"))))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, p256Parameters))
    }

    companion object {
        private const val BASE64_URL_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        private const val P256_COORDINATE_LENGTH = 32

        private val p256Parameters: ECParameterSpec by lazy {
            val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            (generator.generateKeyPair().public as ECPublicKey).params
        }

        fun parse(jws: String): DPoPProof {
            val segments = jws.split('.')
            require(segments.size == 3) { "DPoP proof is not a compact JWS" }
            return DPoPProof(
                header = decodeJsonSegment(segments[0]),
                claims = decodeJsonSegment(segments[1]),
                signingInput = "${segments[0]}.${segments[1]}",
                signatureSegment = segments[2],
            )
        }

        /** Decodes a base64url JWT/JWS segment, such as an access token's claims. */
        fun decodeJsonSegment(segment: String): JSONObject = JSONObject(String(decode(segment), Charsets.UTF_8))

        fun sha256Base64Url(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(digest, BASE64_URL_FLAGS)
        }

        private fun decode(segment: String): ByteArray = Base64.decode(segment, BASE64_URL_FLAGS)

        private fun joseToDer(joseSignature: ByteArray): ByteArray {
            val r = derInteger(joseSignature.copyOfRange(0, P256_COORDINATE_LENGTH))
            val s = derInteger(joseSignature.copyOfRange(P256_COORDINATE_LENGTH, joseSignature.size))
            return byteArrayOf(0x30, (r.size + s.size).toByte()) + r + s
        }

        private fun derInteger(unsignedValue: ByteArray): ByteArray {
            val minimal = BigInteger(1, unsignedValue).toByteArray()
            return byteArrayOf(0x02, minimal.size.toByte()) + minimal
        }
    }
}
