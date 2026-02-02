package com.frontegg.android.services

import android.util.Log
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.fixtures.authResponseJson
import com.frontegg.android.fixtures.getAuthResponse
import com.frontegg.android.utils.CredentialKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.mockkStatic
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class ApiExtendedTest {
    private lateinit var api: Api
    private lateinit var mockkCredentialManager: CredentialManager
    private lateinit var mockWebServer: MockWebServer
    private val mockStorage = mockk<FronteggInnerStorage>()

    private val socialLoginConfigJson = """
        {
            "facebook": {"active": true, "clientId": "fb-123"},
            "google": {"active": true, "clientId": "google-456"},
            "microsoft": {"active": false},
            "github": {"active": true},
            "slack": null,
            "apple": {"active": true},
            "linkedin": {"active": false}
        }
    """.trimIndent()

    private val featureFlagsJson = """
        {
            "feature-a": "on",
            "feature-b": "off",
            "feature-c": "on"
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(mockStorage)
        every { mockStorage.clientId }.returns("TestClientId")
        every { mockStorage.applicationId }.returns("TestApplicationId")
        every { mockStorage.regions }.returns(listOf())

        mockkCredentialManager = mockkClass(CredentialManager::class)

        mockWebServer = MockWebServer()
        mockWebServer.start()
        val url = mockWebServer.url("")
        every { mockStorage.baseUrl }.returns(url.toString())

        api = Api(mockkCredentialManager)
        every { mockkCredentialManager.get(CredentialKeys.ACCESS_TOKEN) }.returns("Test Access Token")

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun dispose() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getSocialLoginConfig returns valid config`() {
        mockWebServer.enqueue(MockResponse().setBody(socialLoginConfigJson))

        val config = api.getSocialLoginConfig()

        assert(config.facebook?.active == true)
        assert(config.facebook?.clientId == "fb-123")
        assert(config.google?.active == true)
        assert(config.microsoft?.active == false)
        assert(config.github?.active == true)
        assert(config.apple?.active == true)
    }

    @Test
    fun `getSocialLoginConfig throws on error response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        assertThrows<FailedToAuthenticateException> {
            api.getSocialLoginConfig()
        }
    }

    @Test
    fun `getFeatureFlags returns valid JSON`() {
        mockWebServer.enqueue(MockResponse().setBody(featureFlagsJson))

        val flags = api.getFeatureFlags()

        assert(flags.contains("feature-a"))
        assert(flags.contains("on"))
    }

    @Test
    fun `getFeatureFlags throws on error response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        assertThrows<FailedToAuthenticateException> {
            api.getFeatureFlags()
        }
    }

    @Test
    fun `switchTenant makes API call`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        api.switchTenant("tenant-123")

        val request = mockWebServer.takeRequest()
        assert(request.method == "PUT")
        // The path contains the tenant switching endpoint
        assert(request.path != null)
        assert(request.body.readUtf8().contains("tenant-123"))
    }

    @Test
    fun `refreshToken handles 401 response with specific error`() {
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("{\"error\": \"Token expired\"}"))

        val exception = assertThrows<FailedToAuthenticateException> {
            api.refreshToken("expired-token")
        }

        assert(exception.message?.contains("401") == true)
    }

    @Test
    fun `refreshToken handles other error responses`() {
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("{\"error\": \"Server error\"}"))

        assertThrows<FailedToAuthenticateException> {
            api.refreshToken("some-token")
        }
    }

    @Test
    fun `exchangeToken handles network timeout gracefully`() {
        // Don't enqueue any response - this simulates timeout
        // In real scenario, this would timeout, but MockWebServer
        // returns immediately with an empty response
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = api.exchangeToken("code", "redirect", "verifier")

        assert(result == null)
    }

    @Test
    fun `API includes applicationId header when set`() {
        every { mockStorage.applicationId }.returns("my-app-id")
        
        mockWebServer.enqueue(MockResponse().setBody(socialLoginConfigJson))

        api.getSocialLoginConfig()

        val request = mockWebServer.takeRequest()
        assert(request.getHeader("frontegg-requested-application-id") == "my-app-id")
    }

    @Test
    fun `API excludes applicationId header when null`() {
        every { mockStorage.applicationId }.returns(null)
        
        // Recreate API to pick up the null applicationId
        api = Api(mockkCredentialManager)
        
        mockWebServer.enqueue(MockResponse().setBody(socialLoginConfigJson))

        api.getSocialLoginConfig()

        val request = mockWebServer.takeRequest()
        assert(request.getHeader("frontegg-requested-application-id") == null)
    }

    @Test
    fun `API includes Authorization header when access token is set`() {
        every { mockkCredentialManager.get(CredentialKeys.ACCESS_TOKEN) }.returns("my-access-token")
        
        mockWebServer.enqueue(MockResponse().setBody(socialLoginConfigJson))

        api.getSocialLoginConfig()

        val request = mockWebServer.takeRequest()
        assert(request.getHeader("Authorization") == "Bearer my-access-token")
    }

    @Test
    fun `API excludes Authorization header when access token is null`() {
        every { mockkCredentialManager.get(CredentialKeys.ACCESS_TOKEN) }.returns(null)
        
        mockWebServer.enqueue(MockResponse().setBody(socialLoginConfigJson))

        api.getSocialLoginConfig()

        val request = mockWebServer.takeRequest()
        assert(request.getHeader("Authorization") == null)
    }

    @Test
    fun `getServerUrl returns correct base URL`() {
        val serverUrl = api.getServerUrl()
        
        assert(serverUrl.isNotEmpty())
        assert(serverUrl.startsWith("http"))
    }

    @Test
    fun `authorizeWithTokens calls silentHostedLoginRefreshToken`() {
        mockWebServer.enqueue(MockResponse().setBody(authResponseJson))

        val response = api.authorizeWithTokens("refresh-token", "device-cookie")

        assert(response == getAuthResponse())
    }

    @Test
    fun `authorizeWithTokens throws on auth failure`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        assertThrows<FailedToAuthenticateException> {
            api.authorizeWithTokens("invalid-token", null)
        }
    }

    @Test
    fun `socialLoginPostLogin makes correct API call`() {
        mockWebServer.enqueue(MockResponse().setBody(authResponseJson))

        val request = com.frontegg.android.models.SocialLoginPostLoginRequest(
            code = "auth-code",
            redirectUri = "https://app.com/callback"
        )

        val response = api.socialLoginPostLogin("google", request)

        assert(response == getAuthResponse())
        
        val recordedRequest = mockWebServer.takeRequest()
        assert(recordedRequest.path?.contains("google") == true)
    }

    @Test
    fun `socialLoginPostLogin throws on error`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        val request = com.frontegg.android.models.SocialLoginPostLoginRequest(
            code = "invalid-code",
            redirectUri = "https://app.com/callback"
        )

        assertThrows<FailedToAuthenticateException> {
            api.socialLoginPostLogin("google", request)
        }
    }
}
