package com.frontegg.android.integrity

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DeviceIntegrityInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenProvider: FakeTokenProvider

    private val authorizationCodeBody =
        """{"code":"abc","redirect_uri":"app://cb","code_verifier":"v","grant_type":"authorization_code"}"""
    private val refreshTokenBody = """{"grant_type":"refresh_token","refresh_token":"r"}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenProvider = FakeTokenProvider()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun preparedAttestor(factory: IntegrityProviderFactory) =
        DeviceIntegrityAttestor(factory) { task -> task() }.also { it.warmUp() }

    private fun client(
        baseUrl: String = server.url("/").toString().trimEnd('/'),
        attestor: DeviceIntegrityAttestor? = preparedAttestor(FakeProviderFactory(listOf(tokenProvider))),
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(DeviceIntegrityInterceptor(baseUrlProvider = { baseUrl }, attestorProvider = { attestor }))
        .build()

    private fun post(client: OkHttpClient, path: String, body: String): RecordedRequest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val request = Request.Builder()
            .url(server.url(path))
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { assertEquals(200, it.code) }
        return server.takeRequest(5, TimeUnit.SECONDS)!!
    }

    @Test
    fun `attests the authorization code exchange with a hash of its body`() {
        val recorded = post(client(), "/oauth/token", authorizationCodeBody)

        val expectedHash = integrityRequestHash(authorizationCodeBody.toByteArray(Charsets.UTF_8))
        assertEquals("token-$expectedHash", recorded.getHeader(DEVICE_INTEGRITY_HEADER))
        assertEquals(authorizationCodeBody, recorded.body.readUtf8())
    }

    @Test
    fun `attests social post-login and passkey post-login`() {
        val client = client()
        val paths = listOf(
            "/oauth/account/social/google/post-login",
            "/identity/resources/auth/v1/webauthn/postlogin",
        )

        paths.forEach { path ->
            val recorded = post(client, path, "{}")
            assertTrue(path, recorded.getHeader(DEVICE_INTEGRITY_HEADER)?.startsWith("token-") == true)
        }
    }

    @Test
    fun `attests sign-in requests behind a vendor base path`() {
        val baseUrl = server.url("/auth").toString().trimEnd('/')

        val recorded = post(client(baseUrl = baseUrl), "/auth/oauth/token", authorizationCodeBody)

        assertTrue(recorded.getHeader(DEVICE_INTEGRITY_HEADER) != null)
    }

    @Test
    fun `does not attest a refresh token grant`() {
        val recorded = post(client(), "/oauth/token", refreshTokenBody)

        assertNull(recorded.getHeader(DEVICE_INTEGRITY_HEADER))
        assertTrue(tokenProvider.requestedHashes.isEmpty())
    }

    @Test
    fun `does not attest other endpoints`() {
        val client = client()
        val paths = listOf(
            "/identity/resources/users/v1/tenant",
            "/oauth/authorize/silent",
            "/oauth/account/social//post-login",
            "/oauth/account/social/post-login",
            "/oauth/account/social/google/extra/post-login",
        )

        paths.forEach { path ->
            assertNull(path, post(client, path, "{}").getHeader(DEVICE_INTEGRITY_HEADER))
        }
        assertTrue(tokenProvider.requestedHashes.isEmpty())
    }

    @Test
    fun `does not attest GET requests`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder().url(server.url("/oauth/authorize/silent")).get().build()
        client().newCall(request).execute().close()

        assertNull(server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader(DEVICE_INTEGRITY_HEADER))
    }

    @Test
    fun `does not attest requests to another host`() {
        val recorded = post(client(baseUrl = "https://tenant.frontegg.example"), "/oauth/token", authorizationCodeBody)

        assertNull(recorded.getHeader(DEVICE_INTEGRITY_HEADER))
        assertTrue(tokenProvider.requestedHashes.isEmpty())
    }

    @Test
    fun `does nothing when attestation is disabled`() {
        val recorded = post(client(attestor = null), "/oauth/token", authorizationCodeBody)

        assertNull(recorded.getHeader(DEVICE_INTEGRITY_HEADER))
    }

    @Test
    fun `sends the request without the header when attestation times out`() {
        val timeout = IntegrityAttestationException("Play Integrity request timed out", TimeoutException())
        val failingAttestor = preparedAttestor(FakeProviderFactory(listOf(FakeTokenProvider(failure = timeout))))

        val recorded = post(client(attestor = failingAttestor), "/oauth/token", authorizationCodeBody)

        assertNull(recorded.getHeader(DEVICE_INTEGRITY_HEADER))
        assertEquals(authorizationCodeBody, recorded.body.readUtf8())
    }

    @Test
    fun `sends the request without the header when the provider cannot be prepared`() {
        val failingAttestor = preparedAttestor(
            FakeProviderFactory(
                providers = listOf(tokenProvider),
                prepareFailure = IntegrityAttestationException("Play Integrity request failed")
            )
        )

        val recorded = post(client(attestor = failingAttestor), "/oauth/token", authorizationCodeBody)

        assertNull(recorded.getHeader(DEVICE_INTEGRITY_HEADER))
    }
}
