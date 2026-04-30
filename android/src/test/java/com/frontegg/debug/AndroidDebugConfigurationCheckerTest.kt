package com.frontegg.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class AndroidDebugConfigurationCheckerTest {

    private val tag = "AndroidDebugConfigurationChecker"

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `runChecks skips when host app is not debuggable`() {
        val dispatcher = RecordingDispatcher()
        val checker = AndroidDebugConfigurationChecker(
            context = contextWithDebuggable(false),
            fronteggDomain = "example.frontegg.com",
            clientId = "client-id",
            ioDispatcher = dispatcher,
        )

        checker.runChecks()

        verify(exactly = 1) {
            Log.d(tag, "ℹ️ Skipping debug configuration checks in release mode.")
        }
        verify(exactly = 0) {
            Log.d(tag, "🔍 Running Android debug configuration checks...")
        }
        assert(dispatcher.dispatchCount == 0) {
            "No coroutine should be dispatched when host app is not debuggable"
        }
    }

    @Test
    fun `runChecks proceeds when host app is debuggable`() {
        val dispatcher = RecordingDispatcher()
        val checker = AndroidDebugConfigurationChecker(
            context = contextWithDebuggable(true),
            fronteggDomain = "example.frontegg.com",
            clientId = "client-id",
            ioDispatcher = dispatcher,
        )

        checker.runChecks()

        verify(exactly = 0) {
            Log.d(tag, "ℹ️ Skipping debug configuration checks in release mode.")
        }
        verify(exactly = 1) {
            Log.d(tag, "🔍 Running Android debug configuration checks...")
        }
        assert(dispatcher.dispatchCount == 1) {
            "Exactly one coroutine should be dispatched when host app is debuggable"
        }
    }

    @Test
    fun `runChecks proceeds when FLAG_DEBUGGABLE is mixed with other flags`() {
        // Real-world ApplicationInfo.flags are bitmasks combining many flags;
        // the gate must isolate FLAG_DEBUGGABLE rather than equality-check.
        val dispatcher = RecordingDispatcher()
        val mixedFlags = ApplicationInfo.FLAG_DEBUGGABLE or
            ApplicationInfo.FLAG_INSTALLED or
            ApplicationInfo.FLAG_HAS_CODE or
            ApplicationInfo.FLAG_ALLOW_BACKUP
        val checker = AndroidDebugConfigurationChecker(
            context = contextWithFlags(mixedFlags),
            fronteggDomain = "example.frontegg.com",
            clientId = "client-id",
            ioDispatcher = dispatcher,
        )

        checker.runChecks()

        verify(exactly = 1) {
            Log.d(tag, "🔍 Running Android debug configuration checks...")
        }
        assert(dispatcher.dispatchCount == 1)
    }

    @Test
    fun `runChecks skips when other flags are set but FLAG_DEBUGGABLE is not`() {
        // A release build still has FLAG_INSTALLED, FLAG_HAS_CODE, etc.
        // The gate must NOT be tricked by any non-DEBUGGABLE bit.
        val dispatcher = RecordingDispatcher()
        val releaseFlags = ApplicationInfo.FLAG_INSTALLED or
            ApplicationInfo.FLAG_HAS_CODE or
            ApplicationInfo.FLAG_ALLOW_BACKUP
        val checker = AndroidDebugConfigurationChecker(
            context = contextWithFlags(releaseFlags),
            fronteggDomain = "example.frontegg.com",
            clientId = "client-id",
            ioDispatcher = dispatcher,
        )

        checker.runChecks()

        verify(exactly = 1) {
            Log.d(tag, "ℹ️ Skipping debug configuration checks in release mode.")
        }
        verify(exactly = 0) {
            Log.d(tag, "🔍 Running Android debug configuration checks...")
        }
        assert(dispatcher.dispatchCount == 0)
    }

    @Test
    fun `gate reads host context flags, not the SDK module BuildConfig (regression FR-24624)`() {
        // Regression for FR-24624: prior implementation read the SDK library
        // module's BuildConfig.DEBUG, which is always false in a published AAR.
        // The gate must depend ONLY on the host Context, so a debuggable host
        // triggers checks regardless of how the SDK itself was built.
        val dispatcher = RecordingDispatcher()
        val checker = AndroidDebugConfigurationChecker(
            context = contextWithDebuggable(true),
            fronteggDomain = "example.frontegg.com",
            clientId = "client-id",
            ioDispatcher = dispatcher,
        )

        checker.runChecks()

        // If the gate were still tied to the SDK's BuildConfig.DEBUG, dispatchCount
        // would be 0 here (because unit tests run against the SDK's `debug` variant
        // BuildConfig, but a real AAR consumer would always see false). Assert the
        // checks ran based on the host context flag instead.
        assert(dispatcher.dispatchCount == 1) {
            "Gate must depend on host context, not SDK module BuildConfig"
        }
    }

    private fun contextWithDebuggable(debuggable: Boolean): Context =
        contextWithFlags(if (debuggable) ApplicationInfo.FLAG_DEBUGGABLE else 0)

    private fun contextWithFlags(flagsValue: Int): Context {
        val context = mockk<Context>(relaxed = true)
        val appInfo = ApplicationInfo().apply { flags = flagsValue }
        every { context.applicationInfo } returns appInfo
        return context
    }

    /**
     * A dispatcher that records calls without actually executing the runnables —
     * keeps tests fast and free of side effects (e.g. real HTTP calls from the
     * checker's launched coroutine).
     */
    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount++
        }
    }
}
