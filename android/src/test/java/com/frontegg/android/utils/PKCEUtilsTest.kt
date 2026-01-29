package com.frontegg.android.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PKCEUtilsTest {

    @Test
    fun `generateCodeVerifier returns non-empty Base64URL string`() {
        val verifier = PKCEUtils.generateCodeVerifier()
        assertTrue(verifier.isNotEmpty())
        assertTrue(verifier.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun `generateCodeVerifier returns different values on each call`() {
        val verifier1 = PKCEUtils.generateCodeVerifier()
        val verifier2 = PKCEUtils.generateCodeVerifier()
        assertNotEquals(verifier1, verifier2)
    }

    @Test
    fun `generateCodeChallenge returns deterministic result for same verifier`() {
        val verifier = "test_code_verifier_12345"
        val challenge1 = PKCEUtils.generateCodeChallenge(verifier)
        val challenge2 = PKCEUtils.generateCodeChallenge(verifier)
        assertEquals(challenge1, challenge2)
    }

    @Test
    fun `generateCodeChallenge returns non-empty Base64URL string`() {
        val verifier = "test_verifier"
        val challenge = PKCEUtils.generateCodeChallenge(verifier)
        assertTrue(challenge.isNotEmpty())
        assertTrue(challenge.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun `generateCodeChallenge returns different values for different verifiers`() {
        val challenge1 = PKCEUtils.generateCodeChallenge("verifier1")
        val challenge2 = PKCEUtils.generateCodeChallenge("verifier2")
        assertNotEquals(challenge1, challenge2)
    }

    @Test
    fun `generateCodeChallenge produces S256-compatible hash length`() {
        val verifier = "a".repeat(128)
        val challenge = PKCEUtils.generateCodeChallenge(verifier)
        assertEquals(43, challenge.length)
    }
}
