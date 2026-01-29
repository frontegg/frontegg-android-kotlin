package com.frontegg.android.services

import org.junit.Assert.assertNotNull
import org.junit.Test

class FronteggStateTest {

    @Test
    fun `all ObservableValue properties are non-null and readable`() {
        assertNotNull(FronteggState.accessToken)
        assertNotNull(FronteggState.refreshToken)
        assertNotNull(FronteggState.user)
        assertNotNull(FronteggState.isAuthenticated)
        assertNotNull(FronteggState.isLoading)
        assertNotNull(FronteggState.webLoading)
        assertNotNull(FronteggState.initializing)
        assertNotNull(FronteggState.showLoader)
        assertNotNull(FronteggState.refreshingToken)
        assertNotNull(FronteggState.isStepUpAuthorization)
        // Smoke test: read values (may be null for token/user)
        FronteggState.accessToken.value
        FronteggState.isAuthenticated.value
    }
}
