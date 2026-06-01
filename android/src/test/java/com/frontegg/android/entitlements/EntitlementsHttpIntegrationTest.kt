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
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * True HTTP-level integration test. Stands up [MockWebServer], serves realistic
 * `/frontegg/entitlements/api/v2/user-entitlements` payloads, and drives the
 * **real** [Api.getUserEntitlements] → [EntitlementsService] → evaluator chain.
 *
 * Strongest verification we can do without hitting Frontegg production:
 * exercises HTTP transport, OkHttp client, Gson parsing, the entire
 * `UserEntitlementsParser`, `EntitlementsService` caching, and the evaluator
 * decision chain in one shot. Unit tests cover each layer in isolation; this
 * one proves the layers wire together correctly against bytes-on-the-wire JSON.
 *
 * Each test uses an isolated [MockWebServer] (started in setUp, shut down in
 * tearDown — same pattern as [com.frontegg.android.services.ApiTest]).
 */
class EntitlementsHttpIntegrationTest {

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

    /**
     * Helper — enqueue a 200 response with the given JSON body and drive a load
     * through the real API path.
     */
    private fun loadWithResponse(json: String): Boolean {
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
        return entitlements.load("test-access-token")
    }

    /**
     * Helper — assert the request that just hit MockWebServer was the
     * user-entitlements V2 endpoint with the access token in the Authorization
     * header. Catches wiring regressions (wrong path, wrong header name, missing
     * Bearer prefix, etc.) that pure-parser tests would never see.
     */
    private fun assertEntitlementsRequest(request: RecordedRequest) {
        assertEquals("GET", request.method)
        assertTrue(
            "expected V2 user-entitlements path, was: ${request.path}",
            request.path?.contains("frontegg/entitlements/api/v2/user-entitlements") == true
        )
        val auth = request.getHeader("Authorization")
        assertNotNull("Authorization header must be present", auth)
        assertTrue(
            "Authorization must use Bearer scheme with the access token, was: $auth",
            auth?.startsWith("Bearer test-access-token") == true
        )
    }

    // MARK: - FR-24821 canonical scenarios

    @Test
    fun `FR-24821 — full HTTP roundtrip, sso linked to plan with defaultTreatment false, verdict false`() {
        // The exact JSON shape Yonatan posted in Slack. We're driving it through
        // the same code path production runs: real OkHttp request, real Gson
        // parse, real EntitlementsService.load → Api.getUserEntitlements → store
        // context → checkFeature → IsEntitledToFeature.evaluate.
        val responseBody = """
            {
              "features": {
                "sso": {
                  "planIds": ["ID_1"],
                  "expireTime": null,
                  "linkedPermissions": ["fe.secure.read.samlDefaultRoles"]
                },
                "directly-granted": {
                  "planIds": [],
                  "expireTime": -1,
                  "linkedPermissions": []
                }
              },
              "plans": {
                "ID_1": { "defaultTreatment": "false" }
              },
              "permissions": {
                "fe.secure.read.samlDefaultRoles": true,
                "fe.read": true
              }
            }
        """.trimIndent()

        val loaded = loadWithResponse(responseBody)
        assertTrue("load() must succeed against a 200 response", loaded)

        // Verify the request that went over the wire.
        val recorded = mockWebServer.takeRequest()
        assertEntitlementsRequest(recorded)

        // Verdict on sso — the FR-24821 customer-facing answer.
        val sso = entitlements.checkFeature("sso")
        assertFalse(
            "FR-24821: sso must NOT be entitled when its only plan defaults to false; got $sso",
            sso.isEntitled
        )
        assertEquals(NotEntitledJustification.MISSING_FEATURE, sso.justification)

        // Sanity — directly-granted IS entitled. Proves the response parsed correctly.
        val direct = entitlements.checkFeature("directly-granted")
        assertTrue("directly-granted (expireTime=-1) must be entitled; got $direct", direct.isEntitled)

        // The permission tied to sso — granted on the wire but linked to a denied feature.
        // Per web algorithm, should be denied.
        val perm = entitlements.checkPermission("fe.secure.read.samlDefaultRoles")
        assertFalse(
            "Permission linked to a denied feature must be denied; got $perm",
            perm.isEntitled
        )

        // And a wildcard-style permission with no linked feature should pass.
        val readPerm = entitlements.checkPermission("fe.read")
        assertTrue("fe.read (no linked features) should pass once granted; got $readPerm", readPerm.isEntitled)

        // Backwards-compat surface — host apps that read state.featureKeys directly
        // (e.g. for "Cached: N feature(s)" badges) still see the catalog.
        assertEquals(setOf("sso", "directly-granted"), entitlements.state.featureKeys)
        assertEquals(setOf("fe.secure.read.samlDefaultRoles", "fe.read"), entitlements.state.permissionKeys)
    }

    @Test
    fun `FR-24821 with per-tenant rule — tenant A entitled, tenant B not entitled`() {
        // Same shape, but the plan has a rule granting SSO only to tenant A.
        // Drive two separate getFeatureEntitlements calls with different JWT
        // tenantId attributes — verdict must flip.
        val responseBody = """
            {
              "features": {
                "sso": {
                  "planIds": ["ID_1"],
                  "expireTime": null,
                  "linkedPermissions": []
                }
              },
              "plans": {
                "ID_1": {
                  "defaultTreatment": "false",
                  "rules": [
                    {
                      "conditionLogic": "and",
                      "treatment": "true",
                      "conditions": [
                        {"attribute":"frontegg.tenantId","negate":false,"op":"in_list","value":{"list":["tenant-A"]}}
                      ]
                    }
                  ]
                }
              },
              "permissions": {}
            }
        """.trimIndent()
        assertTrue(loadWithResponse(responseBody))

        val onTenantA = entitlements.checkFeature(
            "sso",
            Attributes(jwt = mapOf("tenantId" to "tenant-A"))
        )
        assertTrue("Tenant A should be entitled via the rule match; got $onTenantA", onTenantA.isEntitled)

        val onTenantB = entitlements.checkFeature(
            "sso",
            Attributes(jwt = mapOf("tenantId" to "tenant-B"))
        )
        assertFalse(
            "Tenant B should fall through to defaultTreatment=false; got $onTenantB",
            onTenantB.isEntitled
        )
        assertEquals(NotEntitledJustification.MISSING_FEATURE, onTenantB.justification)
    }

    // MARK: - HTTP error paths

    @Test
    fun `HTTP 500 from server — load returns false, state stays empty, verdict is ENTITLEMENTS_NOT_LOADED`() {
        // Simulates the customer's network-flake scenario. EntitlementsService.load
        // returns false; state.context stays null. Subsequent checkFeature must
        // not blow up — it returns ENTITLEMENTS_NOT_LOADED because the cache was
        // never populated. (If the load was a re-load after a tenant switch and
        // PR #254's clear() ran, this is the safest possible verdict — honest "I
        // don't know" instead of a lie from a stale cache.)
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody(""))
        val loaded = entitlements.load("test-access-token")
        assertFalse("load() must return false on HTTP 500", loaded)
        assertFalse(entitlements.hasLoadedOnce)

        val sso = entitlements.checkFeature("sso")
        assertFalse(sso.isEntitled)
        assertEquals(NotEntitledJustification.ENTITLEMENTS_NOT_LOADED, sso.justification)
    }

    @Test
    fun `HTTP 200 with malformed JSON body — load returns false gracefully`() {
        mockWebServer.enqueue(MockResponse().setBody("not-json-at-all").setResponseCode(200))
        val loaded = entitlements.load("test-access-token")
        assertFalse("load() must return false on unparseable body", loaded)
        assertFalse(entitlements.hasLoadedOnce)
    }

    // MARK: - Edge-case payloads

    @Test
    fun `empty response — features and permissions both empty maps`() {
        val responseBody = """{"features":{},"plans":{},"permissions":{}}"""
        assertTrue(loadWithResponse(responseBody))

        val anyFeature = entitlements.checkFeature("anything")
        assertFalse(anyFeature.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_FEATURE, anyFeature.justification)

        val anyPerm = entitlements.checkPermission("anything")
        assertFalse(anyPerm.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_PERMISSION, anyPerm.justification)
    }

    @Test
    fun `feature with expireTime in the future — direct evaluator wins regardless of plan defaultTreatment false`() {
        // The most likely "why is my feature still entitled" surface. Pinned
        // here as expected behavior: per web's directEntitlementEvalutor a
        // non-null future expireTime is unconditionally entitling. If the
        // server returns expireTime != null for a feature on a tenant that
        // shouldn't have it, mobile (and web!) will return isEntitled=true.
        // That's a server-side data issue, not a mobile-port bug.
        val future = System.currentTimeMillis() + 365L * 24 * 3600 * 1000
        val responseBody = """
            {
              "features": {
                "alpha": {
                  "planIds": ["deny-plan"],
                  "expireTime": $future,
                  "linkedPermissions": []
                }
              },
              "plans": {
                "deny-plan": { "defaultTreatment": "false" }
              },
              "permissions": {}
            }
        """.trimIndent()
        assertTrue(loadWithResponse(responseBody))

        val alpha = entitlements.checkFeature("alpha")
        assertTrue(
            "Feature with non-null future expireTime must be entitled (matches web's direct evaluator); got $alpha",
            alpha.isEntitled
        )
    }

    @Test
    fun `feature flag rule reads tenantId from prepared frontegg-prefixed JWT attribute`() {
        // Validates that the AttributesPreparer prefixing actually flows through
        // to the on-the-wire rule attribute name. The JWT carries `tenantId`
        // (no prefix), AttributesPreparer prepends `frontegg.`, and the rule
        // references `frontegg.tenantId`. End-to-end smoke.
        val responseBody = """
            {
              "features": {
                "beta": {
                  "planIds": [],
                  "expireTime": null,
                  "linkedPermissions": [],
                  "featureFlag": {
                    "on": true,
                    "offTreatment": "false",
                    "defaultTreatment": "false",
                    "rules": [
                      {
                        "conditionLogic": "and",
                        "treatment": "true",
                        "conditions": [
                          {"attribute":"frontegg.tenantId","negate":false,"op":"in_list","value":{"list":["tenant-A"]}}
                        ]
                      }
                    ]
                  }
                }
              },
              "plans": {},
              "permissions": {}
            }
        """.trimIndent()
        assertTrue(loadWithResponse(responseBody))

        val onTenantA = entitlements.checkFeature(
            "beta",
            Attributes(jwt = mapOf("tenantId" to "tenant-A"))
        )
        assertTrue("Flag rule should grant tenant A; got $onTenantA", onTenantA.isEntitled)

        val onTenantB = entitlements.checkFeature(
            "beta",
            Attributes(jwt = mapOf("tenantId" to "tenant-B"))
        )
        assertFalse("Flag rule should deny tenant B; got $onTenantB", onTenantB.isEntitled)
    }

    @Test
    fun `large response with many features and plans — parses and evaluates correctly`() {
        // Smoke test for a non-trivial payload size — verifies the parser
        // doesn't choke on >a-handful-of-entries and that lookup-time evaluation
        // still picks the right plan for the right feature.
        val features = (1..50).joinToString(",") { i ->
            """"feature-$i":{"planIds":["plan-$i"],"expireTime":null,"linkedPermissions":[]}"""
        }
        val plans = (1..50).joinToString(",") { i ->
            val treatment = if (i % 2 == 0) "true" else "false"
            """"plan-$i":{"defaultTreatment":"$treatment"}"""
        }
        val responseBody = """{"features":{$features},"plans":{$plans},"permissions":{}}"""
        assertTrue(loadWithResponse(responseBody))

        // Even-indexed features should be entitled (their plan defaults true),
        // odd-indexed denied.
        assertTrue(entitlements.checkFeature("feature-2").isEntitled)
        assertFalse(entitlements.checkFeature("feature-3").isEntitled)
        assertTrue(entitlements.checkFeature("feature-50").isEntitled)
        assertFalse(entitlements.checkFeature("feature-49").isEntitled)

        assertEquals(50, entitlements.state.featureKeys.size)
    }

    // MARK: - PR #254 (cache invalidation) interaction

    @Test
    fun `clear after a successful load wipes the cache to ENTITLEMENTS_NOT_LOADED`() {
        // Ensures the in-memory cache truly resets on clear() — the path
        // PR #254 invokes on tenant switch.
        val responseBody = """
            {
              "features": { "alpha": { "planIds": [], "expireTime": -1, "linkedPermissions": [] } },
              "plans": {},
              "permissions": {}
            }
        """.trimIndent()
        assertTrue(loadWithResponse(responseBody))
        assertTrue(entitlements.checkFeature("alpha").isEntitled)

        entitlements.clear()

        val afterClear = entitlements.checkFeature("alpha")
        assertFalse(afterClear.isEntitled)
        assertEquals(NotEntitledJustification.ENTITLEMENTS_NOT_LOADED, afterClear.justification)
    }
}
