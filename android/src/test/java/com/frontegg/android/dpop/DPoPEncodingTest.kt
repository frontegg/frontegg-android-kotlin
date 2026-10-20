package com.frontegg.android.dpop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class DPoPEncodingTest {

    private fun derSignature(r: ByteArray, s: ByteArray, longForm: Boolean = false): ByteArray {
        val body = byteArrayOf(0x02, r.size.toByte()) + r + byteArrayOf(0x02, s.size.toByte()) + s
        val length = if (longForm) byteArrayOf(0x81.toByte(), body.size.toByte()) else byteArrayOf(body.size.toByte())
        return byteArrayOf(0x30) + length + body
    }

    @Test
    fun `full-length integers are copied as they are`() {
        val r = ByteArray(32) { 0x11 }
        val s = ByteArray(32) { 0x22 }

        val jose = DPoPEncoding.derSignatureToJose(derSignature(r, s))

        assertArrayEquals(r + s, jose)
    }

    @Test
    fun `sign padding byte is stripped from a high-bit integer`() {
        val r = ByteArray(32) { 0x80.toByte() }
        val s = ByteArray(32) { 0x7F }

        val jose = DPoPEncoding.derSignatureToJose(derSignature(byteArrayOf(0) + r, s))

        assertEquals(64, jose.size)
        assertArrayEquals(r + s, jose)
    }

    @Test
    fun `short integers are left-padded to 32 bytes`() {
        val r = ByteArray(30) { 0x01 }
        val s = byteArrayOf(0x05)

        val jose = DPoPEncoding.derSignatureToJose(derSignature(r, s))

        assertArrayEquals(ByteArray(2) + r, jose.copyOfRange(0, 32))
        assertArrayEquals(ByteArray(31) + s, jose.copyOfRange(32, 64))
    }

    @Test
    fun `long-form sequence length is accepted`() {
        val r = ByteArray(32) { 0x33 }
        val s = ByteArray(32) { 0x44 }

        val jose = DPoPEncoding.derSignatureToJose(derSignature(r, s, longForm = true))

        assertArrayEquals(r + s, jose)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-sequence input is rejected`() {
        DPoPEncoding.derSignatureToJose(byteArrayOf(0x02, 0x01, 0x01))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated integer is rejected`() {
        DPoPEncoding.derSignatureToJose(byteArrayOf(0x30, 0x06, 0x02, 0x20, 0x01))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `integer longer than the coordinate is rejected`() {
        DPoPEncoding.derSignatureToJose(derSignature(ByteArray(33) { 0x01 }, byteArrayOf(0x01)))
    }

    @Test
    fun `coordinates are fixed to 32 unsigned bytes`() {
        val small = DPoPEncoding.unsignedFixedLength(BigInteger.ONE)
        val highBit = DPoPEncoding.unsignedFixedLength(BigInteger(1, ByteArray(32) { 0xFF.toByte() }))

        assertEquals(32, small.size)
        assertEquals(1.toByte(), small[31])
        assertArrayEquals(ByteArray(32) { 0xFF.toByte() }, highBit)
    }

    @Test
    fun `sha256 matches the RFC 9449 ath example`() {
        val accessToken = "Kz~8mXK1EalYznwH-LC-1fBAo.4Ljp~zsPE_NeO.gxU"

        assertEquals("fUHyO2r2Z3DZ53EsNrWBb0xWXoaNy59IiKCAqksmQEo", DPoPEncoding.sha256Base64Url(accessToken))
    }
}
