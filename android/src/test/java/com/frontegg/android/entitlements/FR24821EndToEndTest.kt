package com.frontegg.android.entitlements

import com.frontegg.android.models.NotEntitledJustification
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end smoke tests against realistic `/user-entitlements` JSON payloads, run
 * before exposing the SDK to QA. Each test feeds a hand-crafted JSON string through
 * the actual [UserEntitlementsParser] → [IsEntitledToFeature] / [IsEntitledToPermission]
 * chain (same path production exercises) and asserts the verdict.
 *
 * These are deliberately concrete — fixed JSON strings, fixed expected verdicts —
 * so any regression in the parser, the evaluators, or the wiring between them
 * fails one of these in a recognizable shape. The unit-level tests cover each
 * evaluator in isolation; these prove the whole chain agrees with the web SDK on
 * the scenarios Pavel is most likely to hit during FR-24821 QA.
 *
 * Authoritative algorithm reference:
 * `@frontegg/entitlements-javascript-commons/src/user-entitlements/is-entitled-to-feature/evaluators/`.
 */
class FR24821EndToEndTest {

    private fun checkFeature(
        json: String,
        featureKey: String,
        jwtClaims: Map<String, Any?> = emptyMap()
    ): EntitlementResult {
        val ctx = UserEntitlementsParser.parse(JsonParser.parseString(json).asJsonObject)
        return IsEntitledToFeature.evaluate(featureKey, ctx, Attributes(jwt = jwtClaims))
    }

    private fun checkPermission(
        json: String,
        permissionKey: String,
        jwtClaims: Map<String, Any?> = emptyMap()
    ): EntitlementResult {
        val ctx = UserEntitlementsParser.parse(JsonParser.parseString(json).asJsonObject)
        return IsEntitledToPermission.evaluate(permissionKey, ctx, Attributes(jwt = jwtClaims))
    }

    // MARK: - FR-24821 canonical reproduction

    @Test
    fun `FR-24821 sso linked to plan with defaultTreatment false → not entitled`() {
        // The exact shape Yonatan posted in Slack. expireTime = null forces the
        // direct evaluator to fall through; plan-targeting then runs and resolves
        // to defaultTreatment "false".
        val json = """
            {
              "features": {
                "sso": { "planIds": ["ID_1"], "expireTime": null, "linkedPermissions": [] }
              },
              "plans": {
                "ID_1": { "defaultTreatment": "false" }
              },
              "permissions": {}
            }
        """.trimIndent()
        val result = checkFeature(json, "sso")
        assertFalse("FR-24821: sso must NOT be entitled when its only plan defaults to false; was $result", result.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_FEATURE, result.justification)
    }

    @Test
    fun `FR-24821 sso plan rule matches tenant A but tenant B falls through to defaultTreatment false`() {
        // Same shape, but the plan now has a rule that grants SSO to tenant A. On
        // tenant A → entitled (rule wins). On tenant B → rule doesn't match,
        // falls through to defaultTreatment "false" → not entitled.
        val json = """
            {
              "features": {
                "sso": { "planIds": ["ID_1"], "expireTime": null, "linkedPermissions": [] }
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

        val onTenantA = checkFeature(json, "sso", mapOf("tenantId" to "tenant-A"))
        assertTrue("Tenant A should be entitled to sso via the rule match; was $onTenantA", onTenantA.isEntitled)

        val onTenantB = checkFeature(json, "sso", mapOf("tenantId" to "tenant-B"))
        assertFalse("Tenant B should NOT be entitled to sso (rule mismatches, defaults to false); was $onTenantB", onTenantB.isEntitled)
    }

    // MARK: - Direct entitlement (the most common false-positive surface)

    @Test
    fun `feature with non-null expireTime in the future short-circuits the chain to entitled`() {
        // This is the behavior that surprised us during Pavel's repro — by design.
        // If the server returns `expireTime` as a non-null future timestamp for a
        // feature, web's direct evaluator wins regardless of plan membership.
        // Test pinning to prevent accidental regression.
        val futureMs = System.currentTimeMillis() + 365L * 24 * 3600 * 1000
        val json = """
            {
              "features": {
                "alpha": { "planIds": ["plan-deny"], "expireTime": $futureMs, "linkedPermissions": [] }
              },
              "plans": {
                "plan-deny": { "defaultTreatment": "false" }
              },
              "permissions": {}
            }
        """.trimIndent()
        val result = checkFeature(json, "alpha")
        assertTrue("Feature with non-expired expireTime must be entitled (direct evaluator wins); was $result", result.isEntitled)
        assertEquals(null, result.justification)
    }

    @Test
    fun `feature with NO_EXPIRATION_TIME sentinel is entitled forever`() {
        // expireTime = -1 means "directly assigned, no expiration".
        val json = """
            {
              "features": {
                "alpha": { "planIds": [], "expireTime": -1, "linkedPermissions": [] }
              },
              "plans": {},
              "permissions": {}
            }
        """.trimIndent()
        val result = checkFeature(json, "alpha")
        assertTrue(result.isEntitled)
    }

    @Test
    fun `feature with expireTime in the past returns BUNDLE_EXPIRED`() {
        val pastMs = System.currentTimeMillis() - 24L * 3600 * 1000 // yesterday
        val json = """
            {
              "features": {
                "alpha": { "planIds": [], "expireTime": $pastMs, "linkedPermissions": [] }
              },
              "plans": {},
              "permissions": {}
            }
        """.trimIndent()
        val result = checkFeature(json, "alpha")
        assertFalse(result.isEntitled)
        assertEquals(NotEntitledJustification.BUNDLE_EXPIRED, result.justification)
    }

    // MARK: - Plan with multiple rules

    @Test
    fun `plan grants only when a rule matches AND defaultTreatment is false`() {
        // Two rules, both must NOT match for tenant C → falls to defaultTreatment.
        // Tenant A matches rule[0] → entitled.
        // Tenant B matches rule[1] → entitled.
        // Tenant C → entitled iff defaultTreatment="true".
        val json = """
            {
              "features": {
                "alpha": { "planIds": ["plan-1"], "expireTime": null, "linkedPermissions": [] }
              },
              "plans": {
                "plan-1": {
                  "defaultTreatment": "false",
                  "rules": [
                    {"conditionLogic":"and","treatment":"true","conditions":[
                      {"attribute":"frontegg.tenantId","negate":false,"op":"in_list","value":{"list":["tenant-A"]}}
                    ]},
                    {"conditionLogic":"and","treatment":"true","conditions":[
                      {"attribute":"frontegg.tenantId","negate":false,"op":"in_list","value":{"list":["tenant-B"]}}
                    ]}
                  ]
                }
              },
              "permissions": {}
            }
        """.trimIndent()
        assertTrue(checkFeature(json, "alpha", mapOf("tenantId" to "tenant-A")).isEntitled)
        assertTrue(checkFeature(json, "alpha", mapOf("tenantId" to "tenant-B")).isEntitled)
        assertFalse(checkFeature(json, "alpha", mapOf("tenantId" to "tenant-C")).isEntitled)
    }

    @Test
    fun `feature linked to multiple plans grants if any plan grants`() {
        // Two plans linked to the same feature. plan-1 says "true" via
        // defaultTreatment, plan-2 says "false". Per web's planTargetingRules
        // evaluator (loops over planIds, first "true" wins), the feature is
        // entitled.
        val json = """
            {
              "features": {
                "alpha": { "planIds": ["plan-1","plan-2"], "expireTime": null, "linkedPermissions": [] }
              },
              "plans": {
                "plan-1": { "defaultTreatment": "true" },
                "plan-2": { "defaultTreatment": "false" }
              },
              "permissions": {}
            }
        """.trimIndent()
        assertTrue(checkFeature(json, "alpha").isEntitled)
    }

    // MARK: - Feature flag

    @Test
    fun `feature flag on with defaultTreatment true grants when direct doesnt apply`() {
        val json = """
            {
              "features": {
                "alpha": {
                  "planIds": [],
                  "expireTime": null,
                  "linkedPermissions": [],
                  "featureFlag": { "on": true, "offTreatment": "false", "defaultTreatment": "true" }
                }
              },
              "plans": {},
              "permissions": {}
            }
        """.trimIndent()
        assertTrue(checkFeature(json, "alpha").isEntitled)
    }

    @Test
    fun `feature flag off with offTreatment false denies`() {
        val json = """
            {
              "features": {
                "alpha": {
                  "planIds": [],
                  "expireTime": null,
                  "linkedPermissions": [],
                  "featureFlag": { "on": false, "offTreatment": "false", "defaultTreatment": "true" }
                }
              },
              "plans": {},
              "permissions": {}
            }
        """.trimIndent()
        assertFalse(checkFeature(json, "alpha").isEntitled)
    }

    @Test
    fun `feature flag with rule that matches tenant A but not tenant B`() {
        val json = """
            {
              "features": {
                "alpha": {
                  "planIds": [],
                  "expireTime": null,
                  "linkedPermissions": [],
                  "featureFlag": {
                    "on": true,
                    "offTreatment": "false",
                    "defaultTreatment": "false",
                    "rules": [
                      {"conditionLogic":"and","treatment":"true","conditions":[
                        {"attribute":"frontegg.tenantId","negate":false,"op":"in_list","value":{"list":["tenant-A"]}}
                      ]}
                    ]
                  }
                }
              },
              "plans": {},
              "permissions": {}
            }
        """.trimIndent()
        assertTrue(checkFeature(json, "alpha", mapOf("tenantId" to "tenant-A")).isEntitled)
        assertFalse(checkFeature(json, "alpha", mapOf("tenantId" to "tenant-B")).isEntitled)
    }

    // MARK: - Permissions

    @Test
    fun `permission wildcard match without linked features grants directly`() {
        val json = """
            {
              "features": {},
              "plans": {},
              "permissions": { "fe.secure.*": true }
            }
        """.trimIndent()
        assertTrue(checkPermission(json, "fe.secure.read.users").isEntitled)
    }

    @Test
    fun `permission linked to denied feature is denied even though wildcard matches`() {
        // The customer's most likely shape: server grants permission "fe.secure.*"
        // but it's gated on the "sso" feature, which is denied by its plan.
        val json = """
            {
              "features": {
                "sso": {
                  "planIds": ["plan-no-sso"],
                  "expireTime": null,
                  "linkedPermissions": ["fe.secure.read.samlDefaultRoles"]
                }
              },
              "plans": { "plan-no-sso": { "defaultTreatment": "false" } },
              "permissions": { "fe.secure.*": true }
            }
        """.trimIndent()
        val result = checkPermission(json, "fe.secure.read.samlDefaultRoles")
        assertFalse("Granted permission tied to a denied feature must be denied; was $result", result.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_FEATURE, result.justification)
    }

    @Test
    fun `permission missing from response entirely yields MISSING_PERMISSION`() {
        val json = """{"features":{},"plans":{},"permissions":{}}"""
        val result = checkPermission(json, "fe.unknown")
        assertFalse(result.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_PERMISSION, result.justification)
    }
}
