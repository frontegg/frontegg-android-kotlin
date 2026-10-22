package com.frontegg.android.compose

import android.app.Activity
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.frontegg.android.FronteggAuth
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.exceptions.WebAuthnAlreadyRegisteredInLocalDeviceException
import com.frontegg.android.models.User
import com.frontegg.android.models.UserSession
import com.frontegg.android.services.SessionsService
import com.frontegg.android.utils.ObservableValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityCenterStateTest {

    private val sessionsService = mockk<SessionsService>()
    private val signedInUser = ObservableValue<User?>(null)
    private val auth = mockk<FronteggAuth> {
        every { sessions } returns sessionsService
        every { user } returns signedInUser
    }

    private val currentSession = UserSession(id = "current", current = true)
    private val otherSession = UserSession(id = "other")
    private val viewModelStore = ViewModelStore()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

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
    fun `a new load replaces a running one, whose result is dropped`() = runTest {
        val staleLoad = CompletableDeferred<List<UserSession>>()
        val loads = ArrayDeque(listOf(staleLoad, CompletableDeferred(listOf(currentSession))))
        coEvery { sessionsService.getActiveSessions() } coAnswers { loads.removeFirst().await() }
        val state = SecurityCenterState(auth, this)

        state.loadSessions()
        runCurrent()
        state.loadSessions()
        advanceUntilIdle()
        staleLoad.complete(listOf(otherSession))
        advanceUntilIdle()

        assertEquals(listOf(currentSession), state.uiState.value.sessions)
        assertFalse(state.uiState.value.isLoadingSessions)
    }

    @Test
    fun `reset drops the sessions and a running load`() = runTest {
        val pendingLoad = CompletableDeferred<List<UserSession>>()
        coEvery { sessionsService.getActiveSessions() } coAnswers { pendingLoad.await() }
        val state = SecurityCenterState(auth, this)

        state.loadSessions()
        runCurrent()
        state.reset()
        pendingLoad.complete(listOf(currentSession, otherSession))
        advanceUntilIdle()

        assertEquals(emptyList<UserSession>(), state.uiState.value.sessions)
        assertFalse(state.uiState.value.isLoadingSessions)
    }

    @Test
    fun `a revoke that finishes after reset leaves the new state alone`() = runTest {
        val pendingRevoke = CompletableDeferred<List<String>>()
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeOtherSessions() } coAnswers { pendingRevoke.await() }
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeOtherSessions()
        runCurrent()
        state.reset()
        assertFalse(state.uiState.value.isRevokingOtherSessions)
        state.loadSessions()
        advanceUntilIdle()
        pendingRevoke.complete(listOf("other"))
        advanceUntilIdle()

        assertEquals(listOf(currentSession, otherSession), state.uiState.value.sessions)
        assertFalse(state.uiState.value.isRevokingOtherSessions)
        assertFalse(state.uiState.value.sessionActionFailed)
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
        coEvery { sessionsService.revokeSession("other") } throws FronteggException("forbidden")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeSession(otherSession)
        advanceUntilIdle()

        assertEquals(listOf(currentSession, otherSession), state.uiState.value.sessions)
        assertTrue(state.uiState.value.sessionActionFailed)
    }

    @Test
    fun `a successful revoke does not clear the failure of another revoke`() = runTest {
        val thirdSession = UserSession(id = "third")
        val failingRevoke = CompletableDeferred<Unit>()
        val succeedingRevoke = CompletableDeferred<Unit>()
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession, thirdSession)
        coEvery { sessionsService.revokeSession("other") } coAnswers { failingRevoke.await() }
        coEvery { sessionsService.revokeSession("third") } coAnswers { succeedingRevoke.await() }
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeSession(otherSession)
        state.revokeSession(thirdSession)
        runCurrent()
        failingRevoke.completeExceptionally(IOException("offline"))
        advanceUntilIdle()
        succeedingRevoke.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(currentSession, otherSession), state.uiState.value.sessions)
        assertTrue(state.uiState.value.sessionActionFailed)
    }

    @Test
    fun `a failed revokeOtherSessions reloads the sessions`() = runTest {
        coEvery { sessionsService.getActiveSessions() } returnsMany listOf(
            listOf(currentSession, otherSession, UserSession(id = "third")),
            listOf(currentSession, otherSession),
        )
        coEvery { sessionsService.revokeOtherSessions() } throws IOException("offline")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeOtherSessions()
        advanceUntilIdle()

        assertEquals(listOf(currentSession, otherSession), state.uiState.value.sessions)
        assertTrue(state.uiState.value.sessionActionFailed)
    }

    @Test
    fun `a failed revokeOtherSessions keeps the list when the reload fails too`() = runTest {
        var loads = 0
        coEvery { sessionsService.getActiveSessions() } coAnswers {
            loads++
            if (loads == 1) listOf(currentSession, otherSession) else throw IOException("offline")
        }
        coEvery { sessionsService.revokeOtherSessions() } throws IOException("offline")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeOtherSessions()
        advanceUntilIdle()

        assertEquals(listOf(currentSession, otherSession), state.uiState.value.sessions)
        assertFalse(state.uiState.value.sessionsLoadFailed)
        assertTrue(state.uiState.value.sessionActionFailed)
    }

    @Test
    fun `reset keeps a sign-out that is still running`() = runTest {
        every { auth.logout(any()) } returns Unit
        val state = SecurityCenterState(auth, this)

        state.signOut()
        state.reset()

        assertTrue(state.uiState.value.isSigningOut)
    }

    @Test
    fun `sessions cannot be signed out while this device session is unknown`() = runTest {
        coEvery { sessionsService.getActiveSessions() } returns listOf(otherSession, UserSession(id = "third"))
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeSession(otherSession)
        advanceUntilIdle()

        assertFalse(state.uiState.value.canRevokeSessions)
        assertFalse(state.uiState.value.hasOtherSessions)
        coVerify(exactly = 0) { sessionsService.revokeSession(any()) }
    }

    @Test
    fun `revokeOtherSessions keeps only the current session`() = runTest {
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeOtherSessions() } returns listOf("other")
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
    fun `a session cannot be signed out while the other sessions are being signed out`() = runTest {
        val pendingRevoke = CompletableDeferred<List<String>>()
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeOtherSessions() } coAnswers { pendingRevoke.await() }
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeOtherSessions()
        runCurrent()
        state.revokeSession(otherSession)

        assertTrue(state.uiState.value.revokingSessionIds.isEmpty())
        pendingRevoke.complete(listOf("other"))
        advanceUntilIdle()
        coVerify(exactly = 0) { sessionsService.revokeSession(any()) }
    }

    @Test
    fun `other sessions cannot be signed out while a session is being signed out`() = runTest {
        val pendingRevoke = CompletableDeferred<Unit>()
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeSession("other") } coAnswers { pendingRevoke.await() }
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeSession(otherSession)
        runCurrent()
        state.revokeOtherSessions()

        assertFalse(state.uiState.value.isRevokingOtherSessions)
        pendingRevoke.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 0) { sessionsService.revokeOtherSessions() }
    }

    @Test
    fun `leaving the screen does not cancel a running revokeOtherSessions`() = runTest {
        val pendingRevoke = CompletableDeferred<Unit>()
        var revokeFinished = false
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeOtherSessions() } coAnswers {
            pendingRevoke.await()
            revokeFinished = true
            listOf("other")
        }
        val screenScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val state = SecurityCenterState(auth, screenScope)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeOtherSessions()
        runCurrent()
        screenScope.cancel()
        pendingRevoke.complete(Unit)
        advanceUntilIdle()

        assertTrue(revokeFinished)
    }

    @Test
    fun `other sessions cannot be signed out after a failed reload`() = runTest {
        coEvery { sessionsService.getActiveSessions() }
            .returns(listOf(currentSession, otherSession))
            .andThenThrows(IOException("offline"))
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()
        assertTrue(state.uiState.value.hasOtherSessions)

        state.loadSessions()
        advanceUntilIdle()

        assertTrue(state.uiState.value.sessionsLoadFailed)
        assertFalse(state.uiState.value.hasOtherSessions)
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

        every { auth.registerPasskeys(activity, any()) } answers {
            secondArg<((Exception?) -> Unit)?>()?.invoke(CreateCredentialCancellationException())
        }
        state.registerPasskey(activity)
        assertEquals(PasskeyRegistrationStatus.IDLE, state.uiState.value.passkeyRegistration)
    }

    @Test
    fun `a passkey registration that finishes after reset leaves the new state alone`() = runTest {
        val activity = mockk<Activity>()
        var finishRegistration: ((Exception?) -> Unit)? = null
        every { auth.registerPasskeys(activity, any()) } answers { finishRegistration = secondArg() }
        val state = SecurityCenterState(auth, this)

        state.registerPasskey(activity)
        state.reset()
        assertEquals(PasskeyRegistrationStatus.IDLE, state.uiState.value.passkeyRegistration)
        finishRegistration?.invoke(null)

        assertEquals(PasskeyRegistrationStatus.IDLE, state.uiState.value.passkeyRegistration)
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

    @Test
    fun `a session load that finishes after signOut is dropped`() = runTest {
        val pendingLoad = CompletableDeferred<List<UserSession>>()
        coEvery { sessionsService.getActiveSessions() } coAnswers { pendingLoad.await() }
        every { auth.logout(any()) } answers { firstArg<() -> Unit>()() }
        val state = SecurityCenterState(auth, this)

        state.loadSessions()
        runCurrent()
        state.signOut()
        pendingLoad.complete(listOf(currentSession))
        advanceUntilIdle()

        assertEquals(SecurityCenterUiState(), state.uiState.value)
    }

    @Test
    fun `revokeOtherSessions keeps running and stays the only one after a configuration change`() = runTest {
        val pendingRevoke = CompletableDeferred<List<String>>()
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeOtherSessions() } coAnswers { pendingRevoke.await() }
        signedInUser.value = user("user-1")
        val firstViewModel = securityCenterViewModel()
        advanceUntilIdle()
        firstViewModel.securityCenterState.revokeOtherSessions()
        runCurrent()

        val recreatedViewModel = securityCenterViewModel()
        recreatedViewModel.securityCenterState.revokeOtherSessions()
        assertTrue(recreatedViewModel.securityCenterState.uiState.value.isRevokingOtherSessions)
        pendingRevoke.complete(listOf("other"))
        advanceUntilIdle()

        assertSame(firstViewModel, recreatedViewModel)
        assertEquals(listOf(currentSession), recreatedViewModel.securityCenterState.uiState.value.sessions)
        coVerify(exactly = 1) { sessionsService.revokeOtherSessions() }
        coVerify(exactly = 1) { sessionsService.getActiveSessions() }
    }

    @Test
    fun `the view model loads the signed-in user's sessions and resets on sign-out while not shown`() = runTest {
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        signedInUser.value = user("user-1")
        val viewModel = securityCenterViewModel()
        advanceUntilIdle()
        assertEquals(listOf(currentSession, otherSession), viewModel.securityCenterState.uiState.value.sessions)

        signedInUser.value = null
        advanceUntilIdle()

        assertEquals(SecurityCenterUiState(), viewModel.securityCenterState.uiState.value)
        coVerify(exactly = 1) { sessionsService.getActiveSessions() }
    }

    @Test
    fun `the same user signing out and back in while not shown starts over`() = runTest {
        coEvery { sessionsService.getActiveSessions() } returnsMany listOf(
            listOf(currentSession, otherSession),
            listOf(currentSession),
        )
        coEvery { sessionsService.revokeSession("other") } throws IOException("offline")
        signedInUser.value = user("user-1")
        val viewModel = securityCenterViewModel()
        advanceUntilIdle()
        viewModel.securityCenterState.revokeSession(otherSession)
        advanceUntilIdle()
        assertTrue(viewModel.securityCenterState.uiState.value.sessionActionFailed)

        signedInUser.value = null
        runCurrent()
        signedInUser.value = user("user-1")
        advanceUntilIdle()

        val uiState = viewModel.securityCenterState.uiState.value
        assertEquals(listOf(currentSession), uiState.sessions)
        assertFalse(uiState.sessionActionFailed)
        coVerify(exactly = 2) { sessionsService.getActiveSessions() }
    }

    @Test
    fun `a stale load does not bring back a session revoked meanwhile`() = runTest {
        val staleLoad = CompletableDeferred<List<UserSession>>()
        val loads = ArrayDeque(listOf(CompletableDeferred(listOf(currentSession, otherSession)), staleLoad))
        coEvery { sessionsService.getActiveSessions() } coAnswers { loads.removeFirst().await() }
        coEvery { sessionsService.revokeSession("other") } returns Unit
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.loadSessions()
        runCurrent()
        state.revokeSession(otherSession)
        advanceUntilIdle()
        staleLoad.complete(listOf(currentSession, otherSession))
        advanceUntilIdle()

        assertEquals(listOf(currentSession), state.uiState.value.sessions)
    }

    @Test
    fun `a stale load does not bring back sessions revoked by revokeOtherSessions`() = runTest {
        val staleLoad = CompletableDeferred<List<UserSession>>()
        val loads = ArrayDeque(listOf(CompletableDeferred(listOf(currentSession, otherSession)), staleLoad))
        coEvery { sessionsService.getActiveSessions() } coAnswers { loads.removeFirst().await() }
        coEvery { sessionsService.revokeOtherSessions() } returns listOf("other")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.loadSessions()
        runCurrent()
        state.revokeOtherSessions()
        advanceUntilIdle()
        staleLoad.complete(listOf(currentSession, otherSession))
        advanceUntilIdle()

        assertEquals(listOf(currentSession), state.uiState.value.sessions)
    }

    @Test
    fun `a stale load does not bring back a session revokeOtherSessions revoked before it was shown`() = runTest {
        val lateSession = UserSession(id = "late")
        val staleLoad = CompletableDeferred<List<UserSession>>()
        val loads = ArrayDeque(listOf(CompletableDeferred(listOf(currentSession, otherSession)), staleLoad))
        coEvery { sessionsService.getActiveSessions() } coAnswers { loads.removeFirst().await() }
        coEvery { sessionsService.revokeOtherSessions() } returns listOf("other", "late")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.loadSessions()
        runCurrent()
        state.revokeOtherSessions()
        advanceUntilIdle()
        staleLoad.complete(listOf(currentSession, otherSession, lateSession))
        advanceUntilIdle()

        assertEquals(listOf(currentSession), state.uiState.value.sessions)
    }

    @Test
    fun `the reload after a failed revokeOtherSessions leaves out a session revoked before`() = runTest {
        val thirdSession = UserSession(id = "third")
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession, thirdSession)
        coEvery { sessionsService.revokeSession("other") } returns Unit
        coEvery { sessionsService.revokeOtherSessions() } throws IOException("offline")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()
        state.revokeSession(otherSession)
        advanceUntilIdle()

        state.revokeOtherSessions()
        advanceUntilIdle()

        assertEquals(listOf(currentSession, thirdSession), state.uiState.value.sessions)
        assertTrue(state.uiState.value.sessionActionFailed)
    }

    @Test
    fun `reset forgets the revoked sessions`() = runTest {
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeSession("other") } returns Unit
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()
        state.revokeSession(otherSession)
        advanceUntilIdle()

        state.reset()
        state.loadSessions()
        advanceUntilIdle()

        assertEquals(listOf(currentSession, otherSession), state.uiState.value.sessions)
    }

    @Test
    fun `onShown reloads the sessions of a signed-in user`() = runTest {
        coEvery { sessionsService.getActiveSessions() }
            .throws(IOException("offline"))
            .andThen(listOf(currentSession, otherSession))
        signedInUser.value = user("user-1")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()
        assertTrue(state.uiState.value.sessionsLoadFailed)

        state.onShown(isFirstShow = true)
        advanceUntilIdle()

        assertFalse(state.uiState.value.sessionsLoadFailed)
        assertEquals(listOf(currentSession, otherSession), state.uiState.value.sessions)
    }

    @Test
    fun `onShown does not load the sessions without a signed-in user`() = runTest {
        val state = SecurityCenterState(auth, this)

        state.onShown(isFirstShow = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { sessionsService.getActiveSessions() }
        assertFalse(state.uiState.value.isLoadingSessions)
    }

    @Test
    fun `onShown does not restart a running load`() = runTest {
        val pendingLoad = CompletableDeferred<List<UserSession>>()
        coEvery { sessionsService.getActiveSessions() } coAnswers { pendingLoad.await() }
        signedInUser.value = user("user-1")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        runCurrent()

        state.onShown(isFirstShow = true)
        pendingLoad.complete(listOf(currentSession))
        advanceUntilIdle()

        assertEquals(listOf(currentSession), state.uiState.value.sessions)
        coVerify(exactly = 1) { sessionsService.getActiveSessions() }
    }

    @Test
    fun `a first show clears the last result messages`() = runTest {
        val activity = mockk<Activity>()
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeSession("other") } throws IOException("offline")
        every { auth.registerPasskeys(activity, any()) } answers {
            secondArg<((Exception?) -> Unit)?>()?.invoke(IllegalStateException("failed"))
        }
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()
        state.revokeSession(otherSession)
        advanceUntilIdle()
        state.registerPasskey(activity)
        assertTrue(state.uiState.value.sessionActionFailed)
        assertEquals(PasskeyRegistrationStatus.FAILED, state.uiState.value.passkeyRegistration)

        state.onShown(isFirstShow = true)

        assertFalse(state.uiState.value.sessionActionFailed)
        assertEquals(PasskeyRegistrationStatus.IDLE, state.uiState.value.passkeyRegistration)
    }

    @Test
    fun `a show after a configuration change keeps the messages and reloads the sessions`() = runTest {
        val activity = mockk<Activity>()
        coEvery { sessionsService.getActiveSessions() } returns listOf(currentSession, otherSession)
        coEvery { sessionsService.revokeSession("other") } throws IOException("offline")
        every { auth.registerPasskeys(activity, any()) } answers {
            secondArg<((Exception?) -> Unit)?>()?.invoke(IllegalStateException("failed"))
        }
        signedInUser.value = user("user-1")
        val state = SecurityCenterState(auth, this)
        state.onShown(isFirstShow = true)
        advanceUntilIdle()
        state.revokeSession(otherSession)
        advanceUntilIdle()
        state.registerPasskey(activity)

        state.onShown(isFirstShow = false)
        advanceUntilIdle()

        assertTrue(state.uiState.value.sessionActionFailed)
        assertEquals(PasskeyRegistrationStatus.FAILED, state.uiState.value.passkeyRegistration)
        assertEquals(listOf(currentSession, otherSession), state.uiState.value.sessions)
        coVerify(exactly = 2) { sessionsService.getActiveSessions() }
    }

    @Test
    fun `a passkey registration in progress survives a rotation and blocks a second one`() = runTest {
        val startingActivity = mockk<Activity>()
        val recreatedActivity = mockk<Activity>()
        var finishRegistration: ((Exception?) -> Unit)? = null
        every { auth.registerPasskeys(startingActivity, any()) } answers { finishRegistration = secondArg() }
        val state = SecurityCenterState(auth, this)

        state.registerPasskey(startingActivity)
        state.onShown(isFirstShow = false)
        state.registerPasskey(recreatedActivity)

        assertEquals(PasskeyRegistrationStatus.IN_PROGRESS, state.uiState.value.passkeyRegistration)
        verify(exactly = 0) { auth.registerPasskeys(recreatedActivity, any()) }
        finishRegistration?.invoke(null)
        assertEquals(PasskeyRegistrationStatus.REGISTERED, state.uiState.value.passkeyRegistration)
    }

    @Test
    fun `a first show re-enables a passkey registration that never reported back`() = runTest {
        val startingActivity = mockk<Activity>()
        var finishRegistration: ((Exception?) -> Unit)? = null
        every { auth.registerPasskeys(startingActivity, any()) } answers { finishRegistration = secondArg() }
        val state = SecurityCenterState(auth, this)

        state.registerPasskey(startingActivity)
        state.onShown(isFirstShow = true)
        assertEquals(PasskeyRegistrationStatus.IDLE, state.uiState.value.passkeyRegistration)

        finishRegistration?.invoke(null)
        assertEquals(PasskeyRegistrationStatus.REGISTERED, state.uiState.value.passkeyRegistration)
    }

    @Test
    fun `a superseded passkey registration's late result is ignored`() = runTest {
        val activity = mockk<Activity>()
        val pendingRegistrations = mutableListOf<(Exception?) -> Unit>()
        every { auth.registerPasskeys(activity, any()) } answers {
            pendingRegistrations += secondArg<(Exception?) -> Unit>()
        }
        val state = SecurityCenterState(auth, this)

        state.registerPasskey(activity)
        state.onShown(isFirstShow = true)
        assertEquals(PasskeyRegistrationStatus.IDLE, state.uiState.value.passkeyRegistration)
        state.registerPasskey(activity)
        val (firstRegistration, secondRegistration) = pendingRegistrations

        firstRegistration(null)
        assertEquals(PasskeyRegistrationStatus.IN_PROGRESS, state.uiState.value.passkeyRegistration)

        secondRegistration(IllegalStateException("failed"))
        assertEquals(PasskeyRegistrationStatus.FAILED, state.uiState.value.passkeyRegistration)
    }

    @Test
    fun `a failed revokeOtherSessions does not replace a load that started meanwhile`() = runTest {
        val revokeResult = CompletableDeferred<List<String>>()
        val newerLoad = CompletableDeferred<List<UserSession>>()
        val loads = ArrayDeque(listOf(CompletableDeferred(listOf(currentSession, otherSession)), newerLoad))
        coEvery { sessionsService.getActiveSessions() } coAnswers { loads.removeFirst().await() }
        coEvery { sessionsService.revokeOtherSessions() } coAnswers { revokeResult.await() }
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeOtherSessions()
        runCurrent()
        state.loadSessions()
        runCurrent()
        revokeResult.completeExceptionally(IOException("offline"))
        advanceUntilIdle()
        newerLoad.completeExceptionally(IOException("offline"))
        advanceUntilIdle()

        assertTrue(state.uiState.value.sessionsLoadFailed)
        assertTrue(state.uiState.value.sessionActionFailed)
        coVerify(exactly = 2) { sessionsService.getActiveSessions() }
    }

    @Test
    fun `onShown does not start a second load while the reload after a failed revoke runs`() = runTest {
        val pendingReload = CompletableDeferred<List<UserSession>>()
        val loads = ArrayDeque(listOf(CompletableDeferred(listOf(currentSession, otherSession)), pendingReload))
        coEvery { sessionsService.getActiveSessions() } coAnswers { loads.removeFirst().await() }
        coEvery { sessionsService.revokeOtherSessions() } throws IOException("offline")
        signedInUser.value = user("user-1")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeOtherSessions()
        advanceUntilIdle()
        state.onShown(isFirstShow = false)
        pendingReload.complete(listOf(currentSession))
        advanceUntilIdle()

        assertEquals(listOf(currentSession), state.uiState.value.sessions)
        coVerify(exactly = 2) { sessionsService.getActiveSessions() }
    }

    @Test
    fun `a retry replaces the reload after a failed revoke`() = runTest {
        val staleReload = CompletableDeferred<List<UserSession>>()
        val loads = ArrayDeque(
            listOf(
                CompletableDeferred(listOf(currentSession, otherSession)),
                staleReload,
                CompletableDeferred(listOf(currentSession)),
            )
        )
        coEvery { sessionsService.getActiveSessions() } coAnswers { loads.removeFirst().await() }
        coEvery { sessionsService.revokeOtherSessions() } throws IOException("offline")
        val state = SecurityCenterState(auth, this)
        state.loadSessions()
        advanceUntilIdle()

        state.revokeOtherSessions()
        advanceUntilIdle()
        state.loadSessions()
        advanceUntilIdle()
        staleReload.complete(listOf(currentSession, otherSession))
        advanceUntilIdle()

        assertEquals(listOf(currentSession), state.uiState.value.sessions)
        assertFalse(state.uiState.value.isLoadingSessions)
    }

    @Test
    fun `a sign-out goes to the listeners registered when it finishes`() = runTest {
        var finishLogout: (() -> Unit)? = null
        every { auth.logout(any()) } answers { finishLogout = firstArg() }
        val viewModel = securityCenterViewModel()
        var signOuts = 0
        val listener: () -> Unit = { signOuts++ }
        viewModel.addSignOutListener(listener)

        viewModel.signOut()
        finishLogout!!()

        assertEquals(1, signOuts)
    }

    @Test
    fun `a sign-out that finishes with no listener is dropped`() = runTest {
        var finishLogout: (() -> Unit)? = null
        every { auth.logout(any()) } answers { finishLogout = firstArg() }
        val viewModel = securityCenterViewModel()
        var leftScreenSignOuts = 0
        val leftScreenListener: () -> Unit = { leftScreenSignOuts++ }
        viewModel.addSignOutListener(leftScreenListener)

        viewModel.signOut()
        viewModel.removeSignOutListener(leftScreenListener)
        finishLogout!!()
        var reopenedScreenSignOuts = 0
        viewModel.addSignOutListener { reopenedScreenSignOuts++ }

        assertEquals(0, leftScreenSignOuts)
        assertEquals(0, reopenedScreenSignOuts)
    }

    private fun user(userId: String): User = User().apply { id = userId }

    private fun securityCenterViewModel(): SecurityCenterViewModel =
        ViewModelProvider(viewModelStore, SecurityCenterViewModel.factory(auth))[SecurityCenterViewModel::class.java]
}
