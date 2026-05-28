package com.frontegg.android.entitlements

import com.frontegg.android.models.NotEntitledJustification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end tests for the feature evaluator chain.
 *
 * Tests are organized by the chain step that should "win":
 *   * `direct` — the feature is directly assigned with a non-expired bundle.
 *   * `feature-flag` — direct doesn't apply (no expireTime); the feature flag's
 *     treatment is True.
 *   * `plan` — direct and feature flag don't apply; one of the linked plans
 *     evaluates True.
 *   * `denied` — every evaluator returns false; we assert on the justification.
 *
 * The FR-24821 customer reproduction lives in [`fr_24821 sso with defaultTreatment false is not entitled`].
 */
class IsEntitledToFeatureTest {

    @Test
    fun `null context yields MISSING_FEATURE`() {
        val result = IsEntitledToFeature.evaluate("anything", null)
        assertFalse(result.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_FEATURE, result.justification)
    }

    @Test
    fun `direct entitlement with NO_EXPIRATION_TIME wins`() {
        val ctx = UserEntitlementsContext(
            features = mapOf(
                "alpha" to FeatureDetail(
                    planIds = emptyList(),
                    expireTime = FeatureDetail.NO_EXPIRATION_TIME,
                    linkedPermissions = emptyList(),
                    featureFlag = null
                )
            ),
            plans = emptyMap(),
            permissions = emptyMap()
        )
        val result = IsEntitledToFeature.evaluate("alpha", ctx)
        assertTrue("$result", result.isEntitled)
        assertEquals(null, result.justification)
    }

    @Test
    fun `direct entitlement reports BUNDLE_EXPIRED when expireTime in the past`() {
        val ctx = UserEntitlementsContext(
            features = mapOf(
                "alpha" to FeatureDetail(
                    planIds = emptyList(),
                    // 1970-01-02 — guaranteed in the past on any test runner.
                    expireTime = 86_400_000L,
                    linkedPermissions = emptyList(),
                    featureFlag = null
                )
            ),
            plans = emptyMap(),
            permissions = emptyMap()
        )
        val result = IsEntitledToFeature.evaluate("alpha", ctx)
        assertFalse(result.isEntitled)
        assertEquals(NotEntitledJustification.BUNDLE_EXPIRED, result.justification)
    }

    @Test
    fun `feature-flag on with truthy defaultTreatment grants access when direct does not`() {
        val ctx = UserEntitlementsContext(
            features = mapOf(
                "alpha" to FeatureDetail(
                    planIds = emptyList(),
                    expireTime = null,
                    linkedPermissions = emptyList(),
                    featureFlag = FeatureFlag(
                        on = true,
                        offTreatment = Treatment.FALSE,
                        defaultTreatment = Treatment.TRUE,
                        rules = null
                    )
                )
            ),
            plans = emptyMap(),
            permissions = emptyMap()
        )
        val result = IsEntitledToFeature.evaluate("alpha", ctx)
        assertTrue("$result", result.isEntitled)
    }

    @Test
    fun `plan-targeting evaluator grants when a plan rule matches the JWT tenantId`() {
        val ctx = UserEntitlementsContext(
            features = mapOf(
                "sso" to FeatureDetail(
                    planIds = listOf("plan-1"),
                    expireTime = null,
                    linkedPermissions = emptyList(),
                    featureFlag = null
                )
            ),
            plans = mapOf(
                "plan-1" to Plan(
                    defaultTreatment = Treatment.FALSE,
                    rules = listOf(
                        Rule(
                            conditionLogic = ConditionLogic.AND,
                            conditions = listOf(
                                Condition(
                                    attribute = "frontegg.tenantId",
                                    negate = false,
                                    op = Operation.IN_LIST,
                                    value = mapOf("list" to listOf("tenant-A"))
                                )
                            ),
                            treatment = Treatment.TRUE
                        )
                    )
                )
            ),
            permissions = emptyMap()
        )
        val attrs = Attributes(jwt = mapOf("tenantId" to "tenant-A"))
        val result = IsEntitledToFeature.evaluate("sso", ctx, attrs)
        assertTrue("$result", result.isEntitled)
    }

    @Test
    fun `fr_24821 sso with defaultTreatment false is not entitled`() {
        // The exact customer reproduction from FR-24821 / Yonatan's Slack thread:
        //
        // The /user-entitlements response lists "sso" in the features catalog with a
        // linked plan that has `defaultTreatment: "false"` (and no overriding rules
        // for the current tenant). Web evaluates this correctly as "not entitled".
        // Pre-fix the mobile SDK only looked at the features map's keys and reported
        // isEntitled=true — that's the bug this whole PR closes.
        val ctx = UserEntitlementsContext(
            features = mapOf(
                "sso" to FeatureDetail(
                    planIds = listOf("ID_1"),
                    expireTime = null,
                    linkedPermissions = emptyList(),
                    featureFlag = null
                )
            ),
            plans = mapOf(
                "ID_1" to Plan(defaultTreatment = Treatment.FALSE, rules = null)
            ),
            permissions = emptyMap()
        )
        // Even with a JWT-derived attribute bag, no rules → defaultTreatment "false"
        // → not entitled.
        val attrs = Attributes(jwt = mapOf("tenantId" to "tenant-without-sso"))
        val result = IsEntitledToFeature.evaluate("sso", ctx, attrs)
        assertFalse("$result", result.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_FEATURE, result.justification)
    }

    @Test
    fun `aggregate reports BUNDLE_EXPIRED if any evaluator hit expiry but none entitled`() {
        // Direct evaluator says BUNDLE_EXPIRED, plan/featureFlag evaluators say
        // MISSING_FEATURE. Aggregate prefers BUNDLE_EXPIRED — more actionable.
        val ctx = UserEntitlementsContext(
            features = mapOf(
                "alpha" to FeatureDetail(
                    planIds = emptyList(),
                    expireTime = 86_400_000L, // expired
                    linkedPermissions = emptyList(),
                    featureFlag = null
                )
            ),
            plans = emptyMap(),
            permissions = emptyMap()
        )
        val result = IsEntitledToFeature.evaluate("alpha", ctx)
        assertEquals(NotEntitledJustification.BUNDLE_EXPIRED, result.justification)
    }

    @Test
    fun `feature missing from catalog yields MISSING_FEATURE`() {
        val ctx = UserEntitlementsContext(
            features = emptyMap(),
            plans = emptyMap(),
            permissions = emptyMap()
        )
        val result = IsEntitledToFeature.evaluate("unknown", ctx)
        assertFalse(result.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_FEATURE, result.justification)
    }
}
