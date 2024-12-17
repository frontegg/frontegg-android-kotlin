package com.frontegg.android.models

import com.google.gson.Gson
import org.junit.Before
import org.junit.Test

class AuthResponseTest {
    private val tenantJson =
        "{\"id_token\":\"Test Id Token\",\"refresh_token\":\"Test Refresh Token\",\"access_token\":\"Test Access Token\",\"token_type\":\"Test Token Type\"}"
    private lateinit var authResponse: AuthResponse

    @Before
    fun setUp() {
        authResponse = AuthResponse()
        authResponse.id_token = "Test Id Token"
        authResponse.refresh_token = "Test Refresh Token"
        authResponse.access_token = "Test Access Token"
        authResponse.token_type = "Test Token Type"
    }

    @Test
    fun `should return valid model`() {
        val authResponseModel = Gson().fromJson(tenantJson, AuthResponse::class.java)

        assert(authResponseModel == authResponse)
    }
}