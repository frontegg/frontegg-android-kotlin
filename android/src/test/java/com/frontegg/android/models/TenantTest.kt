package com.frontegg.android.models

import com.frontegg.android.fixtures.getTenant
import com.frontegg.android.fixtures.tenantJson
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class TenantTest {
    private lateinit var tenant: Tenant

    @Before
    fun setUp() {
        tenant = getTenant()
    }

    @Test
    fun `should return valid model`() {
        val tenantModel = Gson().fromJson(tenantJson, Tenant::class.java)

        assert(tenantModel == tenant)
    }

    @Test
    fun `equals does not crash when a lateinit field is uninitialized on one side`() {
        // Regression: Gson silently leaves `lateinit var` fields uninitialized when
        // the corresponding JSON key is absent in the response — common for /me
        // responses scoped by `applicationId`. Pre-fix, `tenant == other` crashed
        // with UninitializedPropertyAccessException the moment `equals()` read the
        // uninitialized field. The embedded demo's TenantAdapter.getView triggered
        // exactly this when comparing the current tenant against each list item
        // (`if (tenant == activeTenant)`), blocking QA of PR #257.
        //
        // Post-fix: equals reads via `isInitialized`-guarded accessors, treats
        // uninitialized-vs-anything as "not equal" rather than crashing.
        val partial = Gson().fromJson("""{"name":"PartialTenant"}""", Tenant::class.java)
        val full = getTenant()

        // The two are not equal (partial is missing every field except name) but
        // the comparison itself must complete without throwing.
        assertNotEquals(full, partial)
        assertNotEquals(partial, full)
        // hashCode must not throw either — collections like HashSet / HashMap call
        // it without the host app's knowledge.
        assertEquals(partial.hashCode(), partial.hashCode())
    }

    @Test
    fun `equals returns true for two partially-deserialized tenants with the same shape`() {
        // A second Gson decode of the same JSON should equal the first — even if
        // most lateinit fields are uninitialized — because equals compares safely.
        val a = Gson().fromJson("""{"name":"PartialTenant"}""", Tenant::class.java)
        val b = Gson().fromJson("""{"name":"PartialTenant"}""", Tenant::class.java)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
