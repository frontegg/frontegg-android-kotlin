package com.frontegg.android.exceptions

/**
 * Exception that represents cookie not found when trying to header value from http response
 */
public class CookieNotFoundException(prefix: String) :
    FronteggException(COOKIE_NOT_FOUND_ERROR + prefix)
