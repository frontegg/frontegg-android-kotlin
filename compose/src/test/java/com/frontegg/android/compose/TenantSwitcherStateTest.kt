package com.frontegg.android.compose

import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TenantSwitcherStateTest {

    private val auth = mockk<FronteggAuth>()
    private val tenant = Tenant().apply {
        tenantId = "tenant-1"
        name = "Acme"
    }

    @Test
    fun `successful switch clears progress and reports the tenant`() {
        every { auth.switchTenant("tenant-1", any()) } answers { secondArg<(Boolean) -> Unit>()(true) }
        val state = TenantSwitcherState(auth)
        var switchedTenant: Tenant? = null

        state.switchTenant(tenant) { switchedTenant = it }

        assertEquals(TenantSwitcherUiState(), state.uiState.value)
        assertEquals(tenant, switchedTenant)
    }

    @Test
    fun `failed switch records the tenant and does not report it`() {
        every { auth.switchTenant("tenant-1", any()) } answers { secondArg<(Boolean) -> Unit>()(false) }
        val state = TenantSwitcherState(auth)
        var switchedTenant: Tenant? = null

        state.switchTenant(tenant) { switchedTenant = it }

        assertEquals(TenantSwitcherUiState(failedTenantId = "tenant-1"), state.uiState.value)
        assertNull(switchedTenant)
    }

    @Test
    fun `second switch is ignored while one is running`() {
        every { auth.switchTenant(any(), any()) } answers { }
        val state = TenantSwitcherState(auth)
        val otherTenant = Tenant().apply { tenantId = "tenant-2" }

        state.switchTenant(tenant)
        state.switchTenant(otherTenant)

        assertEquals("tenant-1", state.uiState.value.switchingTenantId)
        verify(exactly = 1) { auth.switchTenant(any(), any()) }
    }

    @Test
    fun `tenant without an id is ignored`() {
        val state = TenantSwitcherState(auth)

        state.switchTenant(Tenant())

        verify(exactly = 0) { auth.switchTenant(any(), any()) }
        assertEquals(TenantSwitcherUiState(), state.uiState.value)
    }
}
