package com.frontegg.android.models

import com.frontegg.android.fixtures.getWebAuthnAssertionRequest
import com.frontegg.android.fixtures.webAuthnAssertionRequestJson
import com.google.gson.Gson
import org.junit.Before
import org.junit.Test

class WebAutnAssertionRequestTest {
    private lateinit var webAuthnAssertionRequest: WebAuthnAssertionRequest

    @Before
    fun setUp() {
        webAuthnAssertionRequest = getWebAuthnAssertionRequest()
    }

    @Test
    fun `should return valid model`() {
        val webAuthnAssertionRequestModel =
            Gson().fromJson(webAuthnAssertionRequestJson, WebAuthnAssertionRequest::class.java)

        assert(webAuthnAssertionRequestModel == webAuthnAssertionRequest)
    }
}