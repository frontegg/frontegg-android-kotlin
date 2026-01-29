package com.frontegg.android.exceptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FronteggExceptionTest {

    @Test
    fun `FronteggException constructor with message`() {
        val msg = "test error"
        val e = FronteggException(msg)
        assertEquals(msg, e.message)
        assertNull(e.cause)
    }

    @Test
    fun `FronteggException constructor with message and cause`() {
        val msg = "wrapped"
        val cause = RuntimeException("root")
        val e = FronteggException(msg, cause)
        assertEquals(msg, e.message)
        assertEquals(cause, e.cause)
    }

    @Test
    fun `companion constants are defined`() {
        assertEquals("frontegg.error.unknown", FronteggException.UNKNOWN_ERROR)
        assertEquals("frontegg.error.app_must_be_initialized", FronteggException.FRONTEGG_APP_MUST_BE_INITIALIZED)
        assertEquals("frontegg.error.domain_must_not_start_with_https", FronteggException.FRONTEGG_DOMAIN_MUST_NOT_START_WITH_HTTPS)
        assertEquals("frontegg.error.key_not_found_shared_preferences", FronteggException.KEY_NOT_FOUND_SHARED_PREFERENCES_ERROR)
        assertEquals("frontegg.error.cookie_not_found", FronteggException.COOKIE_NOT_FOUND_ERROR)
        assertEquals("frontegg.error.mfa_required", FronteggException.MFA_REQUIRED_ERROR)
        assertEquals("frontegg.error.failed_to_register_wbeauthn_error", FronteggException.FAILED_TO_REGISTER_WBEAUTHN_ERROR)
        assertEquals("frontegg.error.not_authenticated_error", FronteggException.NOT_AUTHENTICATED_ERROR)
        assertEquals("frontegg.error.failed_to_authenticate", FronteggException.FAILED_TO_AUTHENTICATE)
        assertEquals("frontegg.error.failed_to_authenticate_passkeys", FronteggException.FAILED_TO_AUTHENTICATE_PASSKEYS)
        assertEquals("frontegg.error.canceled_by_user", FronteggException.CANCELED_BY_USER_ERROR)
        assertEquals("frontegg.error.mfa_not_enrolled", FronteggException.MFA_NOT_ENROLLED_ERROR)
    }
}
