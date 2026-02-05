package com.frontegg.android.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TenantResolverTest {

    @Test
    fun `TenantResolverResult stores tenant value`() {
        val result = TenantResolverResult("my-tenant")
        assertEquals("my-tenant", result.tenant)
    }

    @Test
    fun `TenantResolverResult can have null tenant`() {
        val result = TenantResolverResult(null)
        assertNull(result.tenant)
    }

    @Test
    fun `TenantResolver functional interface works with lambda`() {
        val resolver = TenantResolver { TenantResolverResult("test-org") }
        val result = resolver.resolve()
        assertEquals("test-org", result.tenant)
    }

    @Test
    fun `TenantResolver can return null tenant`() {
        val resolver = TenantResolver { TenantResolverResult(null) }
        val result = resolver.resolve()
        assertNull(result.tenant)
    }

    @Test
    fun `TenantResolver can resolve dynamically`() {
        var organization: String? = null
        
        val resolver = TenantResolver { TenantResolverResult(organization) }
        
        // Initially null
        assertNull(resolver.resolve().tenant)
        
        // Update the value
        organization = "dynamic-tenant"
        assertEquals("dynamic-tenant", resolver.resolve().tenant)
    }

    @Test
    fun `TenantResolverResult data class equality works`() {
        val result1 = TenantResolverResult("tenant-a")
        val result2 = TenantResolverResult("tenant-a")
        val result3 = TenantResolverResult("tenant-b")
        
        assertEquals(result1, result2)
        assert(result1 != result3)
    }

    @Test
    fun `TenantResolverResult copy works`() {
        val original = TenantResolverResult("original")
        val copied = original.copy(tenant = "modified")
        
        assertEquals("original", original.tenant)
        assertEquals("modified", copied.tenant)
    }
}
