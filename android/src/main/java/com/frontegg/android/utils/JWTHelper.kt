package com.frontegg.android.utils

import com.google.gson.Gson
import java.util.Base64

class JWT {
    lateinit var sub: String
    lateinit var name: String
    lateinit var email: String
    var email_verified: Boolean = false
    lateinit var type: String
    lateinit var aud: String
    lateinit var iss: String
    var iat: Long = 0
    var exp: Long = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JWT

        if (sub != other.sub) return false
        if (name != other.name) return false
        if (email != other.email) return false
        if (email_verified != other.email_verified) return false
        if (type != other.type) return false
        if (aud != other.aud) return false
        if (iss != other.iss) return false
        if (iat != other.iat) return false
        if (exp != other.exp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sub.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + email_verified.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + aud.hashCode()
        result = 31 * result + iss.hashCode()
        result = 31 * result + iat.hashCode()
        result = 31 * result + exp.hashCode()
        return result
    }
}

class JWTHelper {

    companion object {
        fun decode(token: String): JWT {
            val chunks: List<String> = token.split(Regex("\\."), 0)
            val decoder: Base64.Decoder = Base64.getUrlDecoder()
            val payload = String(decoder.decode(chunks[1]))
            return Gson().fromJson(payload, JWT::class.java)!!
        }
    }
}