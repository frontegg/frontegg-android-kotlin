package com.frontegg.android.exceptions

public open class FronteggException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    public companion object {
        public const val UNKNOWN_ERROR: String = "frontegg.error.unknown"
        public const val KEY_NOT_FOUND_SHARED_PREFERENCES_ERROR: String = "frontegg.error.key_not_found_shared_preferences"
    }
}