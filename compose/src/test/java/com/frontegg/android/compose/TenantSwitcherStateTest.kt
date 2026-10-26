package com.frontegg.android.compose

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.Tenant
import com.frontegg.android.models.User
import com.frontegg.android.utils.ObservableValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TenantSwitcherStateTest {

    private val signedInUser = ObservableValue<User?>(null)
    private val auth = mockk<FronteggAuth> {
        every { user } returns signedInUser
    }
    private val tenant = Tenant().apply {
        tenantId = "tenant-1"
        name = "Acme"
    }
    private val viewModelStore = ViewModelStore()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
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
    fun `switch that finishes after a reset is dropped`() {
        var completeSwitch: ((Boolean) -> Unit)? = null
        every { auth.switchTenant("tenant-1", any()) } answers { completeSwitch = secondArg() }
        val state = TenantSwitcherState(auth)
        var switchedTenant: Tenant? = null

        state.switchTenant(tenant) { switchedTenant = it }
        state.reset()
        completeSwitch!!(true)

        assertEquals(TenantSwitcherUiState(), state.uiState.value)
        assertNull(switchedTenant)
    }

    @Test
    fun `tenant without an id is ignored`() {
        val state = TenantSwitcherState(auth)

        state.switchTenant(Tenant())

        verify(exactly = 0) { auth.switchTenant(any(), any()) }
        assertEquals(TenantSwitcherUiState(), state.uiState.value)
    }

    @Test
    fun `a switch in progress stays the only one after a configuration change`() {
        every { auth.switchTenant(any(), any()) } answers { }
        val firstViewModel = switcherViewModel()
        firstViewModel.switchTenant(tenant)

        val recreatedViewModel = switcherViewModel()
        recreatedViewModel.switchTenant(Tenant().apply { tenantId = "tenant-2" })

        assertSame(firstViewModel, recreatedViewModel)
        assertEquals("tenant-1", recreatedViewModel.switcherState.uiState.value.switchingTenantId)
        verify(exactly = 1) { auth.switchTenant(any(), any()) }
    }

    @Test
    fun `a switch goes to the listeners registered when it finishes`() {
        var completeSwitch: ((Boolean) -> Unit)? = null
        every { auth.switchTenant("tenant-1", any()) } answers { completeSwitch = secondArg() }
        val viewModel = switcherViewModel()
        var switchedTenant: Tenant? = null
        viewModel.addSwitchListener { switchedTenant = it }

        viewModel.switchTenant(tenant)
        assertNull(switchedTenant)
        completeSwitch!!(true)

        assertEquals(tenant, switchedTenant)
    }

    @Test
    fun `a switch that finishes with no listener is dropped`() {
        var completeSwitch: ((Boolean) -> Unit)? = null
        every { auth.switchTenant("tenant-1", any()) } answers { completeSwitch = secondArg() }
        val viewModel = switcherViewModel()
        var leftScreenTenant: Tenant? = null
        val leftScreenListener: (Tenant) -> Unit = { leftScreenTenant = it }
        viewModel.addSwitchListener(leftScreenListener)

        viewModel.switchTenant(tenant)
        viewModel.removeSwitchListener(leftScreenListener)
        completeSwitch!!(true)
        var reopenedScreenTenant: Tenant? = null
        viewModel.addSwitchListener { reopenedScreenTenant = it }

        assertNull(leftScreenTenant)
        assertNull(reopenedScreenTenant)
    }

    @Test
    fun `a different user resets the switcher and drops its running switch`() = runTest {
        var completeSwitch: ((Boolean) -> Unit)? = null
        every { auth.switchTenant("tenant-1", any()) } answers { completeSwitch = secondArg() }
        signedInUser.value = user("user-1")
        val viewModel = switcherViewModel()
        runCurrent()
        var switchedTenant: Tenant? = null
        viewModel.addSwitchListener { switchedTenant = it }
        viewModel.switchTenant(tenant)

        signedInUser.value = user("user-2")
        runCurrent()
        completeSwitch!!(true)

        assertEquals(TenantSwitcherUiState(), viewModel.switcherState.uiState.value)
        assertNull(switchedTenant)
    }

    @Test
    fun `an update of the same user keeps the running switch`() = runTest {
        var completeSwitch: ((Boolean) -> Unit)? = null
        every { auth.switchTenant("tenant-1", any()) } answers { completeSwitch = secondArg() }
        signedInUser.value = user("user-1")
        val viewModel = switcherViewModel()
        runCurrent()
        var switchedTenant: Tenant? = null
        viewModel.addSwitchListener { switchedTenant = it }
        viewModel.switchTenant(tenant)

        signedInUser.value = user("user-1").apply { name = "Renamed" }
        runCurrent()
        assertEquals("tenant-1", viewModel.switcherState.uiState.value.switchingTenantId)
        completeSwitch!!(true)

        assertEquals(tenant, switchedTenant)
    }

    @Test
    fun `the same user signing out and back in while not shown resets the switcher`() = runTest {
        every { auth.switchTenant("tenant-1", any()) } answers { secondArg<(Boolean) -> Unit>()(false) }
        signedInUser.value = user("user-1")
        val viewModel = switcherViewModel()
        runCurrent()
        viewModel.switchTenant(tenant)
        assertEquals(TenantSwitcherUiState(failedTenantId = "tenant-1"), viewModel.switcherState.uiState.value)

        signedInUser.value = null
        runCurrent()
        signedInUser.value = user("user-1")
        runCurrent()

        assertEquals(TenantSwitcherUiState(), viewModel.switcherState.uiState.value)
    }

    private fun user(userId: String): User = User().apply { id = userId }

    private fun switcherViewModel(): TenantSwitcherViewModel =
        ViewModelProvider(viewModelStore, TenantSwitcherViewModel.factory(auth))[TenantSwitcherViewModel::class.java]
}
