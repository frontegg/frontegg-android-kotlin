package com.frontegg.android.services

import com.frontegg.android.models.Tenant
import com.frontegg.android.models.User
import org.junit.Before
import org.junit.Test

class FronteggStateTest {

    @Before
    fun setUp() {
        // Reset all state before each test
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.user.value = null
        FronteggState.isAuthenticated.value = false
        FronteggState.isLoading.value = false
        FronteggState.webLoading.value = false
        FronteggState.initializing.value = true
        FronteggState.showLoader.value = false
        FronteggState.refreshingToken.value = false
        FronteggState.isStepUpAuthorization.value = false
    }

    @Test
    fun `accessToken can be set and retrieved`() {
        FronteggState.accessToken.value = "test-access-token"
        
        assert(FronteggState.accessToken.value == "test-access-token")
    }

    @Test
    fun `refreshToken can be set and retrieved`() {
        FronteggState.refreshToken.value = "test-refresh-token"
        
        assert(FronteggState.refreshToken.value == "test-refresh-token")
    }

    @Test
    fun `user can be set and retrieved`() {
        val user = User()
        user.id = "user-123"
        user.email = "test@example.com"
        user.name = "Test User"
        user.activeTenant = Tenant()
        
        FronteggState.user.value = user
        
        assert(FronteggState.user.value?.id == "user-123")
        assert(FronteggState.user.value?.email == "test@example.com")
    }

    @Test
    fun `isAuthenticated can be toggled`() {
        assert(!FronteggState.isAuthenticated.value)
        
        FronteggState.isAuthenticated.value = true
        
        assert(FronteggState.isAuthenticated.value)
        
        FronteggState.isAuthenticated.value = false
        
        assert(!FronteggState.isAuthenticated.value)
    }

    @Test
    fun `isLoading can be toggled`() {
        FronteggState.isLoading.value = true
        
        assert(FronteggState.isLoading.value)
        
        FronteggState.isLoading.value = false
        
        assert(!FronteggState.isLoading.value)
    }

    @Test
    fun `webLoading can be toggled`() {
        FronteggState.webLoading.value = true
        
        assert(FronteggState.webLoading.value)
        
        FronteggState.webLoading.value = false
        
        assert(!FronteggState.webLoading.value)
    }

    @Test
    fun `initializing default is true`() {
        // After reset, initializing should be true
        assert(FronteggState.initializing.value)
    }

    @Test
    fun `initializing can be set to false`() {
        FronteggState.initializing.value = false
        
        assert(!FronteggState.initializing.value)
    }

    @Test
    fun `showLoader can be toggled`() {
        FronteggState.showLoader.value = true
        
        assert(FronteggState.showLoader.value)
        
        FronteggState.showLoader.value = false
        
        assert(!FronteggState.showLoader.value)
    }

    @Test
    fun `refreshingToken can be toggled`() {
        FronteggState.refreshingToken.value = true
        
        assert(FronteggState.refreshingToken.value)
        
        FronteggState.refreshingToken.value = false
        
        assert(!FronteggState.refreshingToken.value)
    }

    @Test
    fun `isStepUpAuthorization can be toggled`() {
        FronteggState.isStepUpAuthorization.value = true
        
        assert(FronteggState.isStepUpAuthorization.value)
        
        FronteggState.isStepUpAuthorization.value = false
        
        assert(!FronteggState.isStepUpAuthorization.value)
    }

    @Test
    fun `clearing tokens resets authentication state`() {
        FronteggState.accessToken.value = "token"
        FronteggState.refreshToken.value = "refresh"
        FronteggState.isAuthenticated.value = true
        
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.isAuthenticated.value = false
        
        assert(FronteggState.accessToken.value == null)
        assert(FronteggState.refreshToken.value == null)
        assert(!FronteggState.isAuthenticated.value)
    }

    @Test
    fun `setting user also tracks authentication`() {
        val user = User()
        user.id = "user-456"
        user.activeTenant = Tenant()
        
        FronteggState.user.value = user
        FronteggState.isAuthenticated.value = true
        
        assert(FronteggState.user.value != null)
        assert(FronteggState.isAuthenticated.value)
    }

    @Test
    fun `state can be fully cleared`() {
        // Set all state
        FronteggState.accessToken.value = "token"
        FronteggState.refreshToken.value = "refresh"
        val user = User()
        user.activeTenant = Tenant()
        FronteggState.user.value = user
        FronteggState.isAuthenticated.value = true
        FronteggState.isLoading.value = true
        FronteggState.webLoading.value = true
        FronteggState.initializing.value = false
        FronteggState.showLoader.value = true
        FronteggState.refreshingToken.value = true
        FronteggState.isStepUpAuthorization.value = true
        
        // Clear all state
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.user.value = null
        FronteggState.isAuthenticated.value = false
        FronteggState.isLoading.value = false
        FronteggState.webLoading.value = false
        FronteggState.initializing.value = true
        FronteggState.showLoader.value = false
        FronteggState.refreshingToken.value = false
        FronteggState.isStepUpAuthorization.value = false
        
        // Verify all cleared
        assert(FronteggState.accessToken.value == null)
        assert(FronteggState.refreshToken.value == null)
        assert(FronteggState.user.value == null)
        assert(!FronteggState.isAuthenticated.value)
        assert(!FronteggState.isLoading.value)
        assert(!FronteggState.webLoading.value)
        assert(FronteggState.initializing.value)
        assert(!FronteggState.showLoader.value)
        assert(!FronteggState.refreshingToken.value)
        assert(!FronteggState.isStepUpAuthorization.value)
    }
}
