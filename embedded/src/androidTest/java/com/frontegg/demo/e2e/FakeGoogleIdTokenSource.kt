package com.frontegg.demo.e2e

import com.frontegg.android.services.GoogleIdTokenSource
import java.util.concurrent.CopyOnWriteArrayList

/** Stands in for Credential Manager, which has no Google account to offer on the CI emulator. */
class FakeGoogleIdTokenSource(private val issueIdToken: () -> String) : GoogleIdTokenSource {

    val requestedServerClientIds = CopyOnWriteArrayList<String>()

    override suspend fun getIdToken(serverClientId: String): String {
        requestedServerClientIds.add(serverClientId)
        return issueIdToken()
    }
}
