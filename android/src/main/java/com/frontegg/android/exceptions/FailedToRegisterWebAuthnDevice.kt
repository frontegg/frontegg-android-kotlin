package com.frontegg.android.exceptions

import okhttp3.Headers

/**
 * Exception that represents cookie not found when trying to header value from http response
 */
public class FailedToRegisterWebAuthnDevice(val headers: Headers, error: String) :
    FronteggException("$FAILED_TO_REGISTER_WBEAUTHN_ERROR: $error")
