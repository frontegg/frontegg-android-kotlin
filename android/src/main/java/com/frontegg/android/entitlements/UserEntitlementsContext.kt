package com.frontegg.android.entitlements

/**
 * Faithful port of the `UserEntitlementsContext` shape from
 * `@frontegg/entitlements-javascript-commons` (the canonical evaluator the web SDK
 * uses). Mirrors the v2 `/frontegg/entitlements/api/v2/user-entitlements` response.
 *
 * Pre-fix the mobile SDK only kept the `features` map's *keys* and discarded
 * everything else — that's why `getFeatureEntitlements("sso")` returned
 * `isEntitled = true` regardless of plan `defaultTreatment`, expiry, or feature
 * flags. Storing the full structure lets us run the same evaluator chain as web.
 */
data class UserEntitlementsContext(
    val features: Map<String, FeatureDetail>,
    val plans: Map<String, Plan>,
    val permissions: Map<String, Boolean>
)

data class FeatureDetail(
    val planIds: List<String>,
    /**
     * `null` when this feature is not directly assigned to the user (only reachable via
     * a plan or feature flag); [NO_EXPIRATION_TIME] when assigned permanently; otherwise
     * an epoch-millis timestamp.
     */
    val expireTime: Long?,
    val linkedPermissions: List<String>,
    val featureFlag: FeatureFlag?
) {
    companion object {
        /**
         * Sentinel for "directly assigned, never expires". Matches the web SDK's
         * `NO_EXPIRATION_TIME` constant.
         */
        const val NO_EXPIRATION_TIME: Long = -1L
    }
}

data class Plan(
    val defaultTreatment: Treatment,
    val rules: List<Rule>? = null
)

data class FeatureFlag(
    val on: Boolean,
    val offTreatment: Treatment,
    val defaultTreatment: Treatment,
    val rules: List<Rule>? = null
)

data class Rule(
    /**
     * Web only supports `ConditionLogicEnum.And` today; preserved here so future
     * `or`/etc. additions show up explicitly rather than as a silent default.
     */
    val conditionLogic: ConditionLogic,
    val conditions: List<Condition>,
    val treatment: Treatment
)

enum class ConditionLogic(val wire: String) {
    AND("and");

    companion object {
        fun fromWire(value: String?): ConditionLogic? =
            values().firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

enum class Treatment(val wire: String) {
    TRUE("true"),
    FALSE("false");

    companion object {
        fun fromWire(value: String?): Treatment? =
            values().firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

data class Condition(
    val attribute: String,
    val negate: Boolean,
    val op: Operation,
    /**
     * Raw value shape varies by operation kind (e.g. `{"string": "abc"}` for `matches`,
     * `{"list": [...]}` for `in_list`, `{"number": 42}` for numeric ops,
     * `{"start": ..., "end": ...}` for ranges, `{"boolean": true}` for `is`,
     * `{"date": "..."}` for date ops). The matching sanitizer in
     * [com.frontegg.android.entitlements.operations.SanitizerResolver] is responsible
     * for narrowing to the expected payload.
     */
    val value: Map<String, Any?>
)

/**
 * Mirrors `OperationEnum` from `@frontegg/entitlements-javascript-commons`. Wire
 * values match the JSON the server emits, e.g. `"starts_with"`, `"between_numeric"`.
 */
enum class Operation(val wire: String) {
    // String
    IN_LIST("in_list"),
    STARTS_WITH("starts_with"),
    ENDS_WITH("ends_with"),
    CONTAINS("contains"),
    MATCHES("matches"),

    // Numeric
    EQUAL("equal"),
    GREATER_THAN("greater_than"),
    GREATER_THAN_EQUAL("greater_than_equal"),
    LESSER_THAN("lower_than"),
    LESSER_THAN_EQUAL("lower_than_equal"),
    BETWEEN_NUMERIC("between_numeric"),

    // Boolean
    IS("is"),

    // Date
    ON("on"),
    BETWEEN_DATE("between_date"),
    ON_OR_AFTER("on_or_after"),
    ON_OR_BEFORE("on_or_before");

    companion object {
        fun fromWire(value: String?): Operation? =
            values().firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

/**
 * Pair of [isEntitled] flag plus, when negative, a [com.frontegg.android.models.NotEntitledJustification]
 * code.
 */
data class EntitlementResult(
    val isEntitled: Boolean,
    val justification: String? = null
)

data class Attributes(
    val custom: Map<String, Any?>? = null,
    val jwt: Map<String, Any?>? = null
)
