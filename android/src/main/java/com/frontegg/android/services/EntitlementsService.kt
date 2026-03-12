package com.frontegg.android.services

import com.frontegg.android.models.Entitlement
import com.frontegg.android.models.EntitlementState

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

    fun checkFeature(featureKey: String): Entitlement {
        if (!enabled) return Entitlement(isEntitled = false, justification = "ENTITLEMENTS_DISABLED")
        if (!_hasLoadedOnce) return Entitlement(isEntitled = false, justification = "ENTITLEMENTS_NOT_LOADED")
        val entitled = _state.featureKeys.contains(featureKey)
        return Entitlement(isEntitled = entitled, justification = if (entitled) null else "MISSING_FEATURE")
    }

    fun checkPermission(permissionKey: String): Entitlement {
        if (!enabled) return Entitlement(isEntitled = false, justification = "ENTITLEMENTS_DISABLED")
        if (!_hasLoadedOnce) return Entitlement(isEntitled = false, justification = "ENTITLEMENTS_NOT_LOADED")
        val entitled = _state.permissionKeys.contains(permissionKey)
        return Entitlement(isEntitled = entitled, justification = if (entitled) null else "MISSING_PERMISSION")
    }
}
