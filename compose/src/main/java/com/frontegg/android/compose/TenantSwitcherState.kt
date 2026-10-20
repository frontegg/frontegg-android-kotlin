package com.frontegg.android.compose

import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.Tenant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * @property switchingTenantId the tenant a switch is in progress to, or null when idle;
 * @property failedTenantId the tenant the last switch failed for, or null.
 */
data class TenantSwitcherUiState(
    val switchingTenantId: String? = null,
    val failedTenantId: String? = null,
)

/**
 * Switches the signed-in user between tenants, one switch at a time.
 */
class TenantSwitcherState(private val auth: FronteggAuth) {

    private val mutableUiState = MutableStateFlow(TenantSwitcherUiState())
    val uiState: StateFlow<TenantSwitcherUiState> = mutableUiState.asStateFlow()

    /**
     * Switches to [tenant] unless a switch is already running.
     * [onSwitched] runs on the main thread once the new tenant's tokens are in place.
     */
    fun switchTenant(tenant: Tenant, onSwitched: (Tenant) -> Unit = {}) {
        val tenantId = tenant.tenantIdOrNull() ?: return
        if (mutableUiState.value.switchingTenantId != null) return

        mutableUiState.value = TenantSwitcherUiState(switchingTenantId = tenantId)
        auth.switchTenant(tenantId) { success ->
            mutableUiState.value = TenantSwitcherUiState(failedTenantId = if (success) null else tenantId)
            if (success) onSwitched(tenant)
        }
    }
}
