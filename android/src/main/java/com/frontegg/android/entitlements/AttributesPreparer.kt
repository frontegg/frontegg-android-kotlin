package com.frontegg.android.entitlements

/**
 * Port of `prepareAttributes` from `@frontegg/entitlements-javascript-commons`.
 *
 * Web merges three sources into the rule-evaluation attribute bag:
 *   1. Custom attributes provided by the host app, used as-is (e.g. `"customAttr"`).
 *   2. Frontegg-derived attributes (`email`, `emailVerified`, `tenantId`, `userId`)
 *      pulled out of the JWT claims and prefixed `frontegg.`.
 *   3. The full flattened JWT claim tree, prefixed `jwt.` — so a nested
 *      `{ "metadata": { "plan": "pro" } }` claim becomes `jwt.metadata.plan`.
 *
 * Conditions reference attributes by their final prefixed names — e.g. a rule with
 * `attribute = "frontegg.tenantId"` reads the tenantId from this merged map.
 */
object AttributesPreparer {

    private const val FRONTEGG_PREFIX = "frontegg."
    private const val JWT_PREFIX = "jwt."

    fun prepare(attributes: Attributes): Map<String, Any?> {
        val merged = LinkedHashMap<String, Any?>()
        attributes.custom?.let { merged.putAll(it) }

        val jwt = attributes.jwt.orEmpty()
        val flatJwt = flatten(jwt)
        defaultFronteggAttributes(jwt).forEach { (k, v) ->
            merged[FRONTEGG_PREFIX + k] = v
        }
        flatJwt.forEach { (k, v) ->
            merged[JWT_PREFIX + k] = v
        }
        return merged
    }

    /**
     * Frontegg-canonical fields derived from JWT claims, exactly mirroring web's
     * `defaultFronteggAttributesMapper`.
     *
     *   `jwt.id`   → `frontegg.userId`
     *   `jwt.email`, `jwt.email_verified`, `jwt.tenantId` → corresponding camelCase
     */
    private fun defaultFronteggAttributes(jwt: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["email"] = jwt["email"]
        out["emailVerified"] = jwt["email_verified"]
        out["tenantId"] = jwt["tenantId"]
        out["userId"] = jwt["id"] ?: jwt["sub"]
        return out
    }

    /**
     * Depth-first flattening with `.`-joined keys. List values are kept as-is — they're
     * matched element-wise by operations like `in_list`/`contains` against the
     * attribute payload's `list` field, not by attribute path. Matches web behavior.
     */
    internal fun flatten(input: Map<String, Any?>, prefix: String = ""): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in input) {
            val key = if (prefix.isEmpty()) k else "$prefix.$k"
            if (v is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val nested = v as Map<String, Any?>
                out.putAll(flatten(nested, key))
            } else {
                out[key] = v
            }
        }
        return out
    }
}
