package com.frontegg.android.services

import android.util.Log
import com.frontegg.android.entitlements.Attributes
import com.frontegg.android.entitlements.AttributesPreparer
import com.frontegg.android.entitlements.EntitlementResult
import com.frontegg.android.entitlements.IsEntitledToFeature
import com.frontegg.android.entitlements.IsEntitledToPermission
import com.frontegg.android.models.Entitlement
import com.frontegg.android.models.EntitlementState
import com.frontegg.android.models.NotEntitledJustification

/**
 * In-memory cache around the `/frontegg/entitlements/api/v2/user-entitlements`
 * response. Holds the structured [com.frontegg.android.entitlements.UserEntitlementsContext]
 * (features + plans + permissions + per-feature flags/rules/conditions) and routes
 * the public `checkFeature` / `checkPermission` calls through the
 * [IsEntitledToFeature] / [IsEntitledToPermission] evaluator chains — the same
 * algorithm `@frontegg/entitlements-javascript-commons` runs on web.
 *
 * Pre-fix, this service stored only the feature/permission key sets and treated
 * "key present in `features`" as "user is entitled to that feature", ignoring the
 * linked plans' `defaultTreatment`, expiry, rules, and feature flags — the
 * decision-logic gap that produced FR-24821.
 */
class EntitlementsService(
    private val api: Api,
    private val enabled: Boolean
) {
    @Volatile
    private var _state: EntitlementState = EntitlementState.empty

    @Volatile
    private var _hasLoadedOnce: Boolean = false

    val state: EntitlementState get() = _state

    /** True after at least one successful [load]; false after [clear]. Used to distinguish "never loaded" from "loaded with zero entitlements". */
    val hasLoadedOnce: Boolean get() = _hasLoadedOnce

    fun load(accessToken: String): Boolean {
        if (!enabled) return false
        val newState = api.getUserEntitlements(accessTokenOverride = accessToken) ?: return false
        synchronized(this) {
            _state = newState
            _hasLoadedOnce = true
        }
        return true
    }

    fun clear() {
        synchronized(this) {
            _state = EntitlementState.empty
            _hasLoadedOnce = false
        }
    }

    /**
     * Evaluates `featureKey` against the cached [com.frontegg.android.entitlements.UserEntitlementsContext]
     * using the full decision chain (direct entitlement + feature flag + plan
     * targeting rules). [attributes] carries JWT claims (and any host-app custom
     * attributes) used by rule conditions — see
     * [com.frontegg.android.entitlements.AttributesPreparer].
     */
    fun checkFeature(featureKey: String, attributes: Attributes = Attributes()): Entitlement {
        if (!enabled) return Entitlement(
            isEntitled = false,
            justification = NotEntitledJustification.ENTITLEMENTS_DISABLED
        )
        if (!_hasLoadedOnce) return Entitlement(
            isEntitled = false,
            justification = NotEntitledJustification.ENTITLEMENTS_NOT_LOADED
        )
        val result = IsEntitledToFeature.evaluate(featureKey, _state.context, attributes)
        logCheckTrace(kind = "feature", key = featureKey, attributes = attributes, result = result)
        return Entitlement(isEntitled = result.isEntitled, justification = result.justification)
    }

    fun checkPermission(permissionKey: String, attributes: Attributes = Attributes()): Entitlement {
        if (!enabled) return Entitlement(
            isEntitled = false,
            justification = NotEntitledJustification.ENTITLEMENTS_DISABLED
        )
        if (!_hasLoadedOnce) return Entitlement(
            isEntitled = false,
            justification = NotEntitledJustification.ENTITLEMENTS_NOT_LOADED
        )
        val result = IsEntitledToPermission.evaluate(permissionKey, _state.context, attributes)
        logCheckTrace(kind = "permission", key = permissionKey, attributes = attributes, result = result)
        return Entitlement(isEntitled = result.isEntitled, justification = result.justification)
    }

    /**
     * Diagnostic trace for `getFeatureEntitlements` / `getPermissionEntitlements`.
     * Shows the prepared attribute bag (so the caller can confirm
     * `frontegg.tenantId` / `frontegg.email` / etc. actually came through from the
     * JWT) and the verdict. Tagged `[ENT-DEBUG]` for easy logcat filtering.
     * Sensitive claims (email, tenantId) only appear in your own test app's
     * debug log, never leaving the device unless you copy them.
     */
    private fun logCheckTrace(
        kind: String,
        key: String,
        attributes: Attributes,
        result: EntitlementResult
    ) {
        val prepared = AttributesPreparer.prepare(attributes)
        val attributeSummary = prepared.entries
            .sortedBy { it.key }
            .joinToString(", ") { "${it.key}=${it.value ?: "null"}" }
        val ctx = _state.context
        val contextStatus = if (ctx != null) {
            "features=${ctx.features.size} plans=${ctx.plans.size} permissions=${ctx.permissions.size}"
        } else {
            "context=null"
        }
        Log.i(TAG, "[ENT-DEBUG] check$kind(\"$key\") → isEntitled=${result.isEntitled} justification=${result.justification ?: "null"}")
        Log.i(TAG, "[ENT-DEBUG]   context: $contextStatus")
        Log.i(TAG, "[ENT-DEBUG]   attributes: {$attributeSummary}")
        // Print the relevant slice of the context for THIS feature/permission so
        // the trace is self-contained — no need to cross-reference the load log.
        if (kind == "feature" && ctx != null) {
            val feature = ctx.features[key]
            if (feature != null) {
                val planIdsStr = if (feature.planIds.isEmpty()) "[]" else "[${feature.planIds.joinToString(",")}]"
                val expireStr = feature.expireTime?.toString() ?: "null"
                val flagStr = feature.featureFlag?.let {
                    "on=${it.on} off=${it.offTreatment.wire} default=${it.defaultTreatment.wire} rules=${it.rules?.size ?: 0}"
                } ?: "null"
                Log.i(TAG, "[ENT-DEBUG]   feature slice: planIds=$planIdsStr expireTime=$expireStr featureFlag=$flagStr linkedPermissions=${feature.linkedPermissions}")
                for (planId in feature.planIds) {
                    val plan = ctx.plans[planId]
                    if (plan != null) {
                        Log.i(TAG, "[ENT-DEBUG]     linked plan[$planId]: defaultTreatment=${plan.defaultTreatment.wire} rules=${plan.rules?.size ?: 0}")
                    } else {
                        Log.i(TAG, "[ENT-DEBUG]     linked plan[$planId]: MISSING from plans map")
                    }
                }
            } else {
                Log.i(TAG, "[ENT-DEBUG]   feature[$key]: not present in catalog")
            }
        }
    }

    companion object {
        private const val TAG = "EntitlementsService"
    }
}
