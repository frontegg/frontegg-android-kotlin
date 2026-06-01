package com.frontegg.android.entitlements

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanAndFeatureFlagEvaluatorTest {

    private val ruleMatchingTenantA = Rule(
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

    @Test
    fun `plan defaultTreatment applies when no rules match`() {
        val plan = Plan(
            defaultTreatment = Treatment.FALSE,
            rules = listOf(ruleMatchingTenantA)
        )
        // No tenantId in attributes — the rule doesn't fire, falls back to default.
        assertEquals(Treatment.FALSE, PlanEvaluator.evaluate(plan, emptyMap()))
        // Wrong tenant — same result.
        assertEquals(
            Treatment.FALSE,
            PlanEvaluator.evaluate(plan, mapOf("frontegg.tenantId" to "tenant-B"))
        )
    }

    @Test
    fun `plan rule overrides defaultTreatment when conditions match`() {
        val plan = Plan(
            defaultTreatment = Treatment.FALSE,
            rules = listOf(ruleMatchingTenantA)
        )
        assertEquals(
            Treatment.TRUE,
            PlanEvaluator.evaluate(plan, mapOf("frontegg.tenantId" to "tenant-A"))
        )
    }

    @Test
    fun `plan with no rules just returns defaultTreatment`() {
        // The exact scenario from FR-24821: tenant B's SSO plan has
        // defaultTreatment "false" and no overriding rules → user is NOT entitled.
        val plan = Plan(defaultTreatment = Treatment.FALSE, rules = null)
        assertEquals(Treatment.FALSE, PlanEvaluator.evaluate(plan, emptyMap()))
    }

    @Test
    fun `featureFlag off returns offTreatment regardless of rules`() {
        val flag = FeatureFlag(
            on = false,
            offTreatment = Treatment.FALSE,
            defaultTreatment = Treatment.TRUE,
            rules = listOf(ruleMatchingTenantA)
        )
        assertEquals(
            Treatment.FALSE,
            FeatureFlagEvaluator.evaluate(flag, mapOf("frontegg.tenantId" to "tenant-A"))
        )
    }

    @Test
    fun `featureFlag on uses rules then defaultTreatment`() {
        val flag = FeatureFlag(
            on = true,
            offTreatment = Treatment.FALSE,
            defaultTreatment = Treatment.FALSE,
            rules = listOf(ruleMatchingTenantA)
        )
        assertEquals(
            Treatment.TRUE,
            FeatureFlagEvaluator.evaluate(flag, mapOf("frontegg.tenantId" to "tenant-A"))
        )
        assertEquals(Treatment.FALSE, FeatureFlagEvaluator.evaluate(flag, emptyMap()))
    }
}
