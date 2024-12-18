package com.frontegg.android.utils

import com.google.gson.Gson
import org.junit.Before
import org.junit.Test


class JWTHelperTest {
    private val jwtJson =
        "{\"sub\":\"40352a4d-3aa3-4bba-a396-9e20cf65db11\",\"name\":\"Test User\",\"email\":\"test.user@mail.com\",\"email_verified\":true,\"sid\":\"71a09088-4858-4f8e-b8a8-385aa3bfa93c\",\"type\":\"userToken\",\"applicationId\":\"f717b75f-e09a-48cc-a84e-e20a8454a9cf\",\"nonce\":\"EWtW1ooW2cKG9owM\",\"aud\":\"b6adfe4c-d695-4c04-b95f-3ec9fd0c6cca\",\"iss\":\"https://frontegg.test.com\",\"iat\":1734520950,\"exp\":1734521550}"
    private lateinit var jwt: JWT

    private val jwtToken =
        "xxx.eyJzdWIiOiI0MDM1MmE0ZC0zYWEzLTRiYmEtYTM5Ni05ZTIwY2Y2NWRiMTEiLCJuYW1lIjoiVGVzdCBVc2VyIiwiZW1haWwiOiJ0ZXN0LnVzZXJAbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwic2lkIjoiNzFhMDkwODgtNDg1OC00ZjhlLWI4YTgtMzg1YWEzYmZhOTNjIiwidHlwZSI6InVzZXJUb2tlbiIsImFwcGxpY2F0aW9uSWQiOiJmNzE3Yjc1Zi1lMDlhLTQ4Y2MtYTg0ZS1lMjBhODQ1NGE5Y2YiLCJub25jZSI6IkVXdFcxb29XMmNLRzlvd00iLCJhdWQiOiJiNmFkZmU0Yy1kNjk1LTRjMDQtYjk1Zi0zZWM5ZmQwYzZjY2EiLCJpc3MiOiJodHRwczovL2Zyb250ZWdnLnRlc3QuY29tIiwiaWF0IjoxNzM0NTIwOTUwLCJleHAiOjE3MzQ1MjE1NTB9.xxx"

    @Before
    fun setUp() {
        jwt = JWT()
        jwt.sub = "40352a4d-3aa3-4bba-a396-9e20cf65db11"
        jwt.name = "Test User"
        jwt.email = "test.user@mail.com"
        jwt.email_verified = true
        jwt.type = "userToken"
        jwt.aud = "b6adfe4c-d695-4c04-b95f-3ec9fd0c6cca"
        jwt.iss = "https://frontegg.test.com"
        jwt.iat = 1734520950
        jwt.exp = 1734521550
    }

    @Test
    fun `should return valid model`() {
        val jwtModel = Gson().fromJson(jwtJson, JWT::class.java)

        assert(jwtModel == jwt)
    }

    @Test
    fun `decode should return valid model`() {
        val jwtModel = JWTHelper.decode(jwtToken)

        assert(jwtModel == jwt)
    }
}