package com.frontegg.android.exceptions

/**
 * Exception that represents non-authorized / non-authenticated trying to access protected api
 */
public class NotAuthenticatedException() :
    FronteggException(NOT_AUTHENTICATED_ERROR)
