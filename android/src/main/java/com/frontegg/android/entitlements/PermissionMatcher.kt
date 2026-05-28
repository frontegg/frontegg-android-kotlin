package com.frontegg.android.entitlements

/**
 * Port of `checkPermission` from `@frontegg/entitlements-javascript-commons`. The
 * server returns granted permissions as concrete strings OR wildcard patterns (e.g.
 * `fe.secure.*`); the host app asks about a concrete required permission (e.g.
 * `fe.secure.read.users`). Match if any granted key, when its wildcards are turned
 * into `.*`, regex-matches the requested key.
 *
 * The regex is anchored — `^…$` — so `fe.secure.*` does NOT match `prefix.fe.secure.x`.
 * Dots are escaped to literals; `*` is the only wildcard.
 */
object PermissionMatcher {
    fun hasPermission(grantedPermissions: Map<String, Boolean>, required: String): Boolean {
        // The server semantics for the permissions map is "key present with value=true
        // means granted"; ignore keys whose value isn't truthy.
        val truthy = grantedPermissions.entries.asSequence().filter { it.value }.map { it.key }
        return truthy.any { granted -> patternToRegex(granted).matches(required) }
    }

    /**
     * Returns the granted-permission keys (with their wildcards still intact) whose
     * pattern matches [required]. Useful for callers that want to follow up with
     * permission → linked-feature lookup against the full
     * [UserEntitlementsContext.permissions] map.
     */
    fun matchingGrantedKeys(grantedPermissions: Map<String, Boolean>, required: String): List<String> {
        return grantedPermissions.entries
            .filter { it.value && patternToRegex(it.key).matches(required) }
            .map { it.key }
    }

    private fun patternToRegex(pattern: String): Regex {
        val escaped = StringBuilder("^")
        for (ch in pattern) {
            when (ch) {
                '*' -> escaped.append(".*")
                '.' -> escaped.append("\\.")
                // The other regex metacharacters are extremely unlikely to appear in
                // a real permission key, but escape them defensively so a malformed
                // key can't ReDoS the SDK.
                '+', '?', '(', ')', '[', ']', '{', '}', '|', '^', '$', '\\' ->
                    escaped.append('\\').append(ch)
                else -> escaped.append(ch)
            }
        }
        escaped.append('$')
        return Regex(escaped.toString(), setOf(RegexOption.DOT_MATCHES_ALL))
    }
}
