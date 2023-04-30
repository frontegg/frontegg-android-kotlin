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