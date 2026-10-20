package com.frontegg.android.dpop

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStoreException

class DPoPInterceptorTest {

    private lateinit var server: MockWebServer
    private val keyProvider = SoftwareDPoPKeyProvider()
    private val generator = DPoPProofGenerator(keyProvider)
    private var dpopEnabled = true

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(baseUrl: String = server.url("/").toString()): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                DPoPInterceptor(
                    baseUrlProvider = { baseUrl },
                    isEnabled = { dpopEnabled },
                    proofGeneratorProvider = { generator },
                )
            )
            .build()

    private fun post(path: String, accessToken: String? = null): Request {
        val builder = Request.Builder()
            .url(server.url(path))
            .post("{}".toRequestBody())
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
        return builder.build()
    }

    private fun get(path: String, accessToken: String): Request =
        Request.Builder().url(server.url(path)).header("Authorization", "Bearer $accessToken").build()

    @Test
    fun `token request carries a proof and keeps its authorization header`() {
        server.enqueue(MockResponse().setBody("{}"))

        client().newCall(post("/oauth/token", accessToken = "unbound")).execute().close()

        val recorded = server.takeRequest()
        val proof = recorded.getHeader("DPoP")!!
        val claims = DPoPTestSupport.decodeSegment(proof, 1)
        assertEquals("POST", claims.get("htm").asString)
        assertEquals(server.url("/oauth/token").toString(), claims.get("htu").asString)
        assertFalse(claims.has("ath"))
        assertEquals("Bearer unbound", recorded.getHeader("Authorization"))
    }

    @Test
    fun `silent authorize and social post-login are token requests`() {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))

        client().newCall(post("/oauth/authorize/silent")).execute().close()
        client().newCall(post("/oauth/account/social/google/post-login")).execute().close()

        assertTrue(server.takeRequest().getHeader("DPoP") != null)
        assertTrue(server.takeRequest().getHeader("DPoP") != null)
    }

    @Test
    fun `resource request with an unbound token is left as bearer without a proof`() {
        server.enqueue(MockResponse().setBody("{}"))

        client().newCall(get("/identity/resources/users/v2/me", "plain.jwt.token")).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("DPoP"))
        assertEquals("Bearer plain.jwt.token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `resource request with a device-bound token switches to the DPoP scheme`() {
        server.enqueue(MockResponse().setBody("{}"))
        val boundToken = DPoPTestSupport.accessTokenBoundTo(generator.thumbprint())

        client().newCall(get("/identity/resources/users/v2/me", boundToken)).execute().close()

        val recorded = server.takeRequest()
        assertEquals("DPoP $boundToken", recorded.getHeader("Authorization"))
        val claims = DPoPTestSupport.decodeSegment(recorded.getHeader("DPoP")!!, 1)
        assertEquals("GET", claims.get("htm").asString)
        assertEquals(DPoPEncoding.sha256Base64Url(boundToken), claims.get("ath").asString)
    }

    @Test
    fun `refresh with a device-bound token sends only the proof`() {
        server.enqueue(MockResponse().setBody("{}"))
        val boundToken = DPoPTestSupport.accessTokenBoundTo(generator.thumbprint())

        client().newCall(post("/oauth/token", accessToken = boundToken)).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        val claims = DPoPTestSupport.decodeSegment(recorded.getHeader("DPoP")!!, 1)
        assertFalse(claims.has("ath"))
    }

    @Test
    fun `refresh drops a token bound to a previous device key`() {
        server.enqueue(MockResponse().setBody("{}"))
        val previousKeyToken = DPoPTestSupport.accessTokenBoundTo("previous-key-thumbprint")

        client().newCall(post("/oauth/token", accessToken = previousKeyToken)).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertTrue(recorded.getHeader("DPoP") != null)
    }

    @Test
    fun `token bound to another key stays bearer`() {
        server.enqueue(MockResponse().setBody("{}"))
        val foreignToken = DPoPTestSupport.accessTokenBoundTo("some-other-thumbprint")

        client().newCall(get("/identity/resources/users/v2/me", foreignToken)).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("DPoP"))
        assertEquals("Bearer $foreignToken", recorded.getHeader("Authorization"))
    }

    @Test
    fun `token requests are matched under the base url path prefix`() {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        val prefixedClient = client(baseUrl = server.url("/auth").toString())

        prefixedClient.newCall(post("/auth/oauth/token")).execute().close()
        prefixedClient.newCall(post("/auth/identity/oauth/token")).execute().close()

        assertTrue(server.takeRequest().getHeader("DPoP") != null)
        assertNull(server.takeRequest().getHeader("DPoP"))
    }

    @Test
    fun `requests to other hosts are untouched`() {
        server.enqueue(MockResponse().setBody("{}"))

        client(baseUrl = "https://tenant.frontegg.example").newCall(post("/oauth/token")).execute().close()

        assertNull(server.takeRequest().getHeader("DPoP"))
    }

    @Test
    fun `nothing is added while DPoP is disabled`() {
        dpopEnabled = false
        server.enqueue(MockResponse().setBody("{}"))

        client().newCall(post("/oauth/token")).execute().close()

        assertNull(server.takeRequest().getHeader("DPoP"))
        assertNull(keyProvider.keyPair)
    }

    @Test
    fun `nonce challenge is retried once with the server nonce`() {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setHeader("DPoP-Nonce", "nonce-1")
                .setBody("""{"error":"use_dpop_nonce"}""")
        )
        server.enqueue(MockResponse().setBody("{}"))

        val response = client().newCall(post("/oauth/token")).execute()
        response.close()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
        val firstClaims = DPoPTestSupport.decodeSegment(server.takeRequest().getHeader("DPoP")!!, 1)
        val retryClaims = DPoPTestSupport.decodeSegment(server.takeRequest().getHeader("DPoP")!!, 1)
        assertFalse(firstClaims.has("nonce"))
        assertEquals("nonce-1", retryClaims.get("nonce").asString)
    }

    @Test
    fun `nonce challenge in WWW-Authenticate is retried`() {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setHeader("DPoP-Nonce", "nonce-2")
                .setHeader("WWW-Authenticate", "DPoP error=\"use_dpop_nonce\"")
        )
        server.enqueue(MockResponse().setBody("{}"))

        client().newCall(post("/oauth/token")).execute().close()

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a second nonce challenge is returned instead of retried again`() {
        repeat(2) { attempt ->
            server.enqueue(
                MockResponse().setResponseCode(400)
                    .setHeader("DPoP-Nonce", "nonce-$attempt")
                    .setBody("""{"error":"use_dpop_nonce"}""")
            )
        }

        val response = client().newCall(post("/oauth/token")).execute()
        response.close()

        assertEquals(400, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `remembered nonce is sent on the next request`() {
        server.enqueue(MockResponse().setHeader("DPoP-Nonce", "nonce-3").setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        val client = client()

        client.newCall(post("/oauth/token")).execute().close()
        client.newCall(post("/oauth/token")).execute().close()

        server.takeRequest()
        val claims = DPoPTestSupport.decodeSegment(server.takeRequest().getHeader("DPoP")!!, 1)
        assertEquals("nonce-3", claims.get("nonce").asString)
    }

    @Test
    fun `proof failure on a refresh still drops the device-bound token`() {
        val rsaPrivateKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        keyProvider.keyPair = KeyPair(keyProvider.getOrCreateKeyPair().public, rsaPrivateKey)
        val boundToken = DPoPTestSupport.accessTokenBoundTo(generator.thumbprint())
        server.enqueue(MockResponse().setBody("{}"))

        client().newCall(post("/oauth/token", accessToken = boundToken)).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("DPoP"))
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `proof failure sends the request without DPoP`() {
        keyProvider.failure = KeyStoreException("keystore unavailable")
        server.enqueue(MockResponse().setBody("{}"))

        val response = client().newCall(post("/oauth/token", accessToken = "unbound")).execute()
        response.close()

        assertEquals(200, response.code)
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("DPoP"))
        assertEquals("Bearer unbound", recorded.getHeader("Authorization"))
    }

    @Test
    fun `confirmation thumbprint is read defensively`() {
        assertEquals("abc", DPoPInterceptor.confirmationThumbprint(DPoPTestSupport.accessTokenBoundTo("abc")))
        assertNull(DPoPInterceptor.confirmationThumbprint("not-a-jwt"))
        assertNull(DPoPInterceptor.confirmationThumbprint("a.%%%.c"))
        assertNull(DPoPInterceptor.confirmationThumbprint("a.eyJzdWIiOiJ4In0.c"))
    }
}
