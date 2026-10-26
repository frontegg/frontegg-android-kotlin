package com.frontegg.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.frontegg.android.FronteggAuth
import com.frontegg.android.fronteggAuth
import com.frontegg.android.utils.ReadOnlyObservableValue

/**
 * Collects this value as Compose [State], so composables that read it recompose when it changes.
 * ```
 * val isAuthenticated by rememberFronteggAuth().isAuthenticated.collectAsState()
 * ```
 */
@Composable
fun <T> ReadOnlyObservableValue<T>.collectAsState(): State<T> = stateFlow.collectAsState()

/**
 * The [FronteggAuth] of this app, initializing the SDK from `BuildConfig` on first use.
 */
@Composable
fun rememberFronteggAuth(): FronteggAuth {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) { applicationContext.fronteggAuth }
}
