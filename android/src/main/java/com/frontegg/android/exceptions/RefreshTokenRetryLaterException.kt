package com.frontegg.android.exceptions

import java.io.IOException

/**
 * The token endpoint answered 408 or 429: the refresh token is still valid, but the refresh has to wait.
 */
class RefreshTokenRetryLaterException(
    val statusCode: Int,
    val retryAfterMillis: Long?,
    message: String
) : IOException(message)
