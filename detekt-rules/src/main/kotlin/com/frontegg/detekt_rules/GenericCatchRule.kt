package com.frontegg.detekt_rules

import io.gitlab.arturbosch.detekt.api.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI

class GenericCatchRule(config: Config) : Rule(config) {

    override val issue = Issue(
        id = "GenericCatch",
        severity = Severity.Style,
        debt = Debt.FIVE_MINS,
        description = "Catching generic exception; use specific exception types."
    )


    override fun visitTryExpression(tryExp: KtTryExpression) {
        super.visitTryExpression(tryExp)

        // Flag any catch clauses that swallow the generic Exception or Throwable
        tryExp.catchClauses.forEach { catcher ->
            val param = catcher.catchParameter ?: return@forEach
            val typeName = param.typeReference?.text ?: return@forEach
            if (typeName == "Exception" || typeName == "Throwable") {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(param),
                        "Catch block catches generic exception `$typeName`. Consider catching specific exception types."
                    )
                )
            }
        }
    }
}
