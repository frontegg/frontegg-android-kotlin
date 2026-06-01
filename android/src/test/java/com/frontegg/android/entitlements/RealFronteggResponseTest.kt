package com.frontegg.android.entitlements

import android.util.Log
import com.frontegg.android.models.NotEntitledJustification
import com.frontegg.android.services.Api
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.EntitlementsService
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.StorageProvider
import com.frontegg.android.utils.CredentialKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.mockkStatic
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins behavior against a **real** `/frontegg/entitlements/api/v2/user-entitlements`
 * response captured directly from Frontegg's prod API.
 *
 * Captured via vendor M2M token impersonating a real user against the
 * `frontegg-mobile` MCP vendor's tenant on 2026-05-28. The payload below is
 * verbatim — copy-pasted from `curl` output — so the test proves the SDK
 * handles the *actual* response shape the server emits, not just our
 * understanding of what it should look like.
 *
 * Why this matters: the unit tests cover algorithmic correctness, and
 * [EntitlementsHttpIntegrationTest] covers the HTTP transport with synthetic
 * payloads. This test closes the last gap — "is the server actually emitting
 * the shape our parser expects". If the server ever quietly adds/removes a
 * field this test will surface the divergence (parse failure or a verdict
 * change against our pinned expectations).
 *
 * Refresh procedure: re-capture with
 *   `curl -H 'Authorization: Bearer <vendor-jwt>' \
 *         -H 'frontegg-tenant-id: <tenant-id>' \
 *         -H 'frontegg-user-id: <user-id>' \
 *         https://api.frontegg.com/frontegg/entitlements/api/v2/user-entitlements`
 * and re-run the assertion set against the new payload. Keep the verbatim
 * JSON in [REAL_V2_RESPONSE] below.
 */
class RealFronteggResponseTest {

    private lateinit var api: Api
    private lateinit var mockWebServer: MockWebServer
    private lateinit var credentialManager: CredentialManager
    private val storage = mockk<FronteggInnerStorage>()
    private lateinit var entitlements: EntitlementsService

    @Before
    fun setUp() {
        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(storage)
        every { storage.clientId }.returns("test-client-id")
        every { storage.applicationId }.returns("test-application-id")
        every { storage.regions }.returns(emptyList())
        every { storage.entitlementsEnabled }.returns(true)

        mockWebServer = MockWebServer()
        mockWebServer.start()
        every { storage.baseUrl }.returns(mockWebServer.url("").toString())

        credentialManager = mockkClass(CredentialManager::class)
        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) }.returns("test-access-token")

        api = Api(credentialManager)
        entitlements = EntitlementsService(api = api, enabled = true)

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        io.mockk.unmockkAll()
    }

    @Test
    fun `real Frontegg V2 response parses without dropping any documented field`() {
        mockWebServer.enqueue(MockResponse().setBody(REAL_V2_RESPONSE).setResponseCode(200))
        assertTrue(entitlements.load("test-access-token"))

        val ctx = entitlements.state.context
        assertEquals("expected 3 features", 3, ctx?.features?.size)
        assertEquals("expected 3 plans", 3, ctx?.plans?.size)
        // 13 permissions in the captured response, all with value=true.
        assertEquals("expected 13 permissions", 13, ctx?.permissions?.size)

        // Smoke — fields the algorithm actually reads. If the parser silently
        // dropped any of these, the verdict tests below would still pass but
        // these structural assertions wouldn't.
        val testFeature = ctx?.features?.get("test-feature")
        assertEquals(emptyList<String>(), testFeature?.planIds)
        assertEquals(null, testFeature?.expireTime)
        assertEquals(
            listOf(
                "fe.account-settings.write.custom-login-box",
                "fe.connectivity.*",
                "fe.secure.*",
                "fe.secure.read.*",
                "fe.subscriptions.*"
            ),
            testFeature?.linkedPermissions
        )
        val flag = testFeature?.featureFlag
        assertEquals(true, flag?.on)
        assertEquals(Treatment.TRUE, flag?.defaultTreatment)
        assertEquals(Treatment.FALSE, flag?.offTreatment)
        assertEquals(0, flag?.rules?.size)
    }

    @Test
    fun `real response — feature with no plans no flag no expireTime is NOT entitled`() {
        // `mcp_smoke_1778500431062` and `mcp_probe_1778499981` both have
        // planIds=[], expireTime=null, no featureFlag → MISSING_FEATURE.
        mockWebServer.enqueue(MockResponse().setBody(REAL_V2_RESPONSE).setResponseCode(200))
        assertTrue(entitlements.load("test-access-token"))

        val smoke = entitlements.checkFeature("mcp_smoke_1778500431062")
        assertFalse("mcp_smoke must NOT be entitled (planIds=[] expireTime=null no flag); was $smoke", smoke.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_FEATURE, smoke.justification)

        val probe = entitlements.checkFeature("mcp_probe_1778499981")
        assertFalse(probe.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_FEATURE, probe.justification)
    }

    @Test
    fun `real response — feature with featureFlag on and defaultTreatment true IS entitled`() {
        // `test-feature` has on:true, defaultTreatment:"true", rules:[] →
        // FeatureFlagEvaluator returns Treatment.TRUE → isEntitled=true.
        mockWebServer.enqueue(MockResponse().setBody(REAL_V2_RESPONSE).setResponseCode(200))
        assertTrue(entitlements.load("test-access-token"))

        val testFeature = entitlements.checkFeature("test-feature")
        assertTrue(
            "test-feature must be entitled via featureFlag (on:true, default:true); was $testFeature",
            testFeature.isEntitled
        )
        assertEquals(null, testFeature.justification)
    }

    @Test
    fun `real response — unknown feature key returns MISSING_FEATURE`() {
        mockWebServer.enqueue(MockResponse().setBody(REAL_V2_RESPONSE).setResponseCode(200))
        assertTrue(entitlements.load("test-access-token"))

        val unknown = entitlements.checkFeature("nonexistent-feature")
        assertFalse(unknown.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_FEATURE, unknown.justification)
    }

    @Test
    fun `real response — direct granted permission key passes`() {
        // The response grants `fe.account-settings.delete.account` (concrete, not
        // wildcard) with value=true. checkPermission of that exact key matches
        // directly. No linked features mention it, so the wildcard match alone
        // is the verdict.
        mockWebServer.enqueue(MockResponse().setBody(REAL_V2_RESPONSE).setResponseCode(200))
        assertTrue(entitlements.load("test-access-token"))

        val perm = entitlements.checkPermission("fe.account-settings.delete.account")
        assertTrue("granted permission with no linked features should pass; got $perm", perm.isEntitled)
    }

    @Test
    fun `real response — wildcard permission resolves a concrete sub-key`() {
        // `fe.secure.*` is granted in the response. Asking about a concrete
        // child (`fe.secure.read.users`) must match the wildcard.
        //
        // Note on linked-feature roll-up: `test-feature.linkedPermissions`
        // contains `fe.secure.*` (the wildcard, not the concrete key), so
        // `getLinkedFeatures("fe.secure.read.users")` finds no matches via
        // exact `.includes(...)` — matching web's behavior in
        // entitlement-results.utils.ts. With no linked features, the wildcard
        // grant is the whole verdict → entitled.
        mockWebServer.enqueue(MockResponse().setBody(REAL_V2_RESPONSE).setResponseCode(200))
        assertTrue(entitlements.load("test-access-token"))

        val perm = entitlements.checkPermission("fe.secure.read.users")
        assertTrue(
            "fe.secure.* should match fe.secure.read.users; got $perm",
            perm.isEntitled
        )
    }

    @Test
    fun `real response — permission whose exact key is also a linkedPermission rolls up to feature verdict`() {
        // `fe.secure.*` is BOTH granted directly AND listed in
        // test-feature.linkedPermissions. So:
        //   1. PermissionMatcher matches via exact equality (no wildcard
        //      expansion needed — the granted key IS `fe.secure.*`).
        //   2. test-feature is found via linkedPermissions.contains("fe.secure.*").
        //   3. IsEntitledToFeature("test-feature") → entitled (flag on, default true).
        //   4. Verdict: entitled.
        mockWebServer.enqueue(MockResponse().setBody(REAL_V2_RESPONSE).setResponseCode(200))
        assertTrue(entitlements.load("test-access-token"))

        val perm = entitlements.checkPermission("fe.secure.*")
        assertTrue(
            "fe.secure.* granted + linked to entitled test-feature must be entitled; got $perm",
            perm.isEntitled
        )
    }

    @Test
    fun `real response — missing permission key returns MISSING_PERMISSION`() {
        mockWebServer.enqueue(MockResponse().setBody(REAL_V2_RESPONSE).setResponseCode(200))
        assertTrue(entitlements.load("test-access-token"))

        val perm = entitlements.checkPermission("fe.nonexistent.something")
        assertFalse(perm.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_PERMISSION, perm.justification)
    }

    @Test
    fun `real response — backwards-compat featureKeys and permissionKeys are populated`() {
        // Host apps reading auth.entitlements.state.featureKeys directly (e.g.
        // demo's "Cached: N feature(s)" badge) must still see the catalog of
        // every feature key in the response, regardless of entitlement verdict.
        mockWebServer.enqueue(MockResponse().setBody(REAL_V2_RESPONSE).setResponseCode(200))
        assertTrue(entitlements.load("test-access-token"))

        assertEquals(
            setOf("mcp_smoke_1778500431062", "mcp_probe_1778499981", "test-feature"),
            entitlements.state.featureKeys
        )
        // permissionKeys should equal the keys with value=true (all 13 in this
        // payload).
        assertEquals(13, entitlements.state.permissionKeys.size)
        assertTrue(entitlements.state.permissionKeys.contains("fe.secure.*"))
        assertTrue(entitlements.state.permissionKeys.contains("dp.entitlements.*"))
    }

    companion object {
        /**
         * Verbatim `/frontegg/entitlements/api/v2/user-entitlements` response
         * body captured 2026-05-28 against `api.frontegg.com` via vendor M2M
         * token impersonation (`frontegg-tenant-id` + `frontegg-user-id`
         * headers) for the `frontegg-mobile` MCP vendor's first tenant.
         *
         * Do not edit by hand — re-capture if the algorithm or response shape
         * needs to be re-validated against a different tenant / vendor.
         */
        private const val REAL_V2_RESPONSE = """{
  "features": {
    "mcp_smoke_1778500431062": {
      "planIds": [],
      "expireTime": null,
      "linkedPermissions": []
    },
    "mcp_probe_1778499981": {
      "planIds": [],
      "expireTime": null,
      "linkedPermissions": []
    },
    "test-feature": {
      "planIds": [],
      "expireTime": null,
      "linkedPermissions": [
        "fe.account-settings.write.custom-login-box",
        "fe.connectivity.*",
        "fe.secure.*",
        "fe.secure.read.*",
        "fe.subscriptions.*"
      ],
      "featureFlag": {
        "on": true,
        "defaultTreatment": "true",
        "offTreatment": "false",
        "rules": []
      }
    }
  },
  "plans": {
    "be6aabf8-a7c4-4026-b2c6-8944e402c991": { "defaultTreatment": "false" },
    "2eb288b0-a473-4668-aa71-ddda3c02d576": { "defaultTreatment": "false" },
    "1fd0d21a-2e16-4089-95b7-91cfd0838369": { "defaultTreatment": "false" }
  },
  "permissions": {
    "fe.secure.*": true,
    "fe.account-settings.delete.account": true,
    "fe.subscriptions.*": true,
    "dp.*": true,
    "fe.connectivity.delete.webhook": true,
    "fe.connectivity.*": true,
    "dp.secure.read.impersonationConfiguration": true,
    "dp.secure.write.impersonation": true,
    "dp.secure.write.impersonationConfiguration": true,
    "dp.applications.delete.app": true,
    "dp.applications.read.app": true,
    "fe.account-settings.read.app": true,
    "dp.entitlements.*": true
  }
}"""
    }
}
