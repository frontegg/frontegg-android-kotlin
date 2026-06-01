package com.frontegg.android.entitlements

import com.frontegg.android.models.NotEntitledJustification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsEntitledToPermissionTest {

    private val emptyCtx = UserEntitlementsContext(
        features = emptyMap(),
        plans = emptyMap(),
        permissions = emptyMap()
    )

    @Test
    fun `null context yields MISSING_PERMISSION`() {
        val result = IsEntitledToPermission.evaluate("fe.secure.read.users", null)
        assertFalse(result.isEntitled)
        assertEquals(NotEntitledJustification.MISSING_PERMISSION, result.justification)
    }

    @Test
    fun `wildcard granted permission matches the concrete request`() {
        val ctx = emptyCtx.copy(permissions = mapOf("fe.secure.*" to true))
        val result = IsEntitledToPermission.evaluate("fe.secure.read.users", ctx)
        assertTrue("$result", result.isEntitled)
    }

    @Test
    fun `wildcard with regex meta is escaped — dot is literal`() {
        // "fe.secure" should NOT match "feXsecure" — dots are literal even though
        // they look like regex any-chars. Hardens the SDK against pattern leaks.
        val ctx = emptyCtx.copy(permissions = mapOf("fe.secure" to true))
        val result = IsEntitledToPermission.evaluate("feXsecure", ctx)
        assertFalse("$result", result.isEntitled)
    }

    @Test
    fun `permission with no linked features is entitled once granted`() {
        val ctx = emptyCtx.copy(permissions = mapOf("fe.profile.read" to true))
        val result = IsEntitledToPermission.evaluate("fe.profile.read", ctx)
        assertTrue("$result", result.isEntitled)
    }

    @Test
    fun `permission linked to a not-entitled feature is denied`() {
        // Permission is granted by the server but it's gated on the "sso" feature,
        // which in turn rolls up to a plan with defaultTreatment "false". The
        // permission should NOT be reported as entitled.
        val ctx = UserEntitlementsContext(
            features = mapOf(
                "sso" to FeatureDetail(
                    planIds = listOf("plan-no-sso"),
                    expireTime = null,
                    linkedPermissions = listOf("fe.secure.read.samlDefaultRoles"),
                    featureFlag = null
                )
            ),
            plans = mapOf(
                "plan-no-sso" to Plan(defaultTreatment = Treatment.FALSE, rules = null)
            ),
            permissions = mapOf("fe.secure.read.samlDefaultRoles" to true)
        )
        val result = IsEntitledToPermission.evaluate("fe.secure.read.samlDefaultRoles", ctx)
        assertFalse("$result", result.isEntitled)
        // Aggregated from the linked feature — MISSING_FEATURE since the plan
        // resolved to false (no BUNDLE_EXPIRED in this graph).
        assertEquals(NotEntitledJustification.MISSING_FEATURE, result.justification)
    }

    @Test
    fun `permission linked to an entitled feature passes`() {
        val ctx = UserEntitlementsContext(
            features = mapOf(
                "alpha" to FeatureDetail(
                    planIds = emptyList(),
                    expireTime = FeatureDetail.NO_EXPIRATION_TIME,
                    linkedPermissions = listOf("fe.profile.write"),
                    featureFlag = null
                )
            ),
            plans = emptyMap(),
            permissions = mapOf("fe.profile.write" to true)
        )
        val result = IsEntitledToPermission.evaluate("fe.profile.write", ctx)
        assertTrue("$result", result.isEntitled)
    }
}
