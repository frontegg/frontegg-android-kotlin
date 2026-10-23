package com.frontegg.android.services

import com.frontegg.android.models.Tenant
import com.frontegg.android.models.User
import com.frontegg.android.utils.SentryHelper
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class FronteggStateTest {

    @Before
    fun setUp() {
        // Reset all state before each test
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.user.value = null
        FronteggState.isAuthenticated.value = false
        FronteggState.isLoading.value = false
        FronteggState.webLoading.value = false
        FronteggState.initializing.value = true
        FronteggState.showLoader.value = false
        FronteggState.refreshingToken.value = false
        FronteggState.isStepUpAuthorization.value = false
    }

    @Test
    fun `accessToken can be set and retrieved`() {
        FronteggState.accessToken.value = "test-access-token"
        
        assert(FronteggState.accessToken.value == "test-access-token")
    }

    @Test
    fun `refreshToken can be set and retrieved`() {
        FronteggState.refreshToken.value = "test-refresh-token"
        
        assert(FronteggState.refreshToken.value == "test-refresh-token")
    }

    @Test
    fun `user can be set and retrieved`() {
        val user = User()
        user.id = "user-123"
        user.email = "test@example.com"
        user.name = "Test User"
        user.activeTenant = Tenant()
        
        FronteggState.user.value = user
        
        assert(FronteggState.user.value?.id == "user-123")
        assert(FronteggState.user.value?.email == "test@example.com")
    }

    @Test
    fun `isAuthenticated can be toggled`() {
        assert(!FronteggState.isAuthenticated.value)
        
        FronteggState.isAuthenticated.value = true
        
        assert(FronteggState.isAuthenticated.value)
        
        FronteggState.isAuthenticated.value = false
        
        assert(!FronteggState.isAuthenticated.value)
    }

    @Test
    fun `isLoading can be toggled`() {
        FronteggState.isLoading.value = true
        
        assert(FronteggState.isLoading.value)
        
        FronteggState.isLoading.value = false
        
        assert(!FronteggState.isLoading.value)
    }

    @Test
    fun `webLoading can be toggled`() {
        FronteggState.webLoading.value = true
        
        assert(FronteggState.webLoading.value)
        
        FronteggState.webLoading.value = false
        
        assert(!FronteggState.webLoading.value)
    }

    @Test
    fun `initializing default is true`() {
        // After reset, initializing should be true
        assert(FronteggState.initializing.value)
    }

    @Test
    fun `initializing can be set to false`() {
        FronteggState.initializing.value = false
        
        assert(!FronteggState.initializing.value)
    }

    @Test
    fun `showLoader can be toggled`() {
        FronteggState.showLoader.value = true
        
        assert(FronteggState.showLoader.value)
        
        FronteggState.showLoader.value = false
        
        assert(!FronteggState.showLoader.value)
    }

    @Test
    fun `refreshingToken can be toggled`() {
        FronteggState.refreshingToken.value = true
        
        assert(FronteggState.refreshingToken.value)
        
        FronteggState.refreshingToken.value = false
        
        assert(!FronteggState.refreshingToken.value)
    }

    @Test
    fun `isStepUpAuthorization can be toggled`() {
        FronteggState.isStepUpAuthorization.value = true
        
        assert(FronteggState.isStepUpAuthorization.value)
        
        FronteggState.isStepUpAuthorization.value = false
        
        assert(!FronteggState.isStepUpAuthorization.value)
    }

    @Test
    fun `clearing tokens resets authentication state`() {
        FronteggState.accessToken.value = "token"
        FronteggState.refreshToken.value = "refresh"
        FronteggState.isAuthenticated.value = true
        
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.isAuthenticated.value = false
        
        assert(FronteggState.accessToken.value == null)
        assert(FronteggState.refreshToken.value == null)
        assert(!FronteggState.isAuthenticated.value)
    }

    @Test
    fun `setting user also tracks authentication`() {
        val user = User()
        user.id = "user-456"
        user.activeTenant = Tenant()
        
        FronteggState.user.value = user
        FronteggState.isAuthenticated.value = true
        
        assert(FronteggState.user.value != null)
        assert(FronteggState.isAuthenticated.value)
    }

    @Test
    fun `state can be fully cleared`() {
        // Set all state
        FronteggState.accessToken.value = "token"
        FronteggState.refreshToken.value = "refresh"
        val user = User()
        user.activeTenant = Tenant()
        FronteggState.user.value = user
        FronteggState.isAuthenticated.value = true
        FronteggState.isLoading.value = true
        FronteggState.webLoading.value = true
        FronteggState.initializing.value = false
        FronteggState.showLoader.value = true
        FronteggState.refreshingToken.value = true
        FronteggState.isStepUpAuthorization.value = true
        
        // Clear all state
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.user.value = null
        FronteggState.isAuthenticated.value = false
        FronteggState.isLoading.value = false
        FronteggState.webLoading.value = false
        FronteggState.initializing.value = true
        FronteggState.showLoader.value = false
        FronteggState.refreshingToken.value = false
        FronteggState.isStepUpAuthorization.value = false
        
        // Verify all cleared
        assert(FronteggState.accessToken.value == null)
        assert(FronteggState.refreshToken.value == null)
        assert(FronteggState.user.value == null)
        assert(!FronteggState.isAuthenticated.value)
        assert(!FronteggState.isLoading.value)
        assert(!FronteggState.webLoading.value)
        assert(FronteggState.initializing.value)
        assert(!FronteggState.showLoader.value)
        assert(!FronteggState.refreshingToken.value)
        assert(!FronteggState.isStepUpAuthorization.value)
    }

    @Test
    fun `bindDerivedState derives showLoader and binds only once`() {
        mockkObject(SentryHelper)
        every { SentryHelper.setUser(any(), any(), any()) } returns Unit
        try {
            FronteggState.bindDerivedState()
            FronteggState.bindDerivedState()

            FronteggState.initializing.value = false
            assert(!FronteggState.showLoader.value)
            FronteggState.isLoading.value = true
            assert(FronteggState.showLoader.value)
            FronteggState.isAuthenticated.value = true
            assert(!FronteggState.showLoader.value)

            FronteggState.user.value = User().apply {
                id = "user-1"
                email = "user@test.com"
                name = "User"
            }
            verify(exactly = 1) { SentryHelper.setUser("user-1", "user@test.com", "User") }
        } finally {
            unmockkObject(SentryHelper)
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `a showLoader subscriber runs with no source lock held`() {
        FronteggState.bindDerivedState()
        FronteggState.initializing.value = false
        val otherSetterFinished = CountDownLatch(1)
        val otherSetterStarted = AtomicBoolean(false)
        var otherSetterFinishedInTime = false
        val subscription = FronteggState.showLoader.subscribe { showLoader ->
            if (showLoader.value && otherSetterStarted.compareAndSet(false, true)) {
                Thread {
                    FronteggState.isLoading.value = false
                    otherSetterFinished.countDown()
                }.start()
                otherSetterFinishedInTime = otherSetterFinished.await(5, TimeUnit.SECONDS)
            }
        }
        try {
            FronteggState.isLoading.value = true

            assert(otherSetterFinishedInTime)
            assert(!FronteggState.showLoader.value)
        } finally {
            subscription.dispose()
        }
    }

    @Test
    fun `showLoader converges to the value derived from the final inputs`() {
        FronteggState.bindDerivedState()
        FronteggState.initializing.value = false
        repeat(5000) { round ->
            val start = CountDownLatch(1)
            val togglers = listOf(FronteggState.isLoading, FronteggState.isAuthenticated).map { input ->
                Thread {
                    start.await()
                    repeat(Random.nextInt(1, 6)) { iteration -> input.value = iteration % 2 == 0 }
                }.apply { start() }
            }

            start.countDown()
            togglers.forEach { it.join() }

            val waitingForAuthentication = !FronteggState.isAuthenticated.value &&
                (FronteggState.isLoading.value || FronteggState.webLoading.value)
            val expectedShowLoader = FronteggState.initializing.value || waitingForAuthentication
            assert(FronteggState.showLoader.value == expectedShowLoader) { "round $round" }
        }
    }
}
