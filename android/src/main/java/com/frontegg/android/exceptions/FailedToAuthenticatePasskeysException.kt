package com.frontegg.android.exceptions

/**
 * Exception that represents authentication failure when refreshing token or exchange oauth code
 */
public class FailedToAuthenticatePasskeysException(error:String) :
    FronteggException("$FAILED_TO_AUTHENTICATE_PASSKEYS: $error" )
