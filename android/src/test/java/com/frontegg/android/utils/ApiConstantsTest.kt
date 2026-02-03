package com.frontegg.android.utils

import org.junit.Test

class ApiConstantsTest {

    @Test
    fun `me endpoint is defined`() {
        assert(ApiConstants.me.isNotEmpty())
    }

    @Test
    fun `tenants endpoint is defined`() {
        assert(ApiConstants.tenants.isNotEmpty())
    }

    @Test
    fun `refreshToken endpoint is defined`() {
        assert(ApiConstants.refreshToken.isNotEmpty())
    }

    @Test
    fun `exchangeToken endpoint is defined`() {
        assert(ApiConstants.exchangeToken.isNotEmpty())
    }

    @Test
    fun `logout endpoint is defined`() {
        assert(ApiConstants.logout.isNotEmpty())
    }

    @Test
    fun `switchTenant endpoint is defined`() {
        assert(ApiConstants.switchTenant.isNotEmpty())
    }

    @Test
    fun `webauthnPrelogin endpoint is defined`() {
        assert(ApiConstants.webauthnPrelogin.isNotEmpty())
    }

    @Test
    fun `webauthnPostlogin endpoint is defined`() {
        assert(ApiConstants.webauthnPostlogin.isNotEmpty())
    }

    @Test
    fun `registerWebauthnDevice endpoint is defined`() {
        assert(ApiConstants.registerWebauthnDevice.isNotEmpty())
    }

    @Test
    fun `verifyWebauthnDevice endpoint is defined`() {
        assert(ApiConstants.verifyWebauthnDevice.isNotEmpty())
    }

    @Test
    fun `silentRefreshToken endpoint is defined`() {
        assert(ApiConstants.silentRefreshToken.isNotEmpty())
    }

    @Test
    fun `socialLoginPostLogin endpoint is defined`() {
        assert(ApiConstants.socialLoginPostLogin.isNotEmpty())
    }

    @Test
    fun `all endpoints start with identity or oauth`() {
        val endpoints = listOf(
            ApiConstants.me,
            ApiConstants.tenants,
            ApiConstants.refreshToken,
            ApiConstants.exchangeToken,
            ApiConstants.logout,
            ApiConstants.switchTenant,
            ApiConstants.webauthnPrelogin,
            ApiConstants.webauthnPostlogin,
            ApiConstants.registerWebauthnDevice,
            ApiConstants.verifyWebauthnDevice,
            ApiConstants.silentRefreshToken,
            ApiConstants.socialLoginPostLogin
        )
        
        endpoints.forEach { endpoint ->
            assert(endpoint.startsWith("identity") || endpoint.startsWith("oauth") ||
                   endpoint.startsWith("frontegg")) {
                "Endpoint $endpoint should start with 'identity', 'oauth', or 'frontegg'"
            }
        }
    }

    @Test
    fun `socialLoginPostLogin contains provider placeholder`() {
        assert(ApiConstants.socialLoginPostLogin.contains("{provider}"))
    }

    @Test
    fun `endpoints do not have leading slash`() {
        val endpoints = listOf(
            ApiConstants.me,
            ApiConstants.tenants,
            ApiConstants.refreshToken,
            ApiConstants.exchangeToken,
            ApiConstants.logout,
            ApiConstants.switchTenant
        )
        
        endpoints.forEach { endpoint ->
            assert(!endpoint.startsWith("/")) {
                "Endpoint $endpoint should not start with '/'"
            }
        }
    }
}
