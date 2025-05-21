package com.frontegg.android.exceptions

public open class FronteggException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    public companion object {
        const val UNKNOWN_ERROR: String = "frontegg.error.unknown"
        const val FRONTEGG_APP_MUST_BE_INITIALIZED: String =
            "frontegg.error.app_must_be_initialized"
        const val FRONTEGG_DOMAIN_MUST_NOT_START_WITH_HTTPS: String =
            "frontegg.error.domain_must_not_start_with_https"
        const val KEY_NOT_FOUND_SHARED_PREFERENCES_ERROR: String =
            "frontegg.error.key_not_found_shared_preferences"
        const val COOKIE_NOT_FOUND_ERROR: String =
            "frontegg.error.cookie_not_found"

        const val MFA_REQUIRED_ERROR: String =
            "frontegg.error.mfa_required"

        const val FAILED_TO_REGISTER_WBEAUTHN_ERROR: String =
            "frontegg.error.failed_to_register_wbeauthn_error"

        const val NOT_AUTHENTICATED_ERROR: String =
            "frontegg.error.not_authenticated_error"

        const val FAILED_TO_AUTHENTICATE: String =
            "frontegg.error.failed_to_authenticate"

        const val FAILED_TO_AUTHENTICATE_PASSKEYS: String =
            "frontegg.error.failed_to_authenticate_passkeys"

        const val CANCELED_BY_USER_ERROR: String =
            "frontegg.error.canceled_by_user"

        const val MFA_NOT_ENROLLED_ERROR: String =
            "frontegg.error.mfa_not_enrolled"
    }
}