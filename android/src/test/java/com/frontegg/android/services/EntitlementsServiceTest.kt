package com.frontegg.android.services

import android.util.Log
import com.frontegg.android.models.EntitlementState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EntitlementsServiceTest {

    private lateinit var mockApi: Api
    private lateinit var service: EntitlementsService

    @Before
    fun setUp() {
        mockApi = mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    @Test
    fun `state is empty initially when enabled`() {
        service = EntitlementsService(mockApi, enabled = true)
        assertTrue(service.state.featureKeys.isEmpty())
        assertTrue(service.state.permissionKeys.isEmpty())
    }

    @Test
    fun `load when disabled returns false and does not call api`() {
        service = EntitlementsService(mockApi, enabled = false)
        val result = service.load("token")
        assertFalse(result)
        verify(exactly = 0) { mockApi.getUserEntitlements(any()) }
    }

    @Test
    fun `load when enabled and api returns null returns false`() {
        every { mockApi.getUserEntitlements(accessTokenOverride = "token") } returns null
        service = EntitlementsService(mockApi, enabled = true)
        val result = service.load("token")
        assertFalse(result)
        verify(exactly = 1) { mockApi.getUserEntitlements(accessTokenOverride = "token") }
    }

    @Test
    fun `load when enabled and api returns state updates state and returns true`() {
        val state = EntitlementState(
            featureKeys = setOf("sso", "mfa"),
            permissionKeys = setOf("fe.secure.*")
        )
        every { mockApi.getUserEntitlements(accessTokenOverride = "token") } returns state
        service = EntitlementsService(mockApi, enabled = true)
        val result = service.load("token")
        assertTrue(result)
        assertEquals(setOf("sso", "mfa"), service.state.featureKeys)
        assertEquals(setOf("fe.secure.*"), service.state.permissionKeys)
    }

    @Test
    fun `clear resets state to empty`() {
        val state = EntitlementState(
            featureKeys = setOf("sso"),
            permissionKeys = setOf("fe.secure.*")
        )
        every { mockApi.getUserEntitlements(any()) } returns state
        service = EntitlementsService(mockApi, enabled = true)
        service.load("token")
        service.clear()
        assertTrue(service.state.featureKeys.isEmpty())
        assertTrue(service.state.permissionKeys.isEmpty())
    }

    @Test
    fun `checkFeature when disabled returns ENTITLEMENTS_DISABLED`() {
        service = EntitlementsService(mockApi, enabled = false)
        val result = service.checkFeature("sso")
        assertFalse(result.isEntitled)
        assertEquals("ENTITLEMENTS_DISABLED", result.justification)
    }

    @Test
    fun `checkFeature when enabled and feature missing returns MISSING_FEATURE`() {
        service = EntitlementsService(mockApi, enabled = true)
        val result = service.checkFeature("sso")
        assertFalse(result.isEntitled)
        assertEquals("MISSING_FEATURE", result.justification)
    }

    @Test
    fun `checkFeature when enabled and feature present returns entitled`() {
        every { mockApi.getUserEntitlements(any()) } returns EntitlementState(
            featureKeys = setOf("sso"),
            permissionKeys = emptySet()
        )
        service = EntitlementsService(mockApi, enabled = true)
        service.load("token")
        val result = service.checkFeature("sso")
        assertTrue(result.isEntitled)
        assertEquals(null, result.justification)
    }

    @Test
    fun `checkPermission when disabled returns ENTITLEMENTS_DISABLED`() {
        service = EntitlementsService(mockApi, enabled = false)
        val result = service.checkPermission("fe.secure.*")
        assertFalse(result.isEntitled)
        assertEquals("ENTITLEMENTS_DISABLED", result.justification)
    }

    @Test
    fun `checkPermission when enabled and permission missing returns MISSING_PERMISSION`() {
        service = EntitlementsService(mockApi, enabled = true)
        val result = service.checkPermission("fe.secure.*")
        assertFalse(result.isEntitled)
        assertEquals("MISSING_PERMISSION", result.justification)
    }

    @Test
    fun `checkPermission when enabled and permission present returns entitled`() {
        every { mockApi.getUserEntitlements(any()) } returns EntitlementState(
            featureKeys = emptySet(),
            permissionKeys = setOf("fe.secure.*")
        )
        service = EntitlementsService(mockApi, enabled = true)
        service.load("token")
        val result = service.checkPermission("fe.secure.*")
        assertTrue(result.isEntitled)
        assertEquals(null, result.justification)
    }
}
