package com.frontegg.android.models

/**
 * Result returned from [com.frontegg.android.FronteggAuth.getFeatureEntitlements] and
 * [com.frontegg.android.FronteggAuth.getPermissionEntitlements].
 *
 * [justification] mirrors the canonical values used by the web/JS SDKs
 * (`@frontegg/entitlements-javascript-commons`) and is one of the strings declared in
 * [NotEntitledJustification] — kept as a plain [String] so existing host-app `when`
 * branches on the old values keep compiling.
 */
data class Entitlement(
    val isEntitled: Boolean,
    val justification: String? = null
)

/**
 * Canonical justification codes mirrored from
 * `@frontegg/entitlements-javascript-commons` plus the mobile-specific cases the SDK
 * has historically returned.
 *
 * The web SDK exposes `MISSING_FEATURE`, `MISSING_PERMISSION`, and `BUNDLE_EXPIRED`.
 * Mobile additionally needs `NOT_AUTHENTICATED`, `ENTITLEMENTS_DISABLED`, and
 * `ENTITLEMENTS_NOT_LOADED` to describe SDK-side preconditions that don't exist on web
 * (where the entitlements context is always available synchronously after login).
 */
object NotEntitledJustification {
    const val NOT_AUTHENTICATED = "NOT_AUTHENTICATED"
    const val ENTITLEMENTS_DISABLED = "ENTITLEMENTS_DISABLED"
    const val ENTITLEMENTS_NOT_LOADED = "ENTITLEMENTS_NOT_LOADED"
    const val MISSING_FEATURE = "MISSING_FEATURE"
    const val MISSING_PERMISSION = "MISSING_PERMISSION"
    const val BUNDLE_EXPIRED = "BUNDLE_EXPIRED"
}

/**
 * Cached snapshot of the most recent `/frontegg/entitlements/api/v2/user-entitlements`
 * response.
 *
 * [featureKeys] and [permissionKeys] are kept for backwards compatibility with host
 * apps that read them directly to render counts or debug overlays. They mirror the
 * old SDK behavior — every feature key returned in the catalog and every permission
 * key with `value = true`. **They are NOT the verdict** — they don't account for
 * plan `defaultTreatment`, feature flags, rules, or expiry. The verdict goes through
 * [com.frontegg.android.entitlements.IsEntitledToFeature] /
 * [com.frontegg.android.entitlements.IsEntitledToPermission], driven off [context].
 *
 * [context] is the structured form used by the evaluators — `null` when entitlements
 * haven't been loaded yet, when the load failed, or when the SDK was constructed with
 * `entitlementsEnabled = false`.
 */
data class EntitlementState(
    val featureKeys: Set<String>,
    val permissionKeys: Set<String>,
    val context: com.frontegg.android.entitlements.UserEntitlementsContext? = null
) {
    companion object {
        val empty = EntitlementState(
            featureKeys = emptySet(),
            permissionKeys = emptySet(),
            context = null
        )
    }
}

typealias CustomAttributes = Map<String, Any?>

sealed class EntitledToOptions {
    data class FeatureKey(val key: String) : EntitledToOptions()
    data class PermissionKey(val key: String) : EntitledToOptions()
}
