package com.frontegg.android.models

import com.google.gson.Gson
import org.junit.Before
import org.junit.Test

class TenantTest {
    private val tenantJson =
        "{\"vendorId\":\"392b348b-a37c-471f-8f1b-2c35d23aa7e6\",\"id\":\"9ca34b3c-8ab6-40b9-a582-bd8badb571cb\",\"creatorName\":\"Test User\",\"creatorEmail\":\"test@mail.com\",\"tenantId\":\"d130e118-8e56-4837-b70b-92943e567976\",\"updatedAt\":\"2024-05-13T13:03:31.533Z\",\"createdAt\":\"2024-05-13T13:03:31.533Z\",\"metadata\":\"{}\",\"isReseller\":false,\"name\":\"Test User account\"}"
    private lateinit var tenant: Tenant

    @Before
    fun setUp() {
        tenant = Tenant()
        tenant.vendorId = "392b348b-a37c-471f-8f1b-2c35d23aa7e6"
        tenant.id = "9ca34b3c-8ab6-40b9-a582-bd8badb571cb"
        tenant.creatorName = "Test User"
        tenant.creatorEmail = "test@mail.com"
        tenant.tenantId = "d130e118-8e56-4837-b70b-92943e567976"
        tenant.updatedAt = "2024-05-13T13:03:31.533Z"
        tenant.createdAt = "2024-05-13T13:03:31.533Z"
        tenant.metadata = "{}"
        tenant.isReseller = false
        tenant.name = "Test User account"
    }

    @Test
    fun `should return valid model`() {
        val tenantModel = Gson().fromJson(tenantJson, Tenant::class.java)

        assert(tenantModel == tenant)
    }
}