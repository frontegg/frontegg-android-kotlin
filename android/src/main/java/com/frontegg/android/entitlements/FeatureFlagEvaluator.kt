package com.frontegg.android.entitlements

/**
 * Port of `evaluateFeatureFlag` from `@frontegg/entitlements-javascript-commons`.
 *
 *   * flag off  → `offTreatment`
 *   * flag on   → first matching rule's treatment, else `defaultTreatment`
 */
object FeatureFlagEvaluator {
    fun evaluate(flag: FeatureFlag, attributes: Map<String, Any?>): Treatment {
        if (!flag.on) return flag.offTreatment
        val matching = flag.rules.orEmpty().firstOrNull { rule ->
            RuleEvaluator.evaluate(rule, attributes) == RuleEvaluationResult.TREATABLE
        }
        return matching?.treatment ?: flag.defaultTreatment
    }
}
