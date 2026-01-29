package com.frontegg.android.exceptions

import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubclassExceptionsTest {

    @Test
    fun `NotAuthenticatedException has correct message`() {
        val e = NotAuthenticatedException()
        assertEquals(FronteggException.NOT_AUTHENTICATED_ERROR, e.message)
        assertTrue(e is FronteggException)
    }

    @Test
    fun `CanceledByUserException has correct message`() {
        val e = CanceledByUserException()
        assertEquals(FronteggException.CANCELED_BY_USER_ERROR, e.message)
        assertTrue(e is FronteggException)
    }

    @Test
    fun `CookieNotFoundException has correct message`() {
        val e = CookieNotFoundException(": suffix")
        assertEquals(FronteggException.COOKIE_NOT_FOUND_ERROR + ": suffix", e.message)
    }

    @Test
    fun `KeyNotFoundException has correct message and cause`() {
        val cause = RuntimeException("root")
        val e = KeyNotFoundException(cause)
        assertEquals(FronteggException.KEY_NOT_FOUND_SHARED_PREFERENCES_ERROR, e.message)
        assertEquals(cause, e.cause)
    }

    @Test
    fun `MfaRequiredException has correct message`() {
        val e = MfaRequiredException("mfa-data")
        assertEquals(FronteggException.MFA_REQUIRED_ERROR, e.message)
        assertEquals("mfa-data", e.mfaRequestData)
    }

    @Test
    fun `MFANotEnrolledException has correct message`() {
        val e = MFANotEnrolledException()
        assertEquals(FronteggException.MFA_NOT_ENROLLED_ERROR, e.message)
    }

    @Test
    fun `FailedToAuthenticateException has correct message`() {
        val e = FailedToAuthenticateException(error = "network error")
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(FronteggException.FAILED_TO_AUTHENTICATE))
        assertTrue(e.message!!.contains("network error"))
    }

    @Test
    fun `FailedToAuthenticatePasskeysException has correct message`() {
        val e = FailedToAuthenticatePasskeysException(error = "passkey error")
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(FronteggException.FAILED_TO_AUTHENTICATE_PASSKEYS))
    }

    @Test
    fun `FailedToRegisterWebAuthnDevice has correct message`() {
        val headers = Headers.Builder().build()
        val e = FailedToRegisterWebAuthnDevice(headers, "register failed")
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(FronteggException.FAILED_TO_REGISTER_WBEAUTHN_ERROR))
        assertEquals(headers, e.headers)
    }

    @Test
    fun `WebAuthnAlreadyRegisteredInLocalDeviceException has correct message`() {
        val e = WebAuthnAlreadyRegisteredInLocalDeviceException()
        assertTrue(e.message!!.contains("Passkeys"))
        assertTrue(e is FronteggException)
    }
}
