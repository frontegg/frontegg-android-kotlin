package com.frontegg.android.entitlements

import com.frontegg.android.models.NotEntitledJustification

/**
 * Port of `evaluateIsEntitledToFeature` from
 * `@frontegg/entitlements-javascript-commons`, including its three-evaluator chain
 * with the same priorities:
 *
 *   1. [directEntitlementEvaluator] — `expireTime != null`, not expired
 *   2. [featureFlagEvaluator]        — feature-flag on/off + rules + defaultTreatment
 *   3. [planTargetingRulesEvaluator] — for each linked plan, rules then defaultTreatment
 *
 * First evaluator returning `isEntitled = true` wins. Otherwise the result is
 * `BUNDLE_EXPIRED` if any evaluator reported it, else `MISSING_FEATURE` — matches
 * `getResult` in web's `entitlement-results.utils.ts`.
 */
object IsEntitledToFeature {

    fun evaluate(
        featureKey: String,
        context: UserEntitlementsContext?,
        attributes: Attributes = Attributes()
    ): EntitlementResult {
        if (context == null) {
            return EntitlementResult(
                isEntitled = false,
                justification = NotEntitledJustification.MISSING_FEATURE
            )
        }
        val prepared = AttributesPreparer.prepare(attributes)
        val results = mutableListOf<EntitlementResult>()
        for (evaluator in featureEvaluators) {
            results.add(evaluator(featureKey, context, prepared))
            if (!shouldContinue(results)) break
        }
        return aggregate(results)
    }

    private val featureEvaluators: List<(String, UserEntitlementsContext, Map<String, Any?>) -> EntitlementResult> =
        listOf(::directEntitlementEvaluator, ::featureFlagEvaluator, ::planTargetingRulesEvaluator)

    /**
     * Mirrors `direct-entitlement.evaluator.ts`.
     *   * `expireTime` null  → feature is not directly assigned → `MISSING_FEATURE`
     *   * `expireTime` = -1  → permanently assigned → entitled
     *   * `expireTime` > now → assigned, not yet expired → entitled
     *   * `expireTime` < now → bundle expired → `BUNDLE_EXPIRED`
     */
    internal fun directEntitlementEvaluator(
        featureKey: String,
        context: UserEntitlementsContext,
        @Suppress("UNUSED_PARAMETER") attributes: Map<String, Any?>
    ): EntitlementResult {
        val feature = context.features[featureKey]
        if (feature != null && feature.expireTime != null) {
            val expired = feature.expireTime != FeatureDetail.NO_EXPIRATION_TIME &&
                    feature.expireTime < System.currentTimeMillis()
            if (!expired) return EntitlementResult(isEntitled = true)
            return EntitlementResult(
                isEntitled = false,
                justification = NotEntitledJustification.BUNDLE_EXPIRED
            )
        }
        return EntitlementResult(
            isEntitled = false,
            justification = NotEntitledJustification.MISSING_FEATURE
        )
    }

    internal fun featureFlagEvaluator(
        featureKey: String,
        context: UserEntitlementsContext,
        attributes: Map<String, Any?>
    ): EntitlementResult {
        val feature = context.features[featureKey] ?: return missingFeature()
        val flag = feature.featureFlag ?: return missingFeature()
        val treatment = FeatureFlagEvaluator.evaluate(flag, attributes)
        return if (treatment == Treatment.TRUE) {
            EntitlementResult(isEntitled = true)
        } else {
            missingFeature()
        }
    }

    internal fun planTargetingRulesEvaluator(
        featureKey: String,
        context: UserEntitlementsContext,
        attributes: Map<String, Any?>
    ): EntitlementResult {
        val feature = context.features[featureKey] ?: return missingFeature()
        if (feature.planIds.isEmpty()) return missingFeature()
        for (planId in feature.planIds) {
            val plan = context.plans[planId] ?: continue
            val treatment = PlanEvaluator.evaluate(plan, attributes)
            if (treatment == Treatment.TRUE) {
                return EntitlementResult(isEntitled = true)
            }
        }
        return missingFeature()
    }

    private fun missingFeature() = EntitlementResult(
        isEntitled = false,
        justification = NotEntitledJustification.MISSING_FEATURE
    )

    /**
     * Mirrors `getResult` from `entitlement-results.utils.ts`. First `isEntitled=true`
     * wins; otherwise `BUNDLE_EXPIRED` outranks `MISSING_FEATURE` so the host app sees
     * a more actionable justification when an expired bundle is the closest match.
     */
    internal fun aggregate(results: List<EntitlementResult>): EntitlementResult {
        var anyExpired = false
        for (r in results) {
            if (r.isEntitled) return r
            if (r.justification == NotEntitledJustification.BUNDLE_EXPIRED) anyExpired = true
        }
        return EntitlementResult(
            isEntitled = false,
            justification = if (anyExpired) NotEntitledJustification.BUNDLE_EXPIRED
            else NotEntitledJustification.MISSING_FEATURE
        )
    }

    /** Mirrors `shouldContinue` — stop the chain as soon as any evaluator says yes. */
    internal fun shouldContinue(results: List<EntitlementResult>): Boolean =
        results.all { !it.isEntitled }
}
