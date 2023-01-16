package com.frontegg.android.utils

import java.util.*

class JWTHelper {


    companion object {
        fun decode(token: String): String {


            val chunks: List<String> = token.split(Regex("\\."), 0)


            val decoder: Base64.Decoder = Base64.getUrlDecoder()

            val header: String = String(decoder.decode(chunks[0]))
            val payload: String = String(decoder.decode(chunks[1]))


            return payload
        }
    }
}