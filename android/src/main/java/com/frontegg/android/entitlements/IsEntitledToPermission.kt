package com.frontegg.android.entitlements

import com.frontegg.android.models.NotEntitledJustification

/**
 * Port of `evaluateIsEntitledToPermissions` from
 * `@frontegg/entitlements-javascript-commons`.
 *
 * Two-step check:
 *   1. Wildcard-match the permission key against the granted permissions. If no
 *      granted key matches, the user is missing the permission — short-circuit with
 *      `MISSING_PERMISSION`.
 *   2. If the permission isn't linked to any feature, the wildcard match is the
 *      whole answer — entitled. Otherwise re-run the feature evaluator chain for
 *      every linked feature; if any feature is entitled, the permission is entitled
 *      too. Otherwise the aggregated feature justification (typically `MISSING_FEATURE`
 *      or `BUNDLE_EXPIRED`) is returned.
 */
object IsEntitledToPermission {

    fun evaluate(
        permissionKey: String,
        context: UserEntitlementsContext?,
        attributes: Attributes = Attributes()
    ): EntitlementResult {
        if (context == null) {
            return EntitlementResult(
                isEntitled = false,
                justification = NotEntitledJustification.MISSING_PERMISSION
            )
        }
        if (!PermissionMatcher.hasPermission(context.permissions, permissionKey)) {
            return EntitlementResult(
                isEntitled = false,
                justification = NotEntitledJustification.MISSING_PERMISSION
            )
        }
        val linkedFeatures = context.features.entries
            .filter { (_, detail) -> detail.linkedPermissions.contains(permissionKey) }
            .map { it.key }

        if (linkedFeatures.isEmpty()) return EntitlementResult(isEntitled = true)

        val results = mutableListOf<EntitlementResult>()
        for (featureKey in linkedFeatures) {
            results.add(IsEntitledToFeature.evaluate(featureKey, context, attributes))
            if (!IsEntitledToFeature.shouldContinue(results)) break
        }
        return IsEntitledToFeature.aggregate(results)
    }
}
