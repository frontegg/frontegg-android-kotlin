package com.frontegg.android.exceptions

/**
 * Exception that represents mfa requires when trying to authenticate via Passkeys
 */
public class MfaRequiredException(val mfaRequestData: String) :
    FronteggException(MFA_REQUIRED_ERROR)


