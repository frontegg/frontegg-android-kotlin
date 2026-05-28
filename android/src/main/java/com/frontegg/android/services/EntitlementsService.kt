package com.frontegg.android.services

import com.frontegg.android.entitlements.Attributes
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
        return Entitlement(isEntitled = result.isEntitled, justification = result.justification)
    }
}
