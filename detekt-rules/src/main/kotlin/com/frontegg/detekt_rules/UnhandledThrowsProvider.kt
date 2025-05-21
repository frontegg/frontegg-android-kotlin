package com.frontegg.detekt_rules

import io.gitlab.arturbosch.detekt.api.*

class UnhandledThrowsProvider : RuleSetProvider {
    // this ID *must* match what you use in your YAML:
    override val ruleSetId: String = "unhandled-throws"

    override fun instance(config: Config): RuleSet =
        RuleSet(ruleSetId, listOf(UnhandledThrowsRule(config)))
}