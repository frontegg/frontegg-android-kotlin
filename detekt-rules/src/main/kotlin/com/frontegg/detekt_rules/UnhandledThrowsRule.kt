package com.frontegg.detekt_rules

import io.gitlab.arturbosch.detekt.api.*
import io.gitlab.arturbosch.detekt.rules.fqNameOrNull
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.org.jline.utils.Log
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI


@OptIn(IDEAPluginsCompatibilityAPI::class)
class UnhandledThrowsRule(config: Config) : Rule(config) {
    override val issue = Issue(
        id = "UnhandledThrows",
        severity = Severity.Defect,
        debt = Debt.TEN_MINS,
        description = "Calls to @Throws-annotated functions should be wrapped in try/catch."
    )

    private val throwsAnnotation = FqName("kotlin.jvm.Throws")

    override fun visitCallExpression(call: KtCallExpression) {
        super.visitCallExpression(call)
        val ctx = bindingContext
        val descriptor = call.getResolvedCall(ctx)
        val functionDescriptor = descriptor?.resultingDescriptor ?: return
        val hasThrows = functionDescriptor.annotations.any {
            it.type.toString().contains("Throws")
        }
        if(!hasThrows && !functionDescriptor.annotations.hasAnnotation(throwsAnnotation)) return
        Log.warn("descriptor.name: ${functionDescriptor.name}")
        if (call.parents.any { it is org.jetbrains.kotlin.psi.KtTryExpression }) return

        report(
            CodeSmell(
                issue,
                Entity.from(call),
                "Call to `${functionDescriptor.name}` is annotated @Throws but not wrapped in try/catch."
            )
        )
    }
}