package com.frontegg.android.embedded

import android.app.Activity
import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import com.frontegg.android.BlockCoroutineDispatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test

class CredentialManagerHandlerTest {
    private lateinit var mockActivity: Activity
    private lateinit var credentialManagerHandler: CredentialManagerHandler
    private lateinit var credentialManager: CredentialManager
    private val createPublicKeyCredentialResponse = CreatePublicKeyCredentialResponse(
        registrationResponseJson = "{}"
    )
    private val getCredentialResponse = GetCredentialResponse(
        PublicKeyCredential(
            authenticationResponseJson = "{}",
        )
    )

    @Before
    fun setUp() {
        mockActivity = mockkClass(Activity::class)
        every { mockActivity.applicationContext }.returns(mockkClass(Context::class))

        credentialManager = mockkClass(CredentialManager::class)

        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(any()) }.returns(credentialManager)

        credentialManagerHandler = CredentialManagerHandler(mockActivity)
    }

    @Test
    fun `createPasskey should CredentialManager_clearCredentialState`() {
        coEvery { credentialManager.clearCredentialState(any()) }.returns(Unit)
        coEvery { credentialManager.createCredential(any(), any()) }.returns(
            createPublicKeyCredentialResponse
        )

        CoroutineScope(BlockCoroutineDispatcher()).launch {
            credentialManagerHandler.createPasskey("{\"authenticatorSelection\": {}, \"user\":{\"name\":\"Alex\"}}")
        }

        coVerify { credentialManager.clearCredentialState(any()) }
    }

    @Test
    fun `createPasskey should CredentialManager_createCredential`() {
        coEvery { credentialManager.clearCredentialState(any()) }.returns(Unit)
        coEvery { credentialManager.createCredential(any(), any()) }.returns(
            createPublicKeyCredentialResponse
        )

        CoroutineScope(BlockCoroutineDispatcher()).launch {
            credentialManagerHandler.createPasskey("{\"authenticatorSelection\": {}, \"user\":{\"name\":\"Alex\"}}")
        }

        coVerify { credentialManager.createCredential(any(), any()) }
    }

    @Test
    fun `createPasskey should return response`() {
        coEvery { credentialManager.clearCredentialState(any()) }.returns(Unit)
        coEvery { credentialManager.createCredential(any(), any()) }.returns(
            createPublicKeyCredentialResponse
        )

        var response: CreatePublicKeyCredentialResponse? = null
        CoroutineScope(BlockCoroutineDispatcher()).launch {
            response =
                credentialManagerHandler.createPasskey("{\"authenticatorSelection\": {}, \"user\":{\"name\":\"Alex\"}}")
        }

        assert(response == createPublicKeyCredentialResponse)
    }

    @Test
    fun `getPasskey should CredentialManager_clearCredentialState`() {
        coEvery {
            credentialManager.getCredential(
                any<Context>(),
                any<GetCredentialRequest>()
            )
        }.returns(getCredentialResponse)

        CoroutineScope(BlockCoroutineDispatcher()).launch {
            credentialManagerHandler.getPasskey("{}")
        }

        coVerify { credentialManager.getCredential(any<Context>(), any<GetCredentialRequest>()) }
    }

    @Test
    fun `getPasskey should return response`() {
        coEvery {
            credentialManager.getCredential(
                any<Context>(),
                any<GetCredentialRequest>()
            )
        }.returns(getCredentialResponse)

        var response: GetCredentialResponse? = null
        CoroutineScope(BlockCoroutineDispatcher()).launch {
            response = credentialManagerHandler.getPasskey("{}")
        }

        assert(response == getCredentialResponse)
    }
}