package com.frontegg.android.models

import com.frontegg.android.fixtures.authResponseJson
import com.frontegg.android.fixtures.getAuthResponse
import com.google.gson.Gson
import org.junit.Before
import org.junit.Test

class AuthResponseTest {
    private lateinit var authResponse: AuthResponse

    @Before
    fun setUp() {
        authResponse = getAuthResponse()
    }

    @Test
    fun `should return valid model`() {
        val authResponseModel = Gson().fromJson(authResponseJson, AuthResponse::class.java)

        assert(authResponseModel == authResponse)
    }
}