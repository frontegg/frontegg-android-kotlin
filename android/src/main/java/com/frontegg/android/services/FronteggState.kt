package com.frontegg.android.services

import com.frontegg.android.models.User
import com.frontegg.android.utils.ObservableValue

object FronteggState {
    val accessToken: ObservableValue<String?> = ObservableValue(null)
    val refreshToken: ObservableValue<String?> = ObservableValue(null)
    val user: ObservableValue<User?> = ObservableValue(null)
    val isAuthenticated: ObservableValue<Boolean> = ObservableValue(false)
    val isLoading: ObservableValue<Boolean> = ObservableValue(true)
    val webLoading: ObservableValue<Boolean> = ObservableValue(false)
    val initializing: ObservableValue<Boolean> = ObservableValue(true)
    val showLoader: ObservableValue<Boolean> = ObservableValue(true)
    val refreshingToken: ObservableValue<Boolean> = ObservableValue(false)
    val isOfflineMode: ObservableValue<Boolean> = ObservableValue(false)
    val isStepUpAuthorization: ObservableValue<Boolean> = ObservableValue(false)

    fun reset() {
        accessToken.value = null
        refreshToken.value = null
        user.value = null
        isAuthenticated.value = false
        isLoading.value = true
        webLoading.value = false
        initializing.value = true
        showLoader.value = true
        refreshingToken.value = false
        isStepUpAuthorization.value = false
    }
}