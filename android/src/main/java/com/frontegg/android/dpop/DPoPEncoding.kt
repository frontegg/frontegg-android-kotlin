package com.frontegg.android.dpop

import com.frontegg.android.utils.FronteggEndpoints
import java.math.BigInteger
import java.util.Base64

internal object DPoPEncoding {
    const val P256_COORDINATE_LENGTH = 32

    private const val DER_SEQUENCE_TAG: Byte = 0x30
    private const val DER_INTEGER_TAG: Byte = 0x02
    private const val DER_LONG_FORM_ONE_BYTE: Byte = 0x81.toByte()

    fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun sha256Base64Url(value: String): String = FronteggEndpoints.sha256Base64Url(value.toByteArray(Charsets.UTF_8))

    fun unsignedFixedLength(value: BigInteger, length: Int = P256_COORDINATE_LENGTH): ByteArray =
        leftPadUnsigned(value.toByteArray(), length)

    /** Converts a DER `SHA256withECDSA` signature to the fixed-length R || S form JWS ES256 requires. */
    @Throws(IllegalArgumentException::class)
    fun derSignatureToJose(derSignature: ByteArray, coordinateLength: Int = P256_COORDINATE_LENGTH): ByteArray {
        require(derSignature.size > 2 && derSignature[0] == DER_SEQUENCE_TAG) { "Not a DER ECDSA signature" }
        val integersOffset = if (derSignature[1] == DER_LONG_FORM_ONE_BYTE) 3 else 2
        val r = readDerInteger(derSignature, integersOffset)
        val s = readDerInteger(derSignature, integersOffset + 2 + r.size)
        return leftPadUnsigned(r, coordinateLength) + leftPadUnsigned(s, coordinateLength)
    }

    private fun readDerInteger(der: ByteArray, offset: Int): ByteArray {
        require(offset + 2 <= der.size && der[offset] == DER_INTEGER_TAG) { "Malformed DER integer" }
        val length = der[offset + 1].toInt() and 0xFF
        require(offset + 2 + length <= der.size) { "Truncated DER integer" }
        return der.copyOfRange(offset + 2, offset + 2 + length)
    }

    private fun leftPadUnsigned(bytes: ByteArray, length: Int): ByteArray {
        val significant = bytes.dropWhile { it == 0.toByte() }.toByteArray()
        require(significant.size <= length) { "Integer longer than $length bytes" }
        return ByteArray(length - significant.size) + significant
    }
}
