package com.frontegg.android.entitlements

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Lenient parser for the `/frontegg/entitlements/api/v2/user-entitlements` JSON
 * response. Mirrors the shape consumed by `@frontegg/entitlements-javascript-commons`.
 *
 * Lenient on purpose — the SDK ships ahead of server-side schema changes. Unknown
 * keys are ignored; malformed sub-objects (e.g. a `featureFlag` with the wrong shape)
 * cause that sub-object to be dropped rather than failing the whole parse. The
 * resulting [UserEntitlementsContext] is what every downstream evaluator reads from.
 */
internal object UserEntitlementsParser {

    fun parse(json: JsonObject): UserEntitlementsContext {
        val features = parseFeatures(json.getAsJsonObject("features"))
        val plans = parsePlans(json.getAsJsonObject("plans"))
        val permissions = parsePermissions(json.getAsJsonObject("permissions"))
        return UserEntitlementsContext(
            features = features,
            plans = plans,
            permissions = permissions
        )
    }

    private fun parseFeatures(node: JsonObject?): Map<String, FeatureDetail> {
        if (node == null) return emptyMap()
        val out = LinkedHashMap<String, FeatureDetail>()
        for ((key, value) in node.entrySet()) {
            val obj = value as? JsonObject ?: continue
            val planIds = asStringList(obj.get("planIds"))
            val expireTime = asNullableLong(obj.get("expireTime"))
            val linkedPermissions = asStringList(obj.get("linkedPermissions"))
            val featureFlag = parseFeatureFlag(obj.get("featureFlag") as? JsonObject)
            out[key] = FeatureDetail(
                planIds = planIds,
                expireTime = expireTime,
                linkedPermissions = linkedPermissions,
                featureFlag = featureFlag
            )
        }
        return out
    }

    private fun parsePlans(node: JsonObject?): Map<String, Plan> {
        if (node == null) return emptyMap()
        val out = LinkedHashMap<String, Plan>()
        for ((key, value) in node.entrySet()) {
            val obj = value as? JsonObject ?: continue
            // defaultTreatment is required for a plan to be meaningful; drop the plan
            // entirely if it's missing rather than guessing.
            val defaultTreatment = Treatment.fromWire(asNullableString(obj.get("defaultTreatment")))
                ?: continue
            val rules = parseRules(obj.get("rules") as? JsonArray)
            out[key] = Plan(defaultTreatment = defaultTreatment, rules = rules)
        }
        return out
    }

    private fun parsePermissions(node: JsonObject?): Map<String, Boolean> {
        if (node == null) return emptyMap()
        val out = LinkedHashMap<String, Boolean>()
        for ((key, value) in node.entrySet()) {
            val prim = value?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: continue
            if (!prim.isBoolean) continue
            out[key] = prim.asBoolean
        }
        return out
    }

    private fun parseFeatureFlag(node: JsonObject?): FeatureFlag? {
        if (node == null) return null
        val on = asNullableBoolean(node.get("on")) ?: return null
        val offTreatment = Treatment.fromWire(asNullableString(node.get("offTreatment")))
            ?: return null
        val defaultTreatment = Treatment.fromWire(asNullableString(node.get("defaultTreatment")))
            ?: return null
        val rules = parseRules(node.get("rules") as? JsonArray)
        return FeatureFlag(
            on = on,
            offTreatment = offTreatment,
            defaultTreatment = defaultTreatment,
            rules = rules
        )
    }

    private fun parseRules(node: JsonArray?): List<Rule>? {
        if (node == null) return null
        val out = ArrayList<Rule>(node.size())
        for (element in node) {
            val obj = element as? JsonObject ?: continue
            val rule = parseRule(obj) ?: continue
            out.add(rule)
        }
        return out
    }

    private fun parseRule(obj: JsonObject): Rule? {
        val logic = ConditionLogic.fromWire(asNullableString(obj.get("conditionLogic"))) ?: return null
        val treatment = Treatment.fromWire(asNullableString(obj.get("treatment"))) ?: return null
        val conditionsNode = obj.get("conditions") as? JsonArray ?: return null
        val conditions = ArrayList<Condition>(conditionsNode.size())
        for (c in conditionsNode) {
            val cObj = c as? JsonObject ?: continue
            val condition = parseCondition(cObj) ?: continue
            conditions.add(condition)
        }
        if (conditions.isEmpty()) return null
        return Rule(conditionLogic = logic, conditions = conditions, treatment = treatment)
    }

    private fun parseCondition(obj: JsonObject): Condition? {
        val attribute = asNullableString(obj.get("attribute")) ?: return null
        val op = Operation.fromWire(asNullableString(obj.get("op"))) ?: return null
        val negate = asNullableBoolean(obj.get("negate")) ?: false
        val valueNode = obj.get("value") as? JsonObject ?: return null
        val value = jsonObjectToMap(valueNode)
        return Condition(attribute = attribute, negate = negate, op = op, value = value)
    }

    private fun asStringList(element: JsonElement?): List<String> {
        val arr = (element as? JsonArray) ?: return emptyList()
        val out = ArrayList<String>(arr.size())
        for (e in arr) {
            if (e.isJsonPrimitive && e.asJsonPrimitive.isString) {
                out.add(e.asString)
            }
        }
        return out
    }

    private fun asNullableLong(element: JsonElement?): Long? {
        if (element == null || element.isJsonNull) return null
        return try {
            element.asJsonPrimitive.takeIf { it.isNumber }?.asLong
        } catch (_: Exception) {
            null
        }
    }

    private fun asNullableString(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null
        if (!element.isJsonPrimitive) return null
        val prim = element.asJsonPrimitive
        return if (prim.isString) prim.asString else null
    }

    private fun asNullableBoolean(element: JsonElement?): Boolean? {
        if (element == null || element.isJsonNull) return null
        if (!element.isJsonPrimitive) return null
        val prim = element.asJsonPrimitive
        return if (prim.isBoolean) prim.asBoolean else null
    }

    /**
     * Converts a [JsonObject] into a plain `Map<String, Any?>` for use as a condition
     * `value` payload. Recursively unwraps nested objects, arrays, and primitives so
     * the sanitizer matrix can pattern-match against native Kotlin types.
     */
    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in obj.entrySet()) {
            out[k] = jsonElementToAny(v)
        }
        return out
    }

    private fun jsonElementToAny(el: JsonElement?): Any? {
        if (el == null || el.isJsonNull) return null
        return when {
            el.isJsonPrimitive -> {
                val prim = el.asJsonPrimitive
                when {
                    prim.isBoolean -> prim.asBoolean
                    prim.isString -> prim.asString
                    prim.isNumber -> {
                        // Gson hands us LazilyParsedNumber; promote to Double for
                        // numeric ops, or Long for integer-shaped values, so the
                        // sanitizer's `as? Number` checks succeed.
                        val str = prim.asString
                        if (str.contains('.') || str.contains('e') || str.contains('E')) {
                            prim.asDouble
                        } else {
                            try { prim.asLong } catch (_: NumberFormatException) { prim.asDouble }
                        }
                    }
                    else -> null
                }
            }
            el.isJsonObject -> jsonObjectToMap(el.asJsonObject)
            el.isJsonArray -> {
                val arr = el.asJsonArray
                val out = ArrayList<Any?>(arr.size())
                for (item in arr) out.add(jsonElementToAny(item))
                out
            }
            else -> null
        }
    }
}
