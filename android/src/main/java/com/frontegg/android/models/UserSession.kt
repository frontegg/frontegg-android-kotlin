package com.frontegg.android.models

/**
 * An active session of the signed-in user.
 *
 * @property id the session identifier, used to revoke it;
 * @property userAgent the user agent that opened the session, when known;
 * @property ipAddress the IP address the session was opened from, when known;
 * @property createdAt when the session was opened, as an ISO-8601 string, when known;
 * @property current true for the session this device is using.
 */
data class UserSession(
    val id: String,
    val userAgent: String? = null,
    val ipAddress: String? = null,
    val createdAt: String? = null,
    val current: Boolean = false,
)
