package com.frontegg.android.models

data class Entitlement(
    val isEntitled: Boolean,
    val justification: String? = null
)

data class EntitlementState(
    val featureKeys: Set<String>,
    val permissionKeys: Set<String>
) {
    companion object {
        val empty = EntitlementState(featureKeys = emptySet(), permissionKeys = emptySet())
    }
}

typealias CustomAttributes = Map<String, Any?>

sealed class EntitledToOptions {
    data class FeatureKey(val key: String) : EntitledToOptions()
    data class PermissionKey(val key: String) : EntitledToOptions()
}
