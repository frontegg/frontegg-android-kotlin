package com.frontegg.android.exceptions

public open class FronteggException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    public companion object {
        public const val UNKNOWN_ERROR: String = "frontegg.error.unknown"
        public const val FRONTEGG_APP_MUST_BE_INITIALIZED: String =
            "frontegg.error.app_must_be_initialized"
        public const val FRONTEGG_DOMAIN_MUST_NOT_START_WITH_HTTPS: String =
            "frontegg.error.domain_must_not_start_with_https"
        public const val KEY_NOT_FOUND_SHARED_PREFERENCES_ERROR: String =
            "frontegg.error.key_not_found_shared_preferences"
        public const val COOKIE_NOT_FOUND_ERROR: String =
            "frontegg.error.cookie_not_found"

        public const val MFA_REQUIRED_ERROR: String =
            "frontegg.error.mfa_required"

        public const val FAILED_TO_REGISTER_WBEAUTHN_ERROR: String =
            "frontegg.error.failed_to_register_wbeauthn_error"

        public const val NOT_AUTHENTICATED_ERROR: String =
            "frontegg.error.not_authenticated_error"

        public const val FAILED_TO_AUTHENTICATE: String =
            "frontegg.error.failed_to_authenticate"
    }
}