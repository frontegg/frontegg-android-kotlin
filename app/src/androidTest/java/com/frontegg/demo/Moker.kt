package com.frontegg.demo

import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner

enum class MockMethod(val rawValue: String) {
    mockEmbeddedRefreshToken("mockEmbeddedRefreshToken"),
    mockSSOPrelogin("mockSSOPrelogin"),
    mockSSOAuthSamlCallback("mockSSOAuthSamlCallback"),
    mockSSOAuthOIDCCallback("mockSSOAuthOIDCCallback"),
    mockHostedLoginAuthorize("mockHostedLoginAuthorize"),
    mockHostedLoginRefreshToken("mockHostedLoginRefreshToken"),
    mockLogout("mockLogout"),
    mockGetMe("mockGetMe"),
    mockGetMeTenants("mockGetMeTenants"),
    mockAuthUser("mockAuthUser"),
    mockSessionsConfigurations("mockSessionsConfigurations"),
    mockOauthPostlogin("mockOauthPostlogin"),
    mockVendorConfig("mockVendorConfig"),
    mockPreLoginWithMagicLink("mockPreLoginWithMagicLink"),
    mockPostLoginWithMagicLink("mockPostLoginWithMagicLink")
}

enum class MockDataMethod(val rawValue: String) {
    generateUser("generateUser")
}


object Mocker {

    var baseUrl: String = "http://10.0.2.2:4001"
    var clientId: String = "b6adfe4c-d695-4c04-b95f-3ec9fd0c6cca"

    fun getNgrokUrl(): String {
        val url = URL("${baseUrl}/ngrok")
        val scanner = Scanner(url.openStream())
        scanner.useDelimiter("\\Z")
        return if (scanner.hasNext()) scanner.next() else ""
    }

    private fun mockWithId(name: MockMethod, body: Map<String, Any?>): String {
        val urlStr = "${baseUrl}/mock/${name.rawValue}"
        val url = URL(urlStr)
        val request = url.openConnection() as HttpURLConnection
        request.setRequestProperty("Accept", "application/json")
        request.setRequestProperty("Content-Type", "application/json")
        request.setRequestProperty("Origin", baseUrl)
        request.requestMethod = "POST"

        val json = JSONObject(body).toString()
        request.outputStream.bufferedWriter().use { it.write(json) }

        val responseCode = request.responseCode
        val responseMessage = request.responseMessage

        val response = if (responseCode == 200) {
            request.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            request.errorStream.bufferedReader().use(BufferedReader::readText)
        }

        request.disconnect()
        println("Response code: $responseCode")
        if (responseCode != 200) {
            println("Error body: $response")
        } else {
            println("Response message: $responseMessage")
            println("Response body: $response")
        }

        return response
    }

    fun mock(name: MockMethod, body: Map<String, Any?>) {
        val id = mockWithId(name, body)
        println("Mock(${name.rawValue}) => $id")
    }

    fun mockData(name: MockDataMethod, body: List<Any>): JSONObject {
        val jsonData = JSONArray(body).toString().toByteArray()
        val jsonStr = String(jsonData, Charsets.UTF_8)

        val query = jsonStr.toUri()

        val urlStr = "$baseUrl/faker/${name.rawValue}?options=$query"

        println(urlStr)
        val url = URL(urlStr)

        val request = url.openConnection() as HttpURLConnection
        request.setRequestProperty("Accept", "application/json")
        request.setRequestProperty("Content-Type", "application/json")
        request.setRequestProperty("Origin", "http://10.0.2.2:4001")
        request.requestMethod = "GET"

        val data = request.inputStream.bufferedReader().use { it.readText() }

        return JSONObject(data)

    }

    fun mockClearMocks() {
        val urlStr = "$baseUrl/clear-mock"
        val url = URL(urlStr)

        val request = url.openConnection() as HttpURLConnection
        request.setRequestProperty("Accept", "application/json")
        request.setRequestProperty("Content-Type", "application/json")
        request.setRequestProperty("Origin", "http://10.0.2.2:4001")
        request.requestMethod = "POST"
        request.inputStream.bufferedReader().use(BufferedReader::readText)
    }

    fun mockSuccessPasswordLogin(oauthCode: String): JSONObject {
        val requestBody = mutableListOf(
            clientId,
            mapOf("email" to "test@frontegg.com")
        )
        val mockedUser = mockData(MockDataMethod.generateUser, requestBody).getJSONObject("data")

        val authUserOptions = mapOf(
            "success" to true,
            "user" to mockedUser
        )
        mock(MockMethod.mockAuthUser, mapOf("options" to authUserOptions))
        mock(
            MockMethod.mockHostedLoginRefreshToken, mapOf(
                "partialRequestBody" to emptyMap<String, Any>(),
                "options" to mapOf(
                    "success" to true,
                    "refreshTokenResponse" to mockedUser["refreshTokenResponse"],
                    "refreshTokenCookie" to mockedUser["refreshTokenCookie"]
                )
            )
        )
        mock(
            MockMethod.mockEmbeddedRefreshToken, mapOf(
                "options" to mapOf(
                    "success" to true,
                    "refreshTokenResponse" to mockedUser["refreshTokenResponse"],
                    "refreshTokenCookie" to mockedUser["refreshTokenCookie"]
                )
            )
        )

        mock(MockMethod.mockGetMeTenants, mapOf("options" to mockedUser))
        mock(MockMethod.mockGetMe, mapOf("options" to mockedUser))
        mock(MockMethod.mockSessionsConfigurations, emptyMap())
        mock(
            MockMethod.mockOauthPostlogin,
            mapOf("options" to mapOf("redirectUrl" to "$baseUrl/oauth/mobile/callback?code=$oauthCode"))
        )
        mock(MockMethod.mockLogout, emptyMap())

        return mockedUser
    }
}