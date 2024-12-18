package com.frontegg.android.models

import com.google.gson.Gson
import org.junit.Before
import org.junit.Test

class WebAuthnRegistrationRequestTest {
    private val WebAuthnRegistrationRequestJson = "{\"cookie\": \"Test Cookie\",\"jsonChallenge\": \"{}\"}"
    private lateinit var webAuthnRegistrationRequest: WebAuthnRegistrationRequest

    @Before
    fun setUp() {
        webAuthnRegistrationRequest = WebAuthnRegistrationRequest(
            cookie = "Test Cookie",
            jsonChallenge = "{}"
        )
    }

    @Test
    fun `should return valid model`() {
        val webAuthnRegistrationRequestModel = Gson().fromJson(WebAuthnRegistrationRequestJson, WebAuthnRegistrationRequest::class.java)

        assert(webAuthnRegistrationRequestModel == webAuthnRegistrationRequest)
    }
}