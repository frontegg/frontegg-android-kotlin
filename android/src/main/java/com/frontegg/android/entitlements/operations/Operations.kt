package com.frontegg.android.entitlements.operations

import com.frontegg.android.entitlements.Operation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Port of the operations + sanitizers matrix from
 * `@frontegg/entitlements-javascript-commons`. Two-step protocol:
 *
 *   1. [SanitizerResolver.sanitize] narrows the raw `condition.value` JSON object
 *      (e.g. `{"list":[...]}`, `{"number":42}`, `{"date":"..."}`) to a strongly-typed
 *      payload. If the payload doesn't match the operation, sanitization fails and the
 *      condition is treated as `false` (matches web's behavior — invalid value blocks
 *      the rule).
 *   2. [OperationResolver.resolve] returns a handler `(attribute) -> Boolean` that
 *      runs the operation against an attribute pulled out of the prepared attribute
 *      map. Type mismatches (e.g. operation expects String, attribute is Boolean)
 *      return `false`.
 */
internal sealed class SanitizedPayload {
    data class SingleString(val string: String) : SanitizedPayload()
    data class ListString(val list: List<String>) : SanitizedPayload()
    data class SingleNumber(val number: Double) : SanitizedPayload()
    data class NumericRange(val start: Double, val end: Double) : SanitizedPayload()
    data class SingleBoolean(val boolean: Boolean) : SanitizedPayload()
    data class SingleDate(val date: Date) : SanitizedPayload()
    data class DateRange(val start: Date, val end: Date) : SanitizedPayload()
}

internal object SanitizerResolver {
    fun sanitize(operation: Operation, value: Map<String, Any?>): SanitizedPayload? {
        return when (operation) {
            Operation.MATCHES -> sanitizeSingleString(value)
            Operation.CONTAINS,
            Operation.STARTS_WITH,
            Operation.ENDS_WITH,
            Operation.IN_LIST -> sanitizeListString(value)

            Operation.EQUAL,
            Operation.GREATER_THAN,
            Operation.GREATER_THAN_EQUAL,
            Operation.LESSER_THAN,
            Operation.LESSER_THAN_EQUAL -> sanitizeSingleNumber(value)

            Operation.BETWEEN_NUMERIC -> sanitizeNumericRange(value)

            Operation.IS -> sanitizeSingleBoolean(value)

            Operation.ON,
            Operation.ON_OR_AFTER,
            Operation.ON_OR_BEFORE -> sanitizeSingleDate(value)

            Operation.BETWEEN_DATE -> sanitizeDateRange(value)
        }
    }

    private fun sanitizeSingleString(v: Map<String, Any?>): SanitizedPayload? {
        val s = v["string"] as? String ?: return null
        return SanitizedPayload.SingleString(s)
    }

    private fun sanitizeListString(v: Map<String, Any?>): SanitizedPayload? {
        val raw = v["list"] as? List<*> ?: return null
        if (raw.any { it !is String }) return null
        @Suppress("UNCHECKED_CAST")
        return SanitizedPayload.ListString(raw as List<String>)
    }

    private fun sanitizeSingleNumber(v: Map<String, Any?>): SanitizedPayload? {
        val n = (v["number"] as? Number)?.toDouble() ?: return null
        return SanitizedPayload.SingleNumber(n)
    }

    private fun sanitizeNumericRange(v: Map<String, Any?>): SanitizedPayload? {
        val s = (v["start"] as? Number)?.toDouble() ?: return null
        val e = (v["end"] as? Number)?.toDouble() ?: return null
        return SanitizedPayload.NumericRange(s, e)
    }

    private fun sanitizeSingleBoolean(v: Map<String, Any?>): SanitizedPayload? {
        val b = v["boolean"] as? Boolean ?: return null
        return SanitizedPayload.SingleBoolean(b)
    }

    private fun sanitizeSingleDate(v: Map<String, Any?>): SanitizedPayload? {
        val d = coerceDate(v["date"]) ?: return null
        return SanitizedPayload.SingleDate(d)
    }

    private fun sanitizeDateRange(v: Map<String, Any?>): SanitizedPayload? {
        val s = coerceDate(v["start"]) ?: return null
        val e = coerceDate(v["end"]) ?: return null
        return SanitizedPayload.DateRange(s, e)
    }
}

/**
 * Type coercion helpers used by both [SanitizerResolver] (to parse condition payloads)
 * and [OperationResolver] (to coerce attribute values pulled from the JWT/custom map).
 *
 * Mirror web's strictness as closely as we can given JS doesn't distinguish Int/Long/
 * Double — every JSON number is a `number`. Kotlin attribute values may be Int, Long,
 * Float, or Double; we promote to Double for comparison.
 */
internal fun coerceNumber(value: Any?): Double? = when (value) {
    is Double -> value
    is Float -> value.toDouble()
    is Long -> value.toDouble()
    is Int -> value.toDouble()
    is Short -> value.toDouble()
    is Byte -> value.toDouble()
    is Number -> value.toDouble()
    else -> null
}

internal fun coerceDate(value: Any?): Date? = when (value) {
    null -> null
    is Date -> value
    is Number -> Date(value.toLong())
    is String -> parseIsoDate(value)
    else -> null
}

/**
 * Iterates over a fixed set of date format strings — covers the common ISO-8601
 * variants the server is likely to emit. Kept narrow on purpose: ambiguous formats
 * (e.g. `MM/DD/yyyy`) are not parsed so we don't accidentally invert the same date
 * between platforms.
 */
private fun parseIsoDate(input: String): Date? {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd"
    )
    for (p in patterns) {
        try {
            val fmt = SimpleDateFormat(p, Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.isLenient = false
            return fmt.parse(input)
        } catch (_: Exception) {
            // Try the next pattern.
        }
    }
    return null
}

internal object OperationResolver {
    /**
     * Returns a handler `(attribute) -> Boolean` for the given [operation] +
     * [payload]. Returns `null` if the (operation, payload) pair is mismatched, which
     * is treated as "condition is false" by [com.frontegg.android.entitlements.ConditionEvaluator].
     */
    fun resolve(operation: Operation, payload: SanitizedPayload): ((Any?) -> Boolean)? {
        return when (operation) {
            Operation.STARTS_WITH -> {
                val p = payload as? SanitizedPayload.ListString ?: return null
                { attr -> (attr as? String)?.let { a -> p.list.any { a.startsWith(it) } } ?: false }
            }
            Operation.ENDS_WITH -> {
                val p = payload as? SanitizedPayload.ListString ?: return null
                { attr -> (attr as? String)?.let { a -> p.list.any { a.endsWith(it) } } ?: false }
            }
            Operation.CONTAINS -> {
                val p = payload as? SanitizedPayload.ListString ?: return null
                { attr -> (attr as? String)?.let { a -> p.list.any { a.contains(it) } } ?: false }
            }
            Operation.IN_LIST -> {
                val p = payload as? SanitizedPayload.ListString ?: return null
                { attr -> (attr as? String)?.let { a -> p.list.contains(a) } ?: false }
            }
            Operation.MATCHES -> {
                val p = payload as? SanitizedPayload.SingleString ?: return null
                val regex = try {
                    Regex(p.string)
                } catch (_: Exception) {
                    return { false }
                }
                { attr -> (attr as? String)?.let { regex.containsMatchIn(it) } ?: false }
            }

            Operation.EQUAL -> numericPredicate(payload) { a, n -> a == n }
            Operation.GREATER_THAN -> numericPredicate(payload) { a, n -> a > n }
            Operation.GREATER_THAN_EQUAL -> numericPredicate(payload) { a, n -> a >= n }
            Operation.LESSER_THAN -> numericPredicate(payload) { a, n -> a < n }
            Operation.LESSER_THAN_EQUAL -> numericPredicate(payload) { a, n -> a <= n }
            Operation.BETWEEN_NUMERIC -> {
                val p = payload as? SanitizedPayload.NumericRange ?: return null
                { attr -> coerceNumber(attr)?.let { it >= p.start && it <= p.end } ?: false }
            }

            Operation.IS -> {
                val p = payload as? SanitizedPayload.SingleBoolean ?: return null
                { attr -> attr is Boolean && attr == p.boolean }
            }

            Operation.ON -> datePredicate(payload) { a, d -> a.time == d.time }
            Operation.ON_OR_AFTER -> datePredicate(payload) { a, d -> a.time >= d.time }
            Operation.ON_OR_BEFORE -> datePredicate(payload) { a, d -> a.time <= d.time }
            Operation.BETWEEN_DATE -> {
                val p = payload as? SanitizedPayload.DateRange ?: return null
                { attr -> coerceDate(attr)?.let { it.time >= p.start.time && it.time <= p.end.time } ?: false }
            }
        }
    }

    private fun numericPredicate(
        payload: SanitizedPayload,
        compare: (Double, Double) -> Boolean
    ): ((Any?) -> Boolean)? {
        val p = payload as? SanitizedPayload.SingleNumber ?: return null
        return { attr -> coerceNumber(attr)?.let { compare(it, p.number) } ?: false }
    }

    private fun datePredicate(
        payload: SanitizedPayload,
        compare: (Date, Date) -> Boolean
    ): ((Any?) -> Boolean)? {
        val p = payload as? SanitizedPayload.SingleDate ?: return null
        return { attr -> coerceDate(attr)?.let { compare(it, p.date) } ?: false }
    }
}
