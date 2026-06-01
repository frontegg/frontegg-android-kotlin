package com.frontegg.android.entitlements

/**
 * Port of `evaluatePlan` from `@frontegg/entitlements-javascript-commons`.
 *
 * Rules are checked in document order. The first rule whose conditions all match
 * produces the plan's treatment; if no rule matches, the plan falls back to
 * [Plan.defaultTreatment]. This is the layer that turned `defaultTreatment: "false"`
 * into "not entitled to SSO" on web in FR-24821.
 */
object PlanEvaluator {
    fun evaluate(plan: Plan, attributes: Map<String, Any?>): Treatment {
        val matching = plan.rules.orEmpty().firstOrNull { rule ->
            RuleEvaluator.evaluate(rule, attributes) == RuleEvaluationResult.TREATABLE
        }
        return matching?.treatment ?: plan.defaultTreatment
    }
}
