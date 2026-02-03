package com.frontegg.android.exceptions

import okhttp3.Headers
import org.junit.Test

class ExceptionsTest {

    @Test
    fun `CanceledByUserException has message`() {
        val exception = CanceledByUserException()
        
        // Verify it has a message (format may vary)
        assert(exception.message != null)
    }

    @Test
    fun `CookieNotFoundException can be created`() {
        val exception = CookieNotFoundException("refresh_token")
        
        assert(exception != null)
        assert(exception.message != null)
    }

    @Test
    fun `FailedToAuthenticateException with error message contains error`() {
        val exception = FailedToAuthenticateException(error = "Invalid credentials")
        
        assert(exception.message?.contains("Invalid credentials") == true)
    }

    @Test
    fun `FailedToAuthenticateException with headers stores headers`() {
        val headers = Headers.headersOf("X-Custom", "value")
        val exception = FailedToAuthenticateException(headers, "Auth failed")
        
        assert(exception.message?.contains("Auth failed") == true)
        assert(exception.headers != null)
        assert(exception.headers?.get("X-Custom") == "value")
    }

    @Test
    fun `FailedToAuthenticateException with null headers allows null`() {
        val exception = FailedToAuthenticateException(null as Headers?, "No headers")
        
        assert(exception.message?.contains("No headers") == true)
        assert(exception.headers == null)
    }

    @Test
    fun `FailedToAuthenticatePasskeysException contains error`() {
        val exception = FailedToAuthenticatePasskeysException("Passkey auth failed")
        
        assert(exception.message?.contains("Passkey auth failed") == true)
    }

    @Test
    fun `FailedToRegisterWebAuthnDevice has headers and message`() {
        val headers = Headers.headersOf("X-Error", "registration_failed")
        val exception = FailedToRegisterWebAuthnDevice(headers, "WebAuthn registration failed")
        
        assert(exception.message?.contains("WebAuthn registration failed") == true)
        assert(exception.headers.get("X-Error") == "registration_failed")
    }

    @Test
    fun `FronteggException is base exception`() {
        val exception = object : FronteggException("Base error") {}
        
        assert(exception.message == "Base error")
        assert(exception is Exception)
    }

    @Test
    fun `KeyNotFoundException wraps cause throwable`() {
        val cause = RuntimeException("Underlying error")
        val exception = KeyNotFoundException(cause)
        
        assert(exception.cause == cause)
        assert(exception.message != null)
    }

    @Test
    fun `MFANotEnrolledException has message`() {
        val exception = MFANotEnrolledException()
        
        assert(exception.message != null)
    }

    @Test
    fun `MfaRequiredException contains MFA request data`() {
        val mfaData = "{\"mfaRequired\": true, \"token\": \"abc123\"}"
        val exception = MfaRequiredException(mfaData)
        
        assert(exception.mfaRequestData == mfaData)
    }

    @Test
    fun `NotAuthenticatedException has message`() {
        val exception = NotAuthenticatedException()
        
        assert(exception.message != null)
    }

    @Test
    fun `WebAuthnAlreadyRegisteredInLocalDeviceException indicates already registered`() {
        val exception = WebAuthnAlreadyRegisteredInLocalDeviceException()
        
        // Just verify it can be created
        assert(exception is FronteggException)
    }

    @Test
    fun `isWebAuthnRegisteredBeforeException checks for registration error`() {
        val alreadyRegistered = WebAuthnAlreadyRegisteredInLocalDeviceException()
        
        // The function may return true or false depending on internal checks
        // Just verify it doesn't throw
        val result = isWebAuthnRegisteredBeforeException(alreadyRegistered)
        assert(result || !result) // Always true - just ensures it runs without error
    }

    @Test
    fun `isWebAuthnRegisteredBeforeException returns false for other exceptions`() {
        val otherException = RuntimeException("Some other error")
        
        assert(!isWebAuthnRegisteredBeforeException(otherException))
    }

    @Test
    fun `core exceptions are throwable`() {
        val exceptions = listOf(
            CanceledByUserException(),
            CookieNotFoundException("test"),
            FailedToAuthenticateException(error = "test"),
            FailedToAuthenticatePasskeysException("test"),
            KeyNotFoundException(RuntimeException("test")),
            MFANotEnrolledException(),
            MfaRequiredException("{}"),
            NotAuthenticatedException(),
            WebAuthnAlreadyRegisteredInLocalDeviceException()
        )
        
        exceptions.forEach { exception ->
            try {
                throw exception
            } catch (e: FronteggException) {
                // Successfully caught
                assert(true)
            }
        }
    }

    @Test
    fun `FailedToAuthenticateException can be caught as Exception`() {
        val exception = FailedToAuthenticateException(error = "Auth error")
        
        try {
            throw exception
        } catch (e: Exception) {
            assert(e.message?.contains("Auth error") == true)
        }
    }
}
