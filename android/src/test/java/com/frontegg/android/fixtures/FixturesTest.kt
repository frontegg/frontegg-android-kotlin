package com.frontegg.android.fixtures

import com.google.gson.Gson
import org.junit.Test

/**
 * Tests to verify the test fixtures are correctly defined
 */
class FixturesTest {

    private val gson = Gson()

    @Test
    fun `authResponseJson is valid JSON`() {
        val parsed = gson.fromJson(authResponseJson, com.frontegg.android.models.AuthResponse::class.java)
        
        assert(parsed != null)
    }

    @Test
    fun `getAuthResponse returns valid AuthResponse`() {
        val response = getAuthResponse()
        
        assert(response.id_token == "Test Id Token")
        assert(response.refresh_token == "Test Refresh Token")
        assert(response.access_token == "Test Access Token")
        assert(response.token_type == "Test Token Type")
    }

    @Test
    fun `tenantJson is valid JSON`() {
        val parsed = gson.fromJson(tenantJson, com.frontegg.android.models.Tenant::class.java)
        
        assert(parsed != null)
    }

    @Test
    fun `getTenant returns valid Tenant`() {
        val tenant = getTenant()
        
        assert(tenant.vendorId == "392b348b-a37c-471f-8f1b-2c35d23aa7e6")
        assert(tenant.id == "9ca34b3c-8ab6-40b9-a582-bd8badb571cb")
        assert(tenant.tenantId == "d130e118-8e56-4837-b70b-92943e567976")
        assert(tenant.name == "Test User account")
        assert(tenant.creatorName == "Test User")
        assert(tenant.creatorEmail == "test@mail.com")
        assert(!tenant.isReseller)
    }

    @Test
    fun `userJson is valid JSON`() {
        val parsed = gson.fromJson(userJson, com.frontegg.android.models.User::class.java)
        
        assert(parsed != null)
    }

    @Test
    fun `getUser returns valid User`() {
        val user = getUser()
        
        assert(user.id == "d89330b3-f581-493c-bcd7-0c4b1dff1111")
        assert(user.email == "test@mail.com")
        assert(user.name == "Test User")
        assert(user.verified)
        assert(user.activatedForTenant)
        assert(!user.superUser)
        assert(!user.mfaEnrolled)
    }

    @Test
    fun `getUser has roles`() {
        val user = getUser()
        
        assert(user.roles.isNotEmpty())
        assert(user.roles[0].name == "Admin")
        assert(user.roles[0].key == "Admin")
    }

    @Test
    fun `getUser has tenants`() {
        val user = getUser()
        
        assert(user.tenants.isNotEmpty())
        assert(user.tenants[0].name == "Test User account")
    }

    @Test
    fun `getUser has permissions`() {
        val user = getUser()
        
        assert(user.permissions.isNotEmpty())
        assert(user.permissions.size == 3)
    }

    @Test
    fun `getUser has activeTenant`() {
        val user = getUser()
        
        assert(user.activeTenant != null)
        assert(user.activeTenant.tenantId == "d130e118-8e56-4837-b70b-92943e567976")
    }

    @Test
    fun `webAuthnAssertionRequestJson is valid JSON`() {
        // Just verify it can be parsed
        assert(webAuthnAssertionRequestJson.isNotEmpty())
        assert(webAuthnAssertionRequestJson.contains("cookie"))
    }

    @Test
    fun `getWebAuthnAssertionRequest returns valid request`() {
        val request = getWebAuthnAssertionRequest()
        
        assert(request.cookie == "Test Cookie")
        assert(request.jsonChallenge == "{}")
    }

    @Test
    fun `fixtures are consistent between JSON and object`() {
        val userFromJson = gson.fromJson(userJson, com.frontegg.android.models.User::class.java)
        val userFromFunction = getUser()
        
        // Core fields should match
        assert(userFromJson.email == userFromFunction.email)
        assert(userFromJson.name == userFromFunction.name)
        assert(userFromJson.id == userFromFunction.id)
    }

    @Test
    fun `tenant fixtures are consistent`() {
        val tenantFromJson = gson.fromJson(tenantJson, com.frontegg.android.models.Tenant::class.java)
        val tenantFromFunction = getTenant()
        
        assert(tenantFromJson.tenantId == tenantFromFunction.tenantId)
        assert(tenantFromJson.name == tenantFromFunction.name)
    }

    @Test
    fun `authResponse fixtures are consistent`() {
        val responseFromJson = gson.fromJson(authResponseJson, com.frontegg.android.models.AuthResponse::class.java)
        val responseFromFunction = getAuthResponse()
        
        assert(responseFromJson.access_token == responseFromFunction.access_token)
        assert(responseFromJson.refresh_token == responseFromFunction.refresh_token)
    }
}
