package com.frontegg.android.models

import com.google.gson.Gson
import org.junit.Before
import org.junit.Test

class WebAutnAssertionRequestTest {
    private val webAuthnAssertionRequestJson = "{\"cookie\": \"Test Cookie\",\"jsonChallenge\": \"{}\"}"
    private lateinit var webAuthnAssertionRequest: WebAuthnAssertionRequest

    @Before
    fun setUp() {
        webAuthnAssertionRequest = WebAuthnAssertionRequest(
            cookie = "Test Cookie",
            jsonChallenge = "{}"
        )
    }

    @Test
    fun `should return valid model`() {
        val webAuthnAssertionRequestModel = Gson().fromJson(webAuthnAssertionRequestJson, WebAuthnAssertionRequest::class.java)

        assert(webAuthnAssertionRequestModel == webAuthnAssertionRequest)
    }
}