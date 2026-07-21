package com.frontegg.android

import android.os.Bundle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FR-25934: the embedded auth flow used a static callback plus an un-persisted
 * `authCompleted` flag. After process death the activity is recreated from
 * savedInstanceState with `authCompleted` reset to false, which could fire a
 * spurious user-cancellation. These cover the two contained fixes: persisting the
 * completion flag, and the guard that decides whether onDestroy reports a cancel.
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedAuthActivityTest {

    // --- user-cancellation guard ---

    @Test
    fun `delivers user cancellation when finishing, not completed, not a config change`() {
        assertTrue(
            EmbeddedAuthActivity.shouldDeliverUserCancellation(
                authCompleted = false,
                isFinishing = true,
                isChangingConfigurations = false
            )
        )
    }

    @Test
    fun `does not deliver cancellation when auth already completed`() {
        assertFalse(
            EmbeddedAuthActivity.shouldDeliverUserCancellation(
                authCompleted = true,
                isFinishing = true,
                isChangingConfigurations = false
            )
        )
    }

    @Test
    fun `does not deliver cancellation during a configuration change`() {
        assertFalse(
            EmbeddedAuthActivity.shouldDeliverUserCancellation(
                authCompleted = false,
                isFinishing = true,
                isChangingConfigurations = true
            )
        )
    }

    @Test
    fun `does not deliver cancellation when not finishing`() {
        assertFalse(
            EmbeddedAuthActivity.shouldDeliverUserCancellation(
                authCompleted = false,
                isFinishing = false,
                isChangingConfigurations = false
            )
        )
    }

    // --- authCompleted persistence across process death ---

    @Test
    fun `authCompleted round-trips through the saved-state bundle`() {
        val bundle = Bundle()
        EmbeddedAuthActivity.writeAuthCompleted(bundle, true)

        assertTrue(EmbeddedAuthActivity.readAuthCompleted(bundle))
    }

    @Test
    fun `authCompleted defaults to false when absent from the bundle`() {
        assertFalse(EmbeddedAuthActivity.readAuthCompleted(Bundle()))
    }
}
