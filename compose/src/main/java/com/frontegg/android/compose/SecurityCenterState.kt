package com.frontegg.android.compose

import android.app.Activity
import androidx.credentials.exceptions.CreateCredentialCancellationException
import com.frontegg.android.FronteggAuth
import com.frontegg.android.exceptions.WebAuthnAlreadyRegisteredInLocalDeviceException
import com.frontegg.android.models.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 * @property sessionActionFailed true when the last sign-out of a session failed.
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
    val hasOtherSessions: Boolean
        get() = sessions.any { !it.current }
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

    fun loadSessions() {
        if (mutableUiState.value.isLoadingSessions) return
        mutableUiState.update { it.copy(isLoadingSessions = true, sessionsLoadFailed = false) }

        scope.launch {
            val sessions = runSessionCall { auth.sessions.getActiveSessions() }
            mutableUiState.update { current ->
                if (sessions == null) {
                    current.copy(isLoadingSessions = false, sessionsLoadFailed = true)
                } else {
                    current.copy(isLoadingSessions = false, sessions = sessions.sortedByDescending { it.current })
                }
            }
        }
    }

    fun revokeSession(session: UserSession) {
        if (session.id in mutableUiState.value.revokingSessionIds) return
        mutableUiState.update {
            it.copy(revokingSessionIds = it.revokingSessionIds + session.id, sessionActionFailed = false)
        }

        scope.launch {
            val revoked = runSessionCall { auth.sessions.revokeSession(session.id) } != null
            mutableUiState.update { current ->
                current.copy(
                    revokingSessionIds = current.revokingSessionIds - session.id,
                    sessions = if (revoked) current.sessions.filterNot { it.id == session.id } else current.sessions,
                    sessionActionFailed = !revoked,
                )
            }
        }
    }

    fun revokeOtherSessions() {
        if (mutableUiState.value.isRevokingOtherSessions) return
        mutableUiState.update { it.copy(isRevokingOtherSessions = true, sessionActionFailed = false) }

        scope.launch {
            val revoked = runSessionCall { auth.sessions.revokeOtherSessions() } != null
            mutableUiState.update { current ->
                current.copy(
                    isRevokingOtherSessions = false,
                    sessions = if (revoked) current.sessions.filter { it.current } else current.sessions,
                    sessionActionFailed = !revoked,
                )
            }
        }
    }

    fun registerPasskey(activity: Activity) {
        if (mutableUiState.value.passkeyRegistration == PasskeyRegistrationStatus.IN_PROGRESS) return
        mutableUiState.update { it.copy(passkeyRegistration = PasskeyRegistrationStatus.IN_PROGRESS) }

        auth.registerPasskeys(activity) { error ->
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
            mutableUiState.value = SecurityCenterUiState()
            onSignedOut()
        }
    }

    private suspend fun <T> runSessionCall(call: suspend () -> T): T? =
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
}
