package com.frontegg.android.models

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialLoginPostLoginRequestTest {

    private val gson = Gson()

    @Test
    fun `SocialLoginPostLoginRequest deserializes from JSON`() {
        val json = """
            {
                "code": "auth_code_123",
                "idToken": "id_token_xyz",
                "state": "state_value",
                "redirectUri": "https://app.example.com/callback"
            }
        """.trimIndent()
        val request = gson.fromJson(json, SocialLoginPostLoginRequest::class.java)
        assertEquals("auth_code_123", request.code)
        assertEquals("id_token_xyz", request.idToken)
        assertEquals("state_value", request.state)
        assertEquals("https://app.example.com/callback", request.redirectUri)
    }

    @Test
    fun `SocialLoginPostLoginRequest has default null values`() {
        val request = SocialLoginPostLoginRequest()
        assertNull(request.code)
        assertNull(request.idToken)
        assertNull(request.state)
        assertNull(request.redirectUri)
    }

    @Test
    fun `SocialLoginPostLoginRequest deserializes empty JSON`() {
        val request = gson.fromJson("{}", SocialLoginPostLoginRequest::class.java)
        assertNull(request.code)
        assertNull(request.idToken)
        assertNull(request.state)
        assertNull(request.redirectUri)
    }

    @Test
    fun `SocialLoginPostLoginRequest equals and copy`() {
        val request = SocialLoginPostLoginRequest(
            code = "code1",
            idToken = "token1",
            state = "state1",
            redirectUri = "uri1"
        )
        val copy = request.copy(code = "code2")
        assertEquals("code2", copy.code)
        assertEquals("token1", copy.idToken)
    }
}
