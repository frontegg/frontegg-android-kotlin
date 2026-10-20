package com.frontegg.android.exceptions

/**
 * Exception that represents a missing or inactive Google social login configuration in Frontegg
 */
class GoogleSignInNotConfiguredException : FronteggException(GOOGLE_SIGN_IN_NOT_CONFIGURED_ERROR)
