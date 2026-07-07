package com.frontegg.android

import android.app.Activity
import android.content.Intent
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.StorageProvider
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression guard for FR-19725: the embedded-mode auth launches must use
 * [Activity.startActivity] (no request code), not the deprecated
 * `startActivityForResult`. The result was never consumed via `onActivityResult`,
 * so these tests lock in that each entry point still launches, targets
 * [EmbeddedAuthActivity], and no longer requests a result.
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedAuthActivityTest {

    private lateinit var activity: Activity
    private lateinit var intentSlot: CapturingSlot<Intent>

    @Before
    fun setUp() {
        activity = mockk(relaxed = true)
        intentSlot = slot()
        every { activity.startActivity(capture(intentSlot)) } returns Unit

        // The entry points build an authorize URL via `AuthorizeUrlGenerator(activity)`,
        // whose constructor reads StorageProvider + `context.fronteggAuth`. Satisfy those
        // so the real generator runs (deterministic; its only side effect lands on the
        // relaxed CredentialManager mock).
        val storage = mockk<FronteggInnerStorage>(relaxed = true)
        every { storage.baseUrl } returns "https://base.url.com"
        every { storage.clientId } returns "TestClientId"
        every { storage.applicationId } returns "TestApplicationId"
        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() } returns storage

        val authService = mockk<FronteggAuthService>(relaxed = true)
        every { authService.credentialManager } returns mockk<CredentialManager>(relaxed = true)
        mockkStatic("com.frontegg.android.FronteggAppKt")
        every { activity.fronteggAuth } returns authService
    }

    @After
    fun tearDown() {
        EmbeddedAuthActivity.onAuthFinishedCallback = null
        unmockkAll()
    }

    @Test
    fun `authenticate launches EmbeddedAuthActivity via startActivity without a request code`() {
        EmbeddedAuthActivity.authenticate(activity)

        verify(exactly = 1) { activity.startActivity(any()) }
        verify(exactly = 0) { activity.startActivityForResult(any(), any()) }
        assertEquals(
            EmbeddedAuthActivity::class.java.name,
            intentSlot.captured.component?.className
        )
    }

    @Test
    fun `directLoginAction launches EmbeddedAuthActivity via startActivity without a request code`() {
        EmbeddedAuthActivity.directLoginAction(activity, "type", "data")

        verify(exactly = 1) { activity.startActivity(any()) }
        verify(exactly = 0) { activity.startActivityForResult(any(), any()) }
        assertEquals(
            EmbeddedAuthActivity::class.java.name,
            intentSlot.captured.component?.className
        )
    }

    @Test
    fun `authenticateWithMultiFactor launches EmbeddedAuthActivity via startActivity without a request code`() {
        EmbeddedAuthActivity.authenticateWithMultiFactor(activity, "mfaLoginAction")

        verify(exactly = 1) { activity.startActivity(any()) }
        verify(exactly = 0) { activity.startActivityForResult(any(), any()) }
        assertEquals(
            EmbeddedAuthActivity::class.java.name,
            intentSlot.captured.component?.className
        )
    }

    @Test
    fun `authenticateWithStepUp launches EmbeddedAuthActivity via startActivity without a request code`() {
        EmbeddedAuthActivity.authenticateWithStepUp(activity, null)

        verify(exactly = 1) { activity.startActivity(any()) }
        verify(exactly = 0) { activity.startActivityForResult(any(), any()) }
        assertEquals(
            EmbeddedAuthActivity::class.java.name,
            intentSlot.captured.component?.className
        )
    }
}
