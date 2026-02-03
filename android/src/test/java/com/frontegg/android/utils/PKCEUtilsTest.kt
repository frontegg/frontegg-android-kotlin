package com.frontegg.android.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.frontegg.android.services.CredentialManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test

class PKCEUtilsTest {

    private val mockContext = mockk<Context>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        // Mock Base64 for Android
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `generateCodeVerifier returns non-empty string`() {
        val verifier = PKCEUtils.generateCodeVerifier()
        
        assert(verifier.isNotEmpty())
    }

    @Test
    fun `generateCodeVerifier returns different values each time`() {
        val verifier1 = PKCEUtils.generateCodeVerifier()
        val verifier2 = PKCEUtils.generateCodeVerifier()
        
        assert(verifier1 != verifier2)
    }

    @Test
    fun `generateCodeVerifier returns base64url safe string`() {
        val verifier = PKCEUtils.generateCodeVerifier()
        
        // Should not contain + or / (standard base64 chars)
        // Base64URL uses - and _ instead
        assert(!verifier.contains("+"))
        assert(!verifier.contains("/"))
    }

    @Test
    fun `generateCodeChallenge returns non-empty string for valid input`() {
        val verifier = "test-code-verifier"
        val challenge = PKCEUtils.generateCodeChallenge(verifier)
        
        assert(challenge.isNotEmpty())
    }

    @Test
    fun `generateCodeChallenge returns consistent result for same input`() {
        val verifier = "consistent-test-verifier"
        
        val challenge1 = PKCEUtils.generateCodeChallenge(verifier)
        val challenge2 = PKCEUtils.generateCodeChallenge(verifier)
        
        assert(challenge1 == challenge2)
    }

    @Test
    fun `generateCodeChallenge returns different results for different inputs`() {
        val challenge1 = PKCEUtils.generateCodeChallenge("verifier-1")
        val challenge2 = PKCEUtils.generateCodeChallenge("verifier-2")
        
        assert(challenge1 != challenge2)
    }

    @Test
    fun `generateCodeChallenge handles empty string`() {
        val challenge = PKCEUtils.generateCodeChallenge("")
        
        // Should still produce a hash (of empty string)
        assert(challenge.isNotEmpty())
    }

    @Test
    fun `generateCodeChallenge handles special characters`() {
        val verifier = "test!@#\$%^&*()_+-=[]{}|;':\",./<>?"
        val challenge = PKCEUtils.generateCodeChallenge(verifier)
        
        assert(challenge.isNotEmpty())
    }

    @Test
    fun `generateCodeChallenge handles unicode`() {
        val verifier = "test-verifier-日本語-中文-한국어"
        val challenge = PKCEUtils.generateCodeChallenge(verifier)
        
        assert(challenge.isNotEmpty())
    }

    @Test
    fun `generateCodeChallenge handles long input`() {
        val verifier = "a".repeat(1000)
        val challenge = PKCEUtils.generateCodeChallenge(verifier)
        
        assert(challenge.isNotEmpty())
        // SHA-256 always produces 32 bytes = ~43 base64 chars
        assert(challenge.length <= 50)
    }

    @Test
    fun `getCodeVerifierFromWebview returns a verifier`() {
        mockkConstructor(CredentialManager::class)
        every { anyConstructed<CredentialManager>().get(CredentialKeys.CODE_VERIFIER) } returns "stored-verifier"
        every { anyConstructed<CredentialManager>().save(any(), any()) } returns true
        
        // The method may return stored or generate new depending on implementation
        val verifier = PKCEUtils.getCodeVerifierFromWebview(mockContext)
        
        assert(verifier.isNotEmpty())
    }

    @Test
    fun `getCodeVerifierFromWebview generates new verifier when none stored`() {
        mockkConstructor(CredentialManager::class)
        every { anyConstructed<CredentialManager>().get(CredentialKeys.CODE_VERIFIER) } returns null
        every { anyConstructed<CredentialManager>().save(any(), any()) } returns true
        
        val verifier = PKCEUtils.getCodeVerifierFromWebview(mockContext)
        
        assert(verifier.isNotEmpty())
    }

    @Test
    fun `getCodeVerifierFromWebview generates new verifier when stored is empty`() {
        mockkConstructor(CredentialManager::class)
        every { anyConstructed<CredentialManager>().get(CredentialKeys.CODE_VERIFIER) } returns ""
        every { anyConstructed<CredentialManager>().save(any(), any()) } returns true
        
        val verifier = PKCEUtils.getCodeVerifierFromWebview(mockContext)
        
        assert(verifier.isNotEmpty())
        assert(verifier != "")
    }

    @Test
    fun `getCodeVerifierFromWebview handles exception gracefully`() {
        mockkConstructor(CredentialManager::class)
        every { anyConstructed<CredentialManager>().get(CredentialKeys.CODE_VERIFIER) } throws RuntimeException("Test exception")
        
        val verifier = PKCEUtils.getCodeVerifierFromWebview(mockContext)
        
        // Should return a newly generated verifier
        assert(verifier.isNotEmpty())
    }

    @Test
    fun `PKCE flow produces valid challenge from verifier`() {
        val verifier = PKCEUtils.generateCodeVerifier()
        val challenge = PKCEUtils.generateCodeChallenge(verifier)
        
        // Both should be non-empty base64url strings
        assert(verifier.isNotEmpty())
        assert(challenge.isNotEmpty())
        
        // Challenge should be different from verifier
        assert(verifier != challenge)
    }

    @Test
    fun `generateCodeChallenge produces correct SHA256 hash`() {
        // Test with known input/output
        // SHA256("test") = 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
        // Base64URL of that = n4bQgYhMfWWaL-qgxVrQFaO_TxsrC4Is0V1sFbDwCgg
        val input = "test"
        val expectedChallenge = "n4bQgYhMfWWaL-qgxVrQFaO_TxsrC4Is0V1sFbDwCgg"
        
        val challenge = PKCEUtils.generateCodeChallenge(input)
        
        assert(challenge == expectedChallenge)
    }
}
