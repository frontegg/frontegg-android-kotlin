package com.frontegg.android.entitlements

/**
 * Port of `createRuleEvaluator` from `@frontegg/entitlements-javascript-commons`.
 *
 * A rule is `Treatable` iff every condition in it evaluates true (web only ships
 * AND-of-conditions; if the server ever introduces other operators, the
 * [ConditionLogic] enum is the place to extend).
 */
enum class RuleEvaluationResult { TREATABLE, INSUFFICIENT }

object RuleEvaluator {
    fun evaluate(rule: Rule, attributes: Map<String, Any?>): RuleEvaluationResult {
        val all = rule.conditions.all { ConditionEvaluator.evaluate(it, attributes) }
        return if (all) RuleEvaluationResult.TREATABLE else RuleEvaluationResult.INSUFFICIENT
    }
}
