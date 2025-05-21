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
    val isStepUpAuthorization: ObservableValue<Boolean> = ObservableValue(false)
}