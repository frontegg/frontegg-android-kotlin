package com.frontegg.android.compose

import android.app.Activity
import com.frontegg.android.FronteggAuth
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.exceptions.WebAuthnAlreadyRegisteredInLocalDeviceException
import com.frontegg.android.models.UserSession
import com.frontegg.android.services.SessionsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityCenterStateTest {

    private val sessionsService = mockk<SessionsService>()
    private val auth = mockk<FronteggAuth> {
        every { sessions } returns sessionsService
    }

    private val currentSession = UserSession(id = "current", current = true)
    private val otherSession = UserSession(id = "other")

    @Test
    fun `loadSessions lists the current session first`() = runTest {
        coEvery { sessionsService.getActiveSessions() } returns listOf(otherSession, currentSession)
        val state = SecurityCenterState(auth, this)

        state.loadSessions()
        assertTrue(state.uiState.value.isLoadingSessions)
        advanceUntilIdle()

        val uiState = state.uiState.value
        assertEquals(listOf(currentSession, otherSession), uiState.sessions)
        assertFalse(uiState.isLoadingSessions)
        assertTrue(uiState.hasOtherSessions)
    }

    @Test
    fun `loadSessions reports a failure`() = runTest {
        coEvery { sessionsService.getActiveSessions() } throws IOException("offline")
        val state = SecurityCenterState(auth, this)

        state.loadSessions()
        advanceUntilIdle()

        assertTrue(state.uiState.value.sessionsLoadFailed)
        assertFalse(state.uiState.value.isLoadingSessions)
    }

    @Test
    fun `revokeSession removes the session on success`() = runTest {
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeSession("other") } returns Unit
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeSession(otherSession)
        assertEquals(setOf("other"), state.uiState.value.revokingSessionIds)
        advanceUntilIdle()

        assertEquals(listOf(currentSession), state.uiState.value.sessions)
        assertTrue(state.uiState.value.revokingSessionIds.isEmpty())
        assertFalse(state.uiState.value.sessionActionFailed)
    }

    @Test
    fun `revokeSession keeps the session and reports a failure`() = runTest {
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeSession("other") } throws FailedToAuthenticateException(error = "forbidden")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeSession(otherSession)
        advanceUntilIdle()

        assertEquals(listOf(currentSession, otherSession), state.uiState.value.sessions)
        assertTrue(state.uiState.value.sessionActionFailed)
    }

    @Test
    fun `revokeOtherSessions keeps only the current session`() = runTest {
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeOtherSessions() } returns Unit
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeOtherSessions()
        advanceUntilIdle()

        assertEquals(listOf(currentSession), state.uiState.value.sessions)
        assertFalse(state.uiState.value.hasOtherSessions)
        coVerify(exactly = 1) { sessionsService.revokeOtherSessions() }
    }

    @Test
    fun `registerPasskey maps the registration result`() = runTest {
        val activity = mockk<Activity>()
        val state = SecurityCenterState(auth, this)

        every { auth.registerPasskeys(activity, any()) } answers {
            secondArg<((Exception?) -> Unit)?>()?.invoke(null)
        }
        state.registerPasskey(activity)
        assertEquals(PasskeyRegistrationStatus.REGISTERED, state.uiState.value.passkeyRegistration)

        every { auth.registerPasskeys(activity, any()) } answers {
            secondArg<((Exception?) -> Unit)?>()?.invoke(WebAuthnAlreadyRegisteredInLocalDeviceException())
        }
        state.registerPasskey(activity)
        assertEquals(PasskeyRegistrationStatus.ALREADY_REGISTERED, state.uiState.value.passkeyRegistration)

        every { auth.registerPasskeys(activity, any()) } answers {
            secondArg<((Exception?) -> Unit)?>()?.invoke(IllegalStateException("cancelled"))
        }
        state.registerPasskey(activity)
        assertEquals(PasskeyRegistrationStatus.FAILED, state.uiState.value.passkeyRegistration)
    }

    @Test
    fun `signOut resets the state and reports completion`() = runTest {
        every { auth.logout(any()) } answers { firstArg<() -> Unit>()() }
        val state = SecurityCenterState(auth, this)
        var signedOut = false

        state.signOut { signedOut = true }

        assertTrue(signedOut)
        assertEquals(SecurityCenterUiState(), state.uiState.value)
    }
}
