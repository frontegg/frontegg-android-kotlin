package com.frontegg.detekt_rules

import io.gitlab.arturbosch.detekt.api.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.org.jline.utils.Log
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI


@OptIn(IDEAPluginsCompatibilityAPI::class)
class UnhandledThrowsRule(config: Config) : Rule(config) {

    override val issue = Issue(
        id = "UnhandledThrows",
        severity = Severity.Defect,
        debt = Debt.TEN_MINS,
        description = "Calls to @Throws-annotated functions should be wrapped in try/catch or declared with @Throws."
    )

    private val throwsAnnotation = FqName("kotlin.jvm.Throws")


    private fun checkNames(
        entry: KtAnnotationEntry,
        thrownExceptionShortNames: List<String>
    ): Boolean {
        return entry.valueArguments.any { arg ->
            val text = arg.getArgumentExpression()?.text ?: return@any false
            // e.g. "IOException::class" → "IOException"
            val name = text.removeSuffix("::class").substringAfterLast('.')
            thrownExceptionShortNames.contains(name)
        }
    }

    override fun visitCallExpression(call: KtCallExpression) {
        super.visitCallExpression(call)

        try {
            val ctx = bindingContext
            val resolved = call.getResolvedCall(ctx)?.resultingDescriptor ?: return

            // 1) Only care about calls annotated with @Throws
            val throwsAnns = resolved.annotations.filter { it.fqName == throwsAnnotation }
            if (throwsAnns.isEmpty()) return

            // 2) Pull out the actual exception class short‐names
            val thrownExceptionShortNames: List<String> = throwsAnns
                .flatMap { ann ->
                    ann.allValueArguments
                        .values
                        .flatMap { constant ->
                            when (constant) {
                                // single‐class literal
                                is KClassValue -> listOfNotNull(
                                    (constant.value as? KClassValue.Value.NormalClass)
                                        ?.classId
                                        ?.shortClassName
                                        ?.asString()
                                )

                                // vararg: an ArrayValue of ConstantValue<*>
                                is ArrayValue -> (constant.value)
                                    .mapNotNull { element ->
                                        (element as? KClassValue)
                                            ?.value
                                            ?.let { v -> (v as? KClassValue.Value.NormalClass)?.classId?.shortClassName?.asString() }
                                    }

                                else -> emptyList()
                            }
                        }
                }

            Log.warn("thrownExceptionShortNames: $thrownExceptionShortNames")
            // 3) If we’re inside any try…catch at all, assume handled (we’ll catch generic below)
            if (call.parents.any { it is KtTryExpression }) return

            // 4) If an enclosing function itself has @Throws for one of these same exceptions, skip
            val parentIsDeclaring =
                call.parents
                    .filterIsInstance<KtNamedFunction>()
                    .any { fn ->
                        fn.annotationEntries.any { entry ->
                            entry.shortName?.asString() == "Throws" &&
                                    checkNames(entry, thrownExceptionShortNames)
                        }
                    }
            if (parentIsDeclaring) return

            // 5) Otherwise report an unhandled @Throws
            report(
                CodeSmell(
                    issue,
                    Entity.from(call),
                    "Call to `${resolved.name}` is annotated @Throws but neither wrapped in try/catch nor declared by the caller."
                )
            )
        } catch (e: Exception) {
            Log.warn("Error in UnhandledThrowsRule: ${e.message}")
            // Handle the error gracefully, maybe log it or ignore
            // e.printStackTrace()
            // You can also report the error if needed
            // report(CodeSmell(issue, Entity.from(call), "Error in UnhandledThrowsRule: ${e.message}"))
        }
    }

}
