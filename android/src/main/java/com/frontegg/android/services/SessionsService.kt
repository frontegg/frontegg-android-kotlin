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
     * Signs out the session with [sessionId].
     */
    @Throws(IOException::class, FailedToAuthenticateException::class)
    open suspend fun revokeSession(sessionId: String) {
        withContext(ioDispatcher) { api.revokeUserSession(sessionId) }
    }

    /**
     * Signs out every session of the user except the one this device uses.
     */
    @Throws(IOException::class, FailedToAuthenticateException::class)
    open suspend fun revokeOtherSessions() {
        withContext(ioDispatcher) { api.revokeOtherUserSessions() }
    }
}
