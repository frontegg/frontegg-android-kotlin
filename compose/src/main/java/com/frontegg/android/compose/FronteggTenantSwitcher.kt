package com.frontegg.android.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.Tenant

/**
 * Lists the signed-in user's tenants, marks the active one, and switches tenant on tap.
 *
 * @param onTenantSwitched called once the switch has finished and the new tenant's tokens are in place.
 */
@Composable
fun FronteggTenantSwitcher(
    modifier: Modifier = Modifier,
    auth: FronteggAuth = rememberFronteggAuth(),
    onTenantSwitched: (Tenant) -> Unit = {},
) {
    val user by auth.user.collectAsState()
    val switcherState = remember(auth) { TenantSwitcherState(auth) }
    val uiState by switcherState.uiState.collectAsState()

    FronteggTenantSwitcher(
        tenants = user?.tenantsOrEmpty().orEmpty(),
        activeTenantId = user?.activeTenantIdOrNull(),
        uiState = uiState,
        onTenantSelected = { tenant -> switcherState.switchTenant(tenant, onTenantSwitched) },
        modifier = modifier,
    )
}

/**
 * Stateless tenant switcher: renders [tenants] and reports taps through [onTenantSelected].
 */
@Composable
fun FronteggTenantSwitcher(
    tenants: List<Tenant>,
    activeTenantId: String?,
    uiState: TenantSwitcherUiState,
    onTenantSelected: (Tenant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unnamedTenant = stringResource(R.string.frontegg_tenant_switcher_unnamed_tenant)
    val isSwitching = uiState.switchingTenantId != null

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.frontegg_tenant_switcher_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (tenants.isEmpty()) {
            Text(
                text = stringResource(R.string.frontegg_tenant_switcher_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        tenants.forEach { tenant ->
            val tenantId = tenant.tenantIdOrNull()
            val isActive = tenantId == activeTenantId
            TenantRow(
                tenantName = tenant.nameOrNull() ?: unnamedTenant,
                isActive = isActive,
                isSwitching = tenantId == uiState.switchingTenantId,
                enabled = !isSwitching && !isActive,
                onClick = { onTenantSelected(tenant) },
            )
        }

        val failedTenant = tenants.firstOrNull { it.tenantIdOrNull() == uiState.failedTenantId }
        if (failedTenant != null) {
            Text(
                text = stringResource(
                    R.string.frontegg_tenant_switcher_failed,
                    failedTenant.nameOrNull() ?: unnamedTenant,
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TenantRow(
    tenantName: String,
    isActive: Boolean,
    isSwitching: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(tenantName) },
        supportingContent = if (isActive) {
            { Text(stringResource(R.string.frontegg_tenant_switcher_active)) }
        } else {
            null
        },
        trailingContent = {
            if (isSwitching) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                RadioButton(selected = isActive, onClick = null)
            }
        },
        modifier = Modifier.clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
    )
}
