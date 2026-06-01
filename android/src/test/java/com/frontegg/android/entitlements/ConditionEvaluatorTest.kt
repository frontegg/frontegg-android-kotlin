package com.frontegg.android.entitlements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ConditionEvaluator]. Each test pins a small condition shape and
 * exercises both the truthy and falsy branches. The TS reference implementation
 * (`@frontegg/entitlements-javascript-commons/src/conditions/tests/condition.evaluator.spec.ts`)
 * was the spec we ported from — when in doubt about behavior, that's the source of
 * truth.
 */
class ConditionEvaluatorTest {

    private fun cond(
        attribute: String,
        op: Operation,
        value: Map<String, Any?>,
        negate: Boolean = false
    ) = Condition(attribute = attribute, negate = negate, op = op, value = value)

    // MARK: - String operations

    @Test
    fun `in_list matches when attribute is in the list`() {
        val c = cond("frontegg.email", Operation.IN_LIST, mapOf("list" to listOf("a@x.com", "b@x.com")))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("frontegg.email" to "a@x.com")))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("frontegg.email" to "c@x.com")))
    }

    @Test
    fun `in_list returns false when payload is malformed`() {
        // `list` element types don't match — sanitizer returns null → condition false.
        val c = cond("k", Operation.IN_LIST, mapOf("list" to listOf("a", 1)))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to "a")))
    }

    @Test
    fun `starts_with matches across multiple prefixes`() {
        val c = cond("k", Operation.STARTS_WITH, mapOf("list" to listOf("foo", "bar")))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to "foobar")))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to "barbaz")))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to "baz")))
    }

    @Test
    fun `ends_with and contains`() {
        val ends = cond("k", Operation.ENDS_WITH, mapOf("list" to listOf("xyz")))
        assertTrue(ConditionEvaluator.evaluate(ends, mapOf("k" to "abcxyz")))
        val contains = cond("k", Operation.CONTAINS, mapOf("list" to listOf("ll")))
        assertTrue(ConditionEvaluator.evaluate(contains, mapOf("k" to "hello")))
    }

    @Test
    fun `matches uses regex semantics`() {
        val c = cond("k", Operation.MATCHES, mapOf("string" to "^foo.*bar$"))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to "foo-x-bar")))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to "foo-x-baz")))
    }

    @Test
    fun `matches returns false on a malformed regex pattern`() {
        // Unmatched `(` — Java's regex compiler throws; we must swallow and return false
        // rather than crash the entire entitlement check.
        val c = cond("k", Operation.MATCHES, mapOf("string" to "("))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to "anything")))
    }

    // MARK: - Numeric operations

    @Test
    fun `equal across integer and double attributes`() {
        val c = cond("k", Operation.EQUAL, mapOf("number" to 42))
        // JSON parsing routes integers through Long; ensure both types compare equal.
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to 42)))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to 42L)))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to 42.0)))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to 43)))
    }

    @Test
    fun `between_numeric is inclusive on both ends`() {
        val c = cond("k", Operation.BETWEEN_NUMERIC, mapOf("start" to 10, "end" to 20))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to 10)))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to 15)))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to 20)))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to 21)))
    }

    @Test
    fun `greater_than and lower_than_equal`() {
        val gt = cond("k", Operation.GREATER_THAN, mapOf("number" to 5))
        assertTrue(ConditionEvaluator.evaluate(gt, mapOf("k" to 6)))
        assertFalse(ConditionEvaluator.evaluate(gt, mapOf("k" to 5)))
        val lte = cond("k", Operation.LESSER_THAN_EQUAL, mapOf("number" to 5))
        assertTrue(ConditionEvaluator.evaluate(lte, mapOf("k" to 5)))
        assertFalse(ConditionEvaluator.evaluate(lte, mapOf("k" to 6)))
    }

    // MARK: - Boolean

    @Test
    fun `boolean is checks strict equality, rejects truthy strings`() {
        val c = cond("k", Operation.IS, mapOf("boolean" to true))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to true)))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to false)))
        // Web compares with `===`; matching that — string "true" is NOT boolean true.
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to "true")))
    }

    // MARK: - Date

    @Test
    fun `on_or_after with string ISO attribute`() {
        val c = cond("k", Operation.ON_OR_AFTER, mapOf("date" to "2026-01-01T00:00:00Z"))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to "2026-06-01T00:00:00Z")))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to "2025-12-31T00:00:00Z")))
    }

    @Test
    fun `between_date inclusive`() {
        val c = cond(
            "k",
            Operation.BETWEEN_DATE,
            mapOf("start" to "2026-01-01T00:00:00Z", "end" to "2026-12-31T00:00:00Z")
        )
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to "2026-06-15T00:00:00Z")))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to "2027-01-01T00:00:00Z")))
    }

    // MARK: - Edge cases

    @Test
    fun `missing attribute is false regardless of negate`() {
        val c = cond("absent", Operation.IS, mapOf("boolean" to true), negate = true)
        // Web short-circuits before negate: absent attribute is unconditionally false.
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("present" to true)))
    }

    @Test
    fun `negate inverts a normally-true match`() {
        val c = cond("k", Operation.IS, mapOf("boolean" to true), negate = true)
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to true)))
        assertTrue(ConditionEvaluator.evaluate(c, mapOf("k" to false)))
    }

    @Test
    fun `null attribute value is false even when key is present`() {
        val c = cond("k", Operation.IS, mapOf("boolean" to true))
        // We mirror web's `attributes[key] !== undefined` precheck — null counts as
        // missing for evaluator purposes.
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to null)))
    }

    @Test
    fun `type mismatch between attribute and operation returns false`() {
        // Numeric op expecting a Number, attribute is a String — handler returns false.
        val c = cond("k", Operation.EQUAL, mapOf("number" to 5))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to "5")))
    }

    @Test
    fun `unknown operation enum result is null sanitizer`() {
        // Sanity: Operation enum is closed-over here. If a server adds a new op the
        // SDK doesn't know about yet, the Operation.fromWire(...) in the parser
        // returns null and the condition (and rule) drops. We test that path
        // separately in UserEntitlementsParserTest; here we verify the in-process
        // sanitizer rejects an obviously wrong payload shape.
        val c = cond("k", Operation.EQUAL, mapOf("not-number" to "x"))
        assertFalse(ConditionEvaluator.evaluate(c, mapOf("k" to 5)))
    }

    // Convenience — keep this near the bottom so the file reads top-to-bottom.
    @Test
    fun `evaluate returns Boolean explicitly to satisfy Kotlin truthiness`() {
        // Defensive sanity check — assertEquals catches accidental Boolean vs Boolean?.
        val c = cond("k", Operation.IS, mapOf("boolean" to true))
        assertEquals(true, ConditionEvaluator.evaluate(c, mapOf("k" to true)))
    }
}
