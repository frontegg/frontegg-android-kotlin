package com.frontegg.android

import android.app.Activity
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.utils.ReadOnlyObservableValue


interface FronteggAuth {
    val accessToken: ReadOnlyObservableValue<String?>
    val refreshToken: ReadOnlyObservableValue<String?>
    val user: ReadOnlyObservableValue<User?>
    val isAuthenticated: ReadOnlyObservableValue<Boolean>
    val isLoading: ReadOnlyObservableValue<Boolean>
    val initializing: ReadOnlyObservableValue<Boolean>
    val showLoader: ReadOnlyObservableValue<Boolean>
    val refreshingToken: ReadOnlyObservableValue<Boolean>

    val isMultiRegion: Boolean
    val selectedRegion: RegionConfig?

    fun login(activity: Activity, loginHint: String? = null)

    fun logout(callback: () -> Unit = {})

    fun directLoginAction(
        activity: Activity,
        type: String,
        data: String,
        callback: (() -> Unit)? = null
    )

    fun switchTenant(tenantId: String, callback: (Boolean) -> Unit = {})

    fun refreshTokenIfNeeded(): Boolean

    companion object {
        val instance: FronteggAuth
            get() {
                return FronteggApp.getInstance().auth
            }
    }
}

