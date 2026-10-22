package com.frontegg.demo.ui.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.frontegg.android.compose.FronteggSecurityCenter
import com.frontegg.android.compose.FronteggTenantSwitcher
import com.frontegg.android.compose.collectAsState
import com.frontegg.android.compose.rememberFronteggAuth

/**
 * Hosts the android-compose components: the tenant switcher and the security center.
 */
class ComposeComponentsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ComposeComponentsScreen()
                }
            }
        }
    }
}

private enum class DemoComponent(val label: String) {
    TENANT_SWITCHER("Tenant switcher"),
    SECURITY_CENTER("Security center"),
}

@Composable
private fun ComposeComponentsScreen() {
    val auth = rememberFronteggAuth()
    val user by auth.user.collectAsState()
    var selectedComponent by rememberSaveable { mutableStateOf(DemoComponent.TENANT_SWITCHER) }
    // Gson can leave the lateinit activeTenant and tenantId unset.
    val activeTenantId = user?.let { runCatching { it.activeTenant.tenantId }.getOrNull() }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "ComposeComponentsRoot" },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DemoComponent.values().forEach { component ->
                TextButton(onClick = { selectedComponent = component }) {
                    Text(component.label)
                }
            }
        }
        Text(
            text = "Active tenant ID: $activeTenantId",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = "ComposeActiveTenantValue=$activeTenantId" },
        )
        when (selectedComponent) {
            DemoComponent.TENANT_SWITCHER -> FronteggTenantSwitcher(
                auth = auth,
                modifier = Modifier.fillMaxWidth(),
            )
            DemoComponent.SECURITY_CENTER -> FronteggSecurityCenter(
                auth = auth,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
