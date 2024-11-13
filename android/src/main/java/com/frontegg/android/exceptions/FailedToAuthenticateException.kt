package com.frontegg.android.exceptions

import okhttp3.Headers

/**
 * Exception that represents authentication failure when refreshing token or exchange oauth code
 */
public class FailedToAuthenticateException(val headers: Headers? = null, error:String) :
    FronteggException("$FAILED_TO_AUTHENTICATE: $error" )
