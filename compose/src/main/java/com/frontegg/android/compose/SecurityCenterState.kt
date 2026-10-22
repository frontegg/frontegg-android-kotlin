package com.frontegg.android.compose

import android.app.Activity
import android.util.Log
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frontegg.android.FronteggAuth
import com.frontegg.android.exceptions.WebAuthnAlreadyRegisteredInLocalDeviceException
import com.frontegg.android.models.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class PasskeyRegistrationStatus {
    IDLE,
    IN_PROGRESS,
    REGISTERED,
    ALREADY_REGISTERED,
    FAILED,
}

/**
 * @property sessions the user's active sessions, the current one first;
 * @property sessionsLoadFailed true when the last attempt to list sessions failed;
 * @property revokingSessionIds sessions being signed out right now;
 * @property sessionActionFailed true when a sign-out of a session failed; starting a new one clears it.
 */
data class SecurityCenterUiState(
    val sessions: List<UserSession> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val sessionsLoadFailed: Boolean = false,
    val revokingSessionIds: Set<String> = emptySet(),
    val isRevokingOtherSessions: Boolean = false,
    val sessionActionFailed: Boolean = false,
    val passkeyRegistration: PasskeyRegistrationStatus = PasskeyRegistrationStatus.IDLE,
    val isSigningOut: Boolean = false,
) {
    /** Sessions can be signed out only once this device's own session is known. */
    val canRevokeSessions: Boolean
        get() = sessions.any { it.current }

    val hasOtherSessions: Boolean
        get() = canRevokeSessions && !sessionsLoadFailed && sessions.any { !it.current }
}

/**
 * Loads and revokes sessions, registers passkeys and signs out for [FronteggSecurityCenter].
 * Network work runs in [scope]; results are published on [uiState].
 */
class SecurityCenterState(
    private val auth: FronteggAuth,
    private val scope: CoroutineScope,
) {

    private val mutableUiState = MutableStateFlow(SecurityCenterUiState())
    val uiState: StateFlow<SecurityCenterUiState> = mutableUiState.asStateFlow()

    private var loadSessionsJob: Job? = null
    private var userGeneration = 0
    private var passkeyAttempt = 0
    private var revokedSessionIds = emptySet<String>()

    private val isRevokingSessions: Boolean
        get() = mutableUiState.value.let { it.isRevokingOtherSessions || it.revokingSessionIds.isNotEmpty() }

    private val isRegisteringPasskey: Boolean
        get() = mutableUiState.value.passkeyRegistration == PasskeyRegistrationStatus.IN_PROGRESS

    /** Loads the sessions, replacing a load that is still running. */
    fun loadSessions() {
        mutableUiState.update { it.copy(isLoadingSessions = true, sessionsLoadFailed = false) }
        startSessionsLoad(reportsFailure = true)
    }

    private fun startSessionsLoad(reportsFailure: Boolean) {
        loadSessionsJob?.cancel()
        loadSessionsJob = scope.launch {
            val sessions = runSessionCall { auth.sessions.getActiveSessions() }
            mutableUiState.update { current ->
                when {
                    sessions != null -> current.copy(
                        isLoadingSessions = false,
                        sessionsLoadFailed = false,
                        sessions = sessionsToShow(sessions),
                    )
                    reportsFailure -> current.copy(isLoadingSessions = false, sessionsLoadFailed = true)
                    else -> current.copy(isLoadingSessions = false)
                }
            }
        }
    }

    /** Forgets everything shown for the previous user, and the results of calls still running for them. */
    fun reset() {
        userGeneration++
        revokedSessionIds = emptySet()
        loadSessionsJob?.cancel()
        // A sign-out in progress clears the user before it finishes, so it survives the reset.
        mutableUiState.value = SecurityCenterUiState(isSigningOut = mutableUiState.value.isSigningOut)
    }

    fun revokeSession(session: UserSession) {
        val current = mutableUiState.value
        val canRevoke = !session.current && current.canRevokeSessions && !current.isRevokingOtherSessions
        if (!canRevoke || session.id in current.revokingSessionIds) return
        mutableUiState.update {
            it.copy(revokingSessionIds = it.revokingSessionIds + session.id, sessionActionFailed = false)
        }

        val generation = userGeneration
        scope.launch {
            val revoked = runSessionCall { auth.sessions.revokeSession(session.id) } != null
            if (generation != userGeneration) return@launch
            if (revoked) revokedSessionIds = revokedSessionIds + session.id
            mutableUiState.update { current ->
                current.copy(
                    revokingSessionIds = current.revokingSessionIds - session.id,
                    sessions = if (revoked) current.sessions.filterNot { it.id == session.id } else current.sessions,
                    sessionActionFailed = current.sessionActionFailed || !revoked,
                )
            }
        }
    }

    fun revokeOtherSessions() {
        if (isRevokingSessions) return
        mutableUiState.update { it.copy(isRevokingOtherSessions = true, sessionActionFailed = false) }

        val generation = userGeneration
        // Read now, so a user who signs in before the request starts is never the one signed out.
        val accessToken = auth.accessToken.value
        scope.launch {
            // Once started, finish signing out even if the screen goes away.
            val revokedIds = withContext(NonCancellable) {
                runSessionCall { auth.sessions.revokeOtherSessions(accessToken) }
            }
            if (generation != userGeneration) return@launch
            val revoked = revokedIds != null
            revokedIds?.let { revokedSessionIds = revokedSessionIds + it }
            mutableUiState.update { current ->
                current.copy(
                    isRevokingOtherSessions = false,
                    sessions = if (revoked) current.sessions.filter { it.current } else current.sessions,
                    sessionActionFailed = !revoked,
                )
            }
            // Sessions signed out before the failure are gone; if the reload fails too, the list stays.
            // A load already running is newer and reports its own failure, so it is not replaced.
            if (!revoked && loadSessionsJob?.isActive != true) startSessionsLoad(reportsFailure = false)
        }
    }

    // A load that started before a revoke finished still lists the revoked sessions.
    private fun sessionsToShow(sessions: List<UserSession>): List<UserSession> =
        sessions.filterNot { it.id in revokedSessionIds }.sortedByDescending { it.current }

    /**
     * Called each time the screen is shown; [isFirstShow] is false when it comes back after a configuration change.
     * Reloads the sessions unless a load is running, and only a first show clears the last result messages.
     * A first show also re-enables a passkey registration whose result never came;
     * its late result still applies unless a newer registration has started.
     */
    internal fun onShown(isFirstShow: Boolean) {
        if (isFirstShow) {
            mutableUiState.update {
                it.copy(sessionActionFailed = false, passkeyRegistration = PasskeyRegistrationStatus.IDLE)
            }
        }
        val isSignedIn = auth.user.value?.idOrNull() != null
        if (isSignedIn && loadSessionsJob?.isActive != true) loadSessions()
    }

    /** Registers a passkey unless one is still running; that one reports its result even after a rotation. */
    fun registerPasskey(activity: Activity) {
        if (isRegisteringPasskey) return
        mutableUiState.update { it.copy(passkeyRegistration = PasskeyRegistrationStatus.IN_PROGRESS) }

        val attempt = ++passkeyAttempt
        val generation = userGeneration
        auth.registerPasskeys(activity) { error ->
            val isLatestAttempt = attempt == passkeyAttempt && generation == userGeneration
            if (!isLatestAttempt) return@registerPasskeys
            val status = when (error) {
                null -> PasskeyRegistrationStatus.REGISTERED
                is CreateCredentialCancellationException -> PasskeyRegistrationStatus.IDLE
                is WebAuthnAlreadyRegisteredInLocalDeviceException -> PasskeyRegistrationStatus.ALREADY_REGISTERED
                else -> PasskeyRegistrationStatus.FAILED
            }
            mutableUiState.update { it.copy(passkeyRegistration = status) }
        }
    }

    fun signOut(onSignedOut: () -> Unit = {}) {
        if (mutableUiState.value.isSigningOut) return
        mutableUiState.update { it.copy(isSigningOut = true) }

        auth.logout {
            reset()
            mutableUiState.update { it.copy(isSigningOut = false) }
            onSignedOut()
        }
    }

    private suspend fun <T> runSessionCall(call: suspend () -> T): T? =
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Session request failed", e)
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Session request failed", e)
            null
        }

    private companion object {
        const val TAG = "SecurityCenterState"
    }
}

/**
 * Keeps [FronteggSecurityCenter]'s [securityCenterState] and its calls in progress across configuration changes.
 * Each change of signed-in user resets the state, even while not shown, and loads the new user's sessions.
 * A finished sign-out goes to the listeners registered at that moment; with none it is dropped.
 */
internal class SecurityCenterViewModel(auth: FronteggAuth) : ViewModel() {

    val securityCenterState = SecurityCenterState(auth, viewModelScope)

    private val signOutListeners = mutableListOf<() -> Unit>()

    init {
        viewModelScope.launch {
            auth.user.stateFlow.map { user -> user?.idOrNull() }.distinctUntilChanged().collect { userId ->
                securityCenterState.reset()
                if (userId != null) securityCenterState.loadSessions()
            }
        }
    }

    fun addSignOutListener(listener: () -> Unit) {
        signOutListeners += listener
    }

    fun removeSignOutListener(listener: () -> Unit) {
        signOutListeners -= listener
    }

    fun signOut() {
        securityCenterState.signOut { signOutListeners.toList().forEach { listener -> listener() } }
    }

    companion object {
        fun key(auth: FronteggAuth): String = "SecurityCenterViewModel:${System.identityHashCode(auth)}"

        fun factory(auth: FronteggAuth): ViewModelProvider.Factory = viewModelFactory {
            initializer { SecurityCenterViewModel(auth) }
        }
    }
}
