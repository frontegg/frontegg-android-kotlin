package com.frontegg.android.services

import com.frontegg.android.models.Entitlement
import com.frontegg.android.models.EntitlementState

class EntitlementsService(
    private val api: Api,
    private val enabled: Boolean
) {
    @Volatile
    private var _state: EntitlementState = EntitlementState.empty

    val state: EntitlementState get() = _state

    fun load(accessToken: String): Boolean {
        if (!enabled) return false
        val newState = api.getUserEntitlements(accessTokenOverride = accessToken) ?: return false
        _state = newState
        return true
    }

    fun clear() {
        _state = EntitlementState.empty
    }

    fun checkFeature(featureKey: String): Entitlement {
        if (!enabled) return Entitlement(isEntitled = false, justification = "ENTITLEMENTS_DISABLED")
        val entitled = _state.featureKeys.contains(featureKey)
        return Entitlement(isEntitled = entitled, justification = if (entitled) null else "MISSING_FEATURE")
    }

    fun checkPermission(permissionKey: String): Entitlement {
        if (!enabled) return Entitlement(isEntitled = false, justification = "ENTITLEMENTS_DISABLED")
        val entitled = _state.permissionKeys.contains(permissionKey)
        return Entitlement(isEntitled = entitled, justification = if (entitled) null else "MISSING_PERMISSION")
    }
}
