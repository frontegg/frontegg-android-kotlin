package com.frontegg.android.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.User
import com.frontegg.android.models.UserSession

/**
 * A scrollable account security screen: account details, multi-factor status, passkeys,
 * active sessions and sign out.
 * Calls in progress survive configuration changes; it needs a [androidx.lifecycle.ViewModelStoreOwner].
 *
 * @param onSignedOut called on the main thread after the user signs out, if this component is still shown then.
 */
@Composable
fun FronteggSecurityCenter(
    modifier: Modifier = Modifier,
    auth: FronteggAuth = rememberFronteggAuth(),
    onSignedOut: () -> Unit = {},
) {
    val user by auth.user.collectAsState()
    val securityCenterViewModel: SecurityCenterViewModel = viewModel(
        key = SecurityCenterViewModel.key(auth),
        factory = SecurityCenterViewModel.factory(auth),
    )
    val securityCenterState = securityCenterViewModel.securityCenterState
    val uiState by securityCenterState.uiState.collectAsState()
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    val activity = LocalContext.current.findActivity()
    // Saved across configuration changes, so only a first show clears the last result messages.
    var alreadyShown by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(securityCenterState) {
        securityCenterState.onShown(isFirstShow = !alreadyShown)
        alreadyShown = true
    }
    DisposableEffect(securityCenterViewModel) {
        val listener: () -> Unit = { currentOnSignedOut() }
        securityCenterViewModel.addSignOutListener(listener)
        onDispose { securityCenterViewModel.removeSignOutListener(listener) }
    }

    FronteggSecurityCenter(
        user = user,
        uiState = uiState,
        onRegisterPasskey = activity?.let { { securityCenterState.registerPasskey(it) } },
        onRevokeSession = securityCenterState::revokeSession,
        onRevokeOtherSessions = securityCenterState::revokeOtherSessions,
        onRetrySessions = securityCenterState::loadSessions,
        onSignOut = securityCenterViewModel::signOut,
        modifier = modifier,
    )
}

/**
 * Stateless security center. Pass `null` for [onRegisterPasskey] to disable passkey registration.
 */
@Composable
fun FronteggSecurityCenter(
    user: User?,
    uiState: SecurityCenterUiState,
    onRegisterPasskey: (() -> Unit)?,
    onRevokeSession: (UserSession) -> Unit,
    onRevokeOtherSessions: () -> Unit,
    onRetrySessions: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        AccountSection(user)
        HorizontalDivider()
        MultiFactorSection(mfaEnrolled = user?.mfaEnrolled == true)
        HorizontalDivider()
        PasskeysSection(status = uiState.passkeyRegistration, onRegisterPasskey = onRegisterPasskey)
        HorizontalDivider()
        SessionsSection(
            uiState = uiState,
            onRevokeSession = onRevokeSession,
            onRevokeOtherSessions = onRevokeOtherSessions,
            onRetrySessions = onRetrySessions,
        )
        HorizontalDivider()
        Button(
            onClick = onSignOut,
            enabled = !uiState.isSigningOut,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.frontegg_security_center_sign_out))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionMessage(text: String, isError: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun AccountSection(user: User?) {
    SectionTitle(stringResource(R.string.frontegg_security_center_account_title))
    if (user == null) return

    val verification = if (user.verified) {
        stringResource(R.string.frontegg_security_center_email_verified)
    } else {
        stringResource(R.string.frontegg_security_center_email_not_verified)
    }
    ListItem(
        headlineContent = { Text(user.nameOrNull() ?: user.emailOrNull().orEmpty()) },
        supportingContent = { Text(listOfNotNull(user.emailOrNull(), verification).joinToString(" · ")) },
    )
}

@Composable
private fun MultiFactorSection(mfaEnrolled: Boolean) {
    SectionTitle(stringResource(R.string.frontegg_security_center_mfa_title))
    SectionMessage(
        if (mfaEnrolled) {
            stringResource(R.string.frontegg_security_center_mfa_enabled)
        } else {
            stringResource(R.string.frontegg_security_center_mfa_disabled)
        }
    )
}

@Composable
private fun PasskeysSection(status: PasskeyRegistrationStatus, onRegisterPasskey: (() -> Unit)?) {
    SectionTitle(stringResource(R.string.frontegg_security_center_passkeys_title))
    SectionMessage(stringResource(R.string.frontegg_security_center_passkeys_description))

    when (status) {
        PasskeyRegistrationStatus.REGISTERED ->
            SectionMessage(stringResource(R.string.frontegg_security_center_passkeys_registered))
        PasskeyRegistrationStatus.ALREADY_REGISTERED ->
            SectionMessage(stringResource(R.string.frontegg_security_center_passkeys_already_registered))
        PasskeyRegistrationStatus.FAILED ->
            SectionMessage(stringResource(R.string.frontegg_security_center_passkeys_failed), isError = true)
        PasskeyRegistrationStatus.IDLE, PasskeyRegistrationStatus.IN_PROGRESS -> Unit
    }

    OutlinedButton(
        onClick = { onRegisterPasskey?.invoke() },
        enabled = onRegisterPasskey != null && status != PasskeyRegistrationStatus.IN_PROGRESS,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (status == PasskeyRegistrationStatus.IN_PROGRESS) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.frontegg_security_center_passkeys_add))
        }
    }
}

@Composable
private fun SessionsSection(
    uiState: SecurityCenterUiState,
    onRevokeSession: (UserSession) -> Unit,
    onRevokeOtherSessions: () -> Unit,
    onRetrySessions: () -> Unit,
) {
    SectionTitle(stringResource(R.string.frontegg_security_center_sessions_title))

    when {
        uiState.isLoadingSessions && uiState.sessions.isEmpty() ->
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        uiState.sessionsLoadFailed -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionMessage(
                text = stringResource(R.string.frontegg_security_center_sessions_load_failed),
                isError = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetrySessions, modifier = Modifier.padding(end = 8.dp)) {
                Text(stringResource(R.string.frontegg_security_center_sessions_retry))
            }
        }
        else -> {
            uiState.sessions.forEach { session ->
                SessionRow(
                    session = session,
                    isRevoking = session.id in uiState.revokingSessionIds,
                    onRevoke = { onRevokeSession(session) }
                        .takeIf { uiState.canRevokeSessions && !uiState.isRevokingOtherSessions },
                )
            }
            if (uiState.sessions.none { !it.current }) {
                SectionMessage(stringResource(R.string.frontegg_security_center_sessions_empty))
            }
            if (uiState.sessions.isNotEmpty() && !uiState.canRevokeSessions) {
                SectionMessage(stringResource(R.string.frontegg_security_center_sessions_current_unknown))
            }
        }
    }

    if (uiState.sessionActionFailed) {
        SectionMessage(stringResource(R.string.frontegg_security_center_sessions_action_failed), isError = true)
    }

    if (uiState.hasOtherSessions) {
        OutlinedButton(
            onClick = onRevokeOtherSessions,
            enabled = !uiState.isRevokingOtherSessions && uiState.revokingSessionIds.isEmpty(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.frontegg_security_center_sessions_revoke_others))
        }
    }
}

@Composable
private fun SessionRow(session: UserSession, isRevoking: Boolean, onRevoke: (() -> Unit)?) {
    val details = listOfNotNull(session.ipAddress, session.createdAt).joinToString(" · ")
    ListItem(
        headlineContent = {
            Text(
                session.userAgent?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.frontegg_security_center_sessions_unknown_device)
            )
        },
        supportingContent = if (session.current || details.isNotEmpty()) {
            {
                val currentLabel = stringResource(R.string.frontegg_security_center_sessions_current)
                val labels = listOfNotNull(currentLabel.takeIf { session.current }, details.ifEmpty { null })
                Text(labels.joinToString(" · "))
            }
        } else {
            null
        },
        trailingContent = {
            when {
                session.current || onRevoke == null -> Unit
                isRevoking -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else -> TextButton(onClick = onRevoke) {
                    Text(stringResource(R.string.frontegg_security_center_sessions_revoke))
                }
            }
        },
    )
}

private fun Context.findActivity(): Activity? {
    var currentContext: Context? = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
