package com.frontegg.android.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.Tenant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    private var userGeneration = 0

    /** Forgets the previous user's switch, including the result of one still running. */
    fun reset() {
        userGeneration++
        mutableUiState.value = TenantSwitcherUiState()
    }

    /**
     * Switches to [tenant] unless a switch is already running.
     * [onSwitched] runs on the main thread once the new tenant's tokens are in place.
     */
    fun switchTenant(tenant: Tenant, onSwitched: (Tenant) -> Unit = {}) {
        val tenantId = tenant.tenantIdOrNull() ?: return
        if (mutableUiState.value.switchingTenantId != null) return

        mutableUiState.value = TenantSwitcherUiState(switchingTenantId = tenantId)
        val generation = userGeneration
        auth.switchTenant(tenantId) switched@{ success ->
            if (generation != userGeneration) return@switched
            mutableUiState.value = TenantSwitcherUiState(failedTenantId = if (success) null else tenantId)
            if (success) onSwitched(tenant)
        }
    }
}

/**
 * Keeps [FronteggTenantSwitcher]'s [switcherState] across configuration changes, so a running switch
 * stays the only one. A finished switch goes to the listeners registered at that moment; with none it is dropped.
 * Each change of signed-in user resets the switcher, even while not shown.
 */
internal class TenantSwitcherViewModel(auth: FronteggAuth) : ViewModel() {

    val switcherState = TenantSwitcherState(auth)

    private val switchListeners = mutableListOf<(Tenant) -> Unit>()

    init {
        viewModelScope.launch {
            auth.user.stateFlow.map { user -> user?.idOrNull() }.distinctUntilChanged().collect {
                switcherState.reset()
            }
        }
    }

    fun addSwitchListener(listener: (Tenant) -> Unit) {
        switchListeners += listener
    }

    fun removeSwitchListener(listener: (Tenant) -> Unit) {
        switchListeners -= listener
    }

    fun switchTenant(tenant: Tenant) {
        switcherState.switchTenant(tenant) { switched ->
            switchListeners.toList().forEach { listener -> listener(switched) }
        }
    }

    companion object {
        fun key(auth: FronteggAuth): String = "TenantSwitcherViewModel:${System.identityHashCode(auth)}"

        fun factory(auth: FronteggAuth): ViewModelProvider.Factory = viewModelFactory {
            initializer { TenantSwitcherViewModel(auth) }
        }
    }
}
