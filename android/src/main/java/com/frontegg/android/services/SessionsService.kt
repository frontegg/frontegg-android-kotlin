package com.frontegg.android.services

import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.models.UserSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Lists and revokes the signed-in user's active sessions.
 * Every call runs its network request on [ioDispatcher].
 */
open class SessionsService(
    private val api: Api,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * @return the user's active sessions, with the one this device uses marked [UserSession.current].
     */
    @Throws(IOException::class, FailedToAuthenticateException::class)
    open suspend fun getActiveSessions(): List<UserSession> =
        withContext(ioDispatcher) { api.getUserSessions() }

    /**
     * Signs out the session with [sessionId]. A session that is already gone counts as signed out.
     */
    @Throws(IOException::class, FailedToAuthenticateException::class)
    open suspend fun revokeSession(sessionId: String) {
        withContext(ioDispatcher) { api.revokeUserSession(sessionId) }
    }

    /**
     * Signs out, one by one, every session of the user except the one this device uses,
     * going on past failures; a session that is already gone counts as signed out.
     * Throws [IllegalStateException], signing out nothing, when no session is marked as this device's.
     * Once every session was tried, throws the first failure with the others suppressed.
     * With [accessToken], every request uses it instead of the stored one, so the call stays bound to that user.
     *
     * @return the ids of the sessions it signed out, so callers can update what they display.
     */
    @Throws(IOException::class, FailedToAuthenticateException::class, IllegalStateException::class)
    open suspend fun revokeOtherSessions(accessToken: String? = null): List<String> =
        withContext(ioDispatcher) {
            val sessions = api.getUserSessions(accessToken)
            check(sessions.any { it.current }) { "No session is marked as this device's" }
            val otherSessionIds = sessions.filterNot { it.current }.map { it.id }
            val failures = otherSessionIds.mapNotNull { revokeOrGetFailure(it, accessToken) }
            val firstFailure = failures.firstOrNull() ?: return@withContext otherSessionIds
            failures.drop(1).forEach(firstFailure::addSuppressed)
            throw firstFailure
        }

    private fun revokeOrGetFailure(sessionId: String, accessToken: String?): Exception? =
        try {
            api.revokeUserSession(sessionId, accessToken)
            null
        } catch (e: IOException) {
            e
        } catch (e: FailedToAuthenticateException) {
            e
        }
}
