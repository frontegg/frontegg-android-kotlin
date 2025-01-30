package com.frontegg.android.exceptions

import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialException

/**
 * Exception that represents non-authorized / non-authenticated trying to access protected api
 */
public class WebAuthnAlreadyRegisteredInLocalDeviceException() :
    FronteggException("Passkeys already register for this user")


public fun isWebAuthnRegisteredBeforeException(e: Exception): Boolean {
    return e is CreatePublicKeyCredentialException && e.type == "androidx.credentials.TYPE_CREATE_PUBLIC_KEY_CREDENTIAL_DOM_EXCEPTION/androidx.credentials.TYPE_INVALID_STATE_ERROR"
}