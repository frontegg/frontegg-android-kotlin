package com.frontegg.android.dpop

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

class DPoPProofGeneratorTest {

    private val keyProvider = SoftwareDPoPKeyProvider()
    private val generator = DPoPProofGenerator(keyProvider) { 1_700_000_000L }
    private val tokenUrl = "https://auth.example.com/oauth/token?grant_type=refresh_token#fragment".toHttpUrl()

    @Test
    fun `header carries the dpop type, ES256 and the public JWK`() {
        val proof = generator.createProof("POST", tokenUrl)

        val header = DPoPTestSupport.decodeSegment(proof, 0)
        assertEquals("dpop+jwt", header.get("typ").asString)
        assertEquals("ES256", header.get("alg").asString)
        val jwk = header.getAsJsonObject("jwk")
        assertEquals("EC", jwk.get("kty").asString)
        assertEquals("P-256", jwk.get("crv").asString)
        assertEquals(32, DPoPTestSupport.base64UrlDecode(jwk.get("x").asString).size)
        assertEquals(32, DPoPTestSupport.base64UrlDecode(jwk.get("y").asString).size)
        assertFalse(jwk.has("d"))
    }

    @Test
    fun `claims bind the method and the url without query or fragment`() {
        val proof = generator.createProof("POST", tokenUrl)

        val claims = DPoPTestSupport.decodeSegment(proof, 1)
        assertEquals("POST", claims.get("htm").asString)
        assertEquals("https://auth.example.com/oauth/token", claims.get("htu").asString)
        assertEquals(1_700_000_000L, claims.get("iat").asLong)
        assertTrue(claims.get("jti").asString.isNotBlank())
        assertFalse(claims.has("ath"))
        assertFalse(claims.has("nonce"))
    }

    @Test
    fun `every proof gets a new jti`() {
        val first = DPoPTestSupport.decodeSegment(generator.createProof("POST", tokenUrl), 1)
        val second = DPoPTestSupport.decodeSegment(generator.createProof("POST", tokenUrl), 1)

        assertNotEquals(first.get("jti").asString, second.get("jti").asString)
    }

    @Test
    fun `access token hash and nonce are included when given`() {
        val proof = generator.createProof("GET", "https://auth.example.com/me".toHttpUrl(), "token-value", "nonce-1")

        val claims = DPoPTestSupport.decodeSegment(proof, 1)
        assertEquals(DPoPEncoding.sha256Base64Url("token-value"), claims.get("ath").asString)
        assertEquals("nonce-1", claims.get("nonce").asString)
    }

    @Test
    fun `signature verifies with the device public key`() {
        val proof = generator.createProof("POST", tokenUrl)
        val segments = proof.split('.')
        val joseSignature = DPoPTestSupport.base64UrlDecode(segments[2])

        assertEquals(64, joseSignature.size)
        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(keyProvider.keyPair!!.public)
            update("${segments[0]}.${segments[1]}".toByteArray(Charsets.US_ASCII))
        }
        assertTrue(verifier.verify(DPoPTestSupport.joseToDer(joseSignature)))
    }

    @Test
    fun `jwk thumbprint matches the RFC 9449 example`() {
        val publicKey = publicKey(
            x = "l8tFrhx-34tV3hRICRDY9zCkDlpBhF42UQUfWVAWBFs",
            y = "9VE4jf_Ok_o64zbTTlcuNJajHmt6v9TDVrU0CdvGRDA",
        )

        assertEquals("0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I", DPoPProofGenerator.jwkThumbprint(publicKey))
    }

    @Test
    fun `rotating deletes the key and the next thumbprint comes from a new key`() {
        val before = generator.thumbprint()

        generator.rotateKey()
        val after = generator.thumbprint()

        assertEquals(1, keyProvider.deleteCount)
        assertNotEquals(before, after)
    }

    private fun publicKey(x: String, y: String): ECPublicKey {
        val curve = AlgorithmParameters.getInstance("EC")
            .apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(
            BigInteger(1, DPoPTestSupport.base64UrlDecode(x)),
            BigInteger(1, DPoPTestSupport.base64UrlDecode(y)),
        )
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, curve)) as ECPublicKey
    }
}
