package com.frontegg.android.models

class WebAuthnAssertionRequest(
    var cookie: String,
    var jsonChallenge: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WebAuthnAssertionRequest

        if (cookie != other.cookie) return false
        if (jsonChallenge != other.jsonChallenge) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cookie.hashCode()
        result = 31 * result + jsonChallenge.hashCode()
        return result
    }
}