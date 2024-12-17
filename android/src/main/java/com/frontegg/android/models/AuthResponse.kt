package com.frontegg.android.models

class AuthResponse {
    lateinit var token_type: String
    lateinit var refresh_token: String
    lateinit var access_token: String
    lateinit var id_token: String
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuthResponse

        if (token_type != other.token_type) return false
        if (refresh_token != other.refresh_token) return false
        if (access_token != other.access_token) return false
        if (id_token != other.id_token) return false

        return true
    }

    override fun hashCode(): Int {
        var result = token_type.hashCode()
        result = 31 * result + refresh_token.hashCode()
        result = 31 * result + access_token.hashCode()
        result = 31 * result + id_token.hashCode()
        return result
    }
}