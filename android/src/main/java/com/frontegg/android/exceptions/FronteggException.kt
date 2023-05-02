package com.frontegg.android.exceptions

public open class FronteggException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    public companion object {
        public const val UNKNOWN_ERROR: String = "frontegg.error.unknown"
        public const val FRONTEGG_APP_MUST_BE_INITIALIZED: String = "frontegg.error.app_must_be_initialized"
        public const val FRONTEGG_DOMAIN_MUST_START_WITH_HTTPS: String = "frontegg.error.domain_must_start_with_http"
        public const val KEY_NOT_FOUND_SHARED_PREFERENCES_ERROR: String = "frontegg.error.key_not_found_shared_preferences"
    }
}