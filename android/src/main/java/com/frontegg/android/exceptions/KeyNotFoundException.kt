package com.frontegg.android.exceptions

/**
 * Exception that represents key not found when trying to get value from Shared Preference
 */
public class KeyNotFoundException(cause: Throwable) :
    FronteggException(KEY_NOT_FOUND_SHARED_PREFERENCES_ERROR, cause)
