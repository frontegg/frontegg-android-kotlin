package com.frontegg.android.dpop

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

internal class SoftwareDPoPKeyProvider : DPoPKeyProvider {
    var keyPair: KeyPair? = null
    var deleteCount = 0
    var failure: GeneralSecurityException? = null

    override fun getOrCreateKeyPair(): KeyPair {
        failure?.let { throw it }
        return keyPair ?: KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
            .also { keyPair = it }
    }

    override fun deleteKeyPair() {
        keyPair = null
        deleteCount++
    }
}

internal object DPoPTestSupport {
    fun base64UrlDecode(segment: String): ByteArray = Base64.getUrlDecoder().decode(segment)

    fun decodeSegment(proof: String, index: Int): JsonObject =
        JsonParser.parseString(String(base64UrlDecode(proof.split('.')[index]), Charsets.UTF_8)).asJsonObject

    fun joseToDer(joseSignature: ByteArray): ByteArray {
        val half = joseSignature.size / 2
        val r = derInteger(joseSignature.copyOfRange(0, half))
        val s = derInteger(joseSignature.copyOfRange(half, joseSignature.size))
        val body = r + s
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    fun accessTokenBoundTo(thumbprint: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256"}""".toByteArray())
        val payload = encoder.encodeToString("""{"sub":"user","cnf":{"jkt":"$thumbprint"}}""".toByteArray())
        return "$header.$payload.signature"
    }

    private fun derInteger(unsignedBytes: ByteArray): ByteArray {
        val minimal = BigInteger(1, unsignedBytes).toByteArray()
        return byteArrayOf(0x02, minimal.size.toByte()) + minimal
    }
}
