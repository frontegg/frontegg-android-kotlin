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
import java.io.IOException
import java.util.Base64

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
    fun `getUserSessions marks the access token session as current when the server does not`() {
        val claims = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sid":"s-2"}""".toByteArray())
        val credentialManager = mockkClass(CredentialManager::class)
        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) }.returns("header.$claims.signature")
        mockWebServer.enqueue(
            MockResponse().setBody("""[{"id": "s-1", "current": false}, {"id": "s-2", "current": false}]""")
        )

        val sessions = Api(credentialManager).getUserSessions()

        assert(sessions == listOf(UserSession("s-1"), UserSession("s-2", current = true)))
    }

    @Test
    fun `getUserSessions keeps the server current session over the access token session`() {
        val claims = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sid":"s-2"}""".toByteArray())
        val credentialManager = mockkClass(CredentialManager::class)
        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) }.returns("header.$claims.signature")
        mockWebServer.enqueue(MockResponse().setBody("""[{"id": "s-1", "current": true}, {"id": "s-2"}]"""))

        val sessions = Api(credentialManager).getUserSessions()

        assert(sessions == listOf(UserSession("s-1", current = true), UserSession("s-2")))
    }

    @Test
    fun `getUserSessions accepts a paginated items envelope`() {
        mockWebServer.enqueue(MockResponse().setBody("""{"items": [{"id": "s-9"}]}"""))

        assert(api.getUserSessions() == listOf(UserSession("s-9")))
    }

    @Test
    fun `getUserSessions reports an unexpected JSON shape as a network error`() {
        listOf("""{"sessions": [{"id": "s-1"}]}""", """{"items": null}""", "\"signed out\"").forEach { body ->
            mockWebServer.enqueue(MockResponse().setBody(body))

            assertThrows<IOException> { api.getUserSessions() }
        }
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
    fun `revokeUserSession throws on error response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        assertThrows<FailedToAuthenticateException> {
            api.revokeUserSession("s-2")
        }
    }

    @Test
    fun `revokeUserSession treats a session that is already gone as revoked`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        api.revokeUserSession("missing")

        assert(mockWebServer.takeRequest().method == "DELETE")
    }

    @Test
    fun `revokeOtherSessions signs out each session except the current one`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setBody("""[{"id": "s-1", "current": true}, {"id": "s-2"}, {"id": "s-3"}]""")
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val revokedSessionIds = SessionsService(api).revokeOtherSessions()

        assert(revokedSessionIds == listOf("s-2", "s-3"))
        assert(mockWebServer.takeRequest().method == "GET")
        val revokedPaths = List(2) { mockWebServer.takeRequest() }.map { it.method to it.path }
        assert(
            revokedPaths == listOf(
                "DELETE" to "/identity/resources/users/sessions/v1/me/s-2",
                "DELETE" to "/identity/resources/users/sessions/v1/me/s-3",
            )
        )
    }

    @Test
    fun `revokeOtherSessions tries every session and throws once after a failure`() {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """[{"id": "s-1", "current": true}, {"id": "s-2"}, {"id": "s-3"}, {"id": "s-4"}, {"id": "s-5"}]"""
            )
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val failure = assertThrows<FailedToAuthenticateException> {
            runBlocking { SessionsService(api).revokeOtherSessions() }
        }

        assert(failure.suppressed.size == 1)
        assert(mockWebServer.requestCount == 5)
    }

    @Test
    fun `revokeOtherSessions returns no ids when this device has the only session`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody("""[{"id": "s-1", "current": true}]"""))

        val revokedSessionIds = SessionsService(api).revokeOtherSessions()

        assert(revokedSessionIds.isEmpty())
        assert(mockWebServer.requestCount == 1)
    }

    @Test
    fun `revokeOtherSessions signs out nothing when the sessions response has an unexpected shape`() {
        mockWebServer.enqueue(MockResponse().setBody("""{"sessions": [{"id": "s-2"}]}"""))

        assertThrows<IOException> {
            runBlocking { SessionsService(api).revokeOtherSessions() }
        }
        assert(mockWebServer.requestCount == 1)
    }

    @Test
    fun `revokeOtherSessions succeeds when a session is already gone`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setBody("""[{"id": "s-1", "current": true}, {"id": "s-2"}, {"id": "s-3"}]""")
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val revokedSessionIds = SessionsService(api).revokeOtherSessions()

        assert(revokedSessionIds == listOf("s-2", "s-3"))
        assert(mockWebServer.requestCount == 3)
    }

    @Test
    fun `revokeOtherSessions with an access token uses it instead of the stored one`() = runBlocking {
        val claims = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sid":"s-1"}""".toByteArray())
        val initiatingAccessToken = "header.$claims.signature"
        mockWebServer.enqueue(MockResponse().setBody("""[{"id": "s-1"}, {"id": "s-2"}]"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val revokedSessionIds = SessionsService(api).revokeOtherSessions(initiatingAccessToken)

        assert(revokedSessionIds == listOf("s-2"))
        val authorizations = List(2) { mockWebServer.takeRequest().getHeader("Authorization") }
        assert(authorizations == List(2) { "Bearer $initiatingAccessToken" })
    }

    @Test
    fun `revokeOtherSessions signs out nothing when no session is current`() {
        mockWebServer.enqueue(MockResponse().setBody("""[{"id": "s-2"}]"""))

        assertThrows<IllegalStateException> {
            runBlocking { SessionsService(api).revokeOtherSessions() }
        }
        assert(mockWebServer.requestCount == 1)
    }

    @Test
    fun `getUserSessions reports a response that is not JSON as a network error`() {
        mockWebServer.enqueue(MockResponse().setBody("<html>Sign in to the network</html>"))

        assertThrows<IOException> { api.getUserSessions() }
    }
}
