package com.frontegg.android.models

import com.frontegg.android.fixtures.getTenant
import com.frontegg.android.fixtures.tenantJson
import com.google.gson.Gson
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
}