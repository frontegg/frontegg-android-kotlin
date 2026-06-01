package com.frontegg.android.entitlements

import com.frontegg.android.entitlements.operations.OperationResolver
import com.frontegg.android.entitlements.operations.SanitizerResolver

/**
 * Port of `createConditionEvaluator` from
 * `@frontegg/entitlements-javascript-commons`.
 *
 * A condition evaluates true iff:
 *   * the condition's value payload sanitizes for its operation,
 *   * the attribute named by `condition.attribute` exists in the prepared attribute
 *     map,
 *   * the operation's predicate returns true for that attribute.
 *
 * `negate = true` inverts the result. If sanitization fails, the attribute is
 * missing, or the operation handler can't be resolved, the condition is `false`
 * regardless of `negate` — matches web (which short-circuits to `false` before
 * applying negate).
 */
object ConditionEvaluator {
    fun evaluate(condition: Condition, attributes: Map<String, Any?>): Boolean {
        val payload = SanitizerResolver.sanitize(condition.op, condition.value) ?: return false
        val handler = OperationResolver.resolve(condition.op, payload) ?: return false

        // attributes.containsKey is significant — a present-but-null attribute should
        // still go through the handler (matching the JS `attributes[key] !== undefined`
        // pre-check), but an absent attribute short-circuits to false.
        if (!attributes.containsKey(condition.attribute)) return false
        val value = attributes[condition.attribute] ?: return false

        val raw = handler(value)
        return if (condition.negate) !raw else raw
    }
}
