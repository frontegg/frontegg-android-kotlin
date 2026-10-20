package com.frontegg.android.services

import android.util.Log
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.models.UserSession
import com.frontegg.android.utils.CredentialKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class ApiSessionsTest {
    private lateinit var api: Api
    private lateinit var mockWebServer: MockWebServer
    private val mockStorage = mockk<FronteggInnerStorage>()

    private val sessionsJson = """
        [
            {"id": "s-1", "userAgent": "Chrome", "ipAddress": "10.0.0.1", "createdAt": "2026-10-01T10:00:00Z", "current": true},
            {"id": "s-2", "userAgent": null, "ipAddress": "10.0.0.2"},
            {"userAgent": "no id"},
            "not an object"
        ]
    """.trimIndent()

    @Before
    fun setUp() {
        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(mockStorage)
        every { mockStorage.clientId }.returns("TestClientId")
        every { mockStorage.applicationId }.returns("TestApplicationId")
        every { mockStorage.regions }.returns(listOf())

        val credentialManager = mockkClass(CredentialManager::class)
        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) }.returns("Test Access Token")

        mockWebServer = MockWebServer()
        mockWebServer.start()
        every { mockStorage.baseUrl }.returns(mockWebServer.url("").toString().removeSuffix("/"))

        api = Api(credentialManager)

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        unmockkAll()
    }

    @Test
    fun `getUserSessions parses sessions and skips malformed entries`() {
        mockWebServer.enqueue(MockResponse().setBody(sessionsJson))

        val sessions = api.getUserSessions()

        assert(
            sessions == listOf(
                UserSession("s-1", "Chrome", "10.0.0.1", "2026-10-01T10:00:00Z", current = true),
                UserSession("s-2", null, "10.0.0.2", null, current = false),
            )
        )
        val request = mockWebServer.takeRequest()
        assert(request.method == "GET")
        assert(request.path == "/identity/resources/users/sessions/v1/me")
        assert(request.getHeader("Authorization") == "Bearer Test Access Token")
    }

    @Test
    fun `getUserSessions accepts a paginated items envelope`() {
        mockWebServer.enqueue(MockResponse().setBody("""{"items": [{"id": "s-9"}]}"""))

        assert(api.getUserSessions() == listOf(UserSession("s-9")))
    }

    @Test
    fun `getUserSessions throws on error response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        assertThrows<FailedToAuthenticateException> {
            api.getUserSessions()
        }
    }

    @Test
    fun `revokeUserSession deletes the encoded session path`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        api.revokeUserSession("s 1/2")

        val request = mockWebServer.takeRequest()
        assert(request.method == "DELETE")
        assert(request.path == "/identity/resources/users/sessions/v1/me/s%201%2F2")
    }

    @Test
    fun `revokeOtherUserSessions deletes all other sessions`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        api.revokeOtherUserSessions()

        val request = mockWebServer.takeRequest()
        assert(request.method == "DELETE")
        assert(request.path == "/identity/resources/users/sessions/v1/me/all")
    }

    @Test
    fun `revokeUserSession throws on error response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        assertThrows<FailedToAuthenticateException> {
            api.revokeUserSession("missing")
        }
    }

    @Test
    fun `SessionsService delegates to the api`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody("""[{"id": "s-1", "current": true}]"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val service = SessionsService(api)

        assert(service.getActiveSessions() == listOf(UserSession("s-1", current = true)))
        service.revokeOtherSessions()
        mockWebServer.takeRequest()
        assert(mockWebServer.takeRequest().path == "/identity/resources/users/sessions/v1/me/all")
    }
}
