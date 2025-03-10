package com.frontegg.android.services

import android.app.Activity
import com.frontegg.android.testUtils.BlockCoroutineDispatcher
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.JWT
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.utils.StepUpConstants
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class StepUpAuthenticatorTest {

    private lateinit var api: Api
    private lateinit var credentialManager: CredentialManager
    private lateinit var multiFactorAuthenticator: MultiFactorAuthenticator
    private lateinit var stepUpAuthenticator: StepUpAuthenticator

    @Before
    fun setUp() {
        api = mockk()
        credentialManager = mockk()
        multiFactorAuthenticator = mockk()
        stepUpAuthenticator = StepUpAuthenticator(api, credentialManager, multiFactorAuthenticator)

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope }.returns(CoroutineScope(BlockCoroutineDispatcher()))
    }

    @Test
    fun `isSteppedUp returns false when no access token`() {
        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) } returns null
        assertFalse(stepUpAuthenticator.isSteppedUp())
    }

    @Test
    fun `isSteppedUp returns true for valid authentication`() {
        val mockToken = mockk<JWT>(relaxed = true) {
            every { auth_time } returns (System.currentTimeMillis() / 1000)
            every { acr } returns StepUpConstants.ACR_VALUE
            every { amr } returns listOf("otp", StepUpConstants.AMR_MFA_VALUE)
        }

        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) } returns "mock_token"

        mockkObject(JWTHelper)
        every { JWTHelper.decode("mock_token") } returns mockToken

        assertTrue(stepUpAuthenticator.isSteppedUp())
    }

    @Test
    fun `isSteppedUp returns true for valid auth_time`() {
        val mockToken = mockk<JWT>(relaxed = true) {
            every { auth_time } returns (System.currentTimeMillis() / 1000)
            every { acr } returns StepUpConstants.ACR_VALUE
            every { amr } returns listOf("otp", StepUpConstants.AMR_MFA_VALUE)
        }

        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) } returns "mock_token"

        mockkObject(JWTHelper)
        every { JWTHelper.decode("mock_token") } returns mockToken

        assertTrue(stepUpAuthenticator.isSteppedUp(maxAge = 1.toDuration(DurationUnit.HOURS)))
    }

    @Test
    fun `isSteppedUp returns false for expired auth_time`() {
        val mockToken = mockk<JWT>(relaxed = true) {
            every { auth_time } returns (System.currentTimeMillis() / 1000 - 2.toDuration(
                DurationUnit.HOURS
            ).inWholeSeconds)
            every { acr } returns StepUpConstants.ACR_VALUE
            every { amr } returns listOf("otp", StepUpConstants.AMR_MFA_VALUE)
        }

        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) } returns "mock_token"

        mockkObject(JWTHelper)
        every { JWTHelper.decode("mock_token") } returns mockToken

        assertTrue(!stepUpAuthenticator.isSteppedUp(maxAge = 1.toDuration(DurationUnit.HOURS)))
    }

    @Test
    fun `isSteppedUp returns false if JWT not contains acr`() {
        val mockToken = mockk<JWT>(relaxed = true) {
            every { auth_time } returns (System.currentTimeMillis() / 1000)
            every { acr } returns null
            every { amr } returns listOf("otp", StepUpConstants.AMR_MFA_VALUE)
        }

        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) } returns "mock_token"

        mockkObject(JWTHelper)
        every { JWTHelper.decode("mock_token") } returns mockToken

        assertTrue(!stepUpAuthenticator.isSteppedUp())
    }

    @Test
    fun `isSteppedUp returns false if amr not contains mfa value`() {
        val mockToken = mockk<JWT>(relaxed = true) {
            every { auth_time } returns (System.currentTimeMillis() / 1000)
            every { acr } returns StepUpConstants.ACR_VALUE
            every { amr } returns listOf("otp")
        }

        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) } returns "mock_token"

        mockkObject(JWTHelper)
        every { JWTHelper.decode("mock_token") } returns mockToken

        assertTrue(!stepUpAuthenticator.isSteppedUp())
    }

    @Test
    fun `isSteppedUp returns false if amr not contains one of AMR_ADDITIONAL_VALUE value`() {
        val mockToken = mockk<JWT>(relaxed = true) {
            every { auth_time } returns (System.currentTimeMillis() / 1000)
            every { acr } returns StepUpConstants.ACR_VALUE
            every { amr } returns listOf("method", StepUpConstants.AMR_MFA_VALUE)
        }

        every { credentialManager.get(CredentialKeys.ACCESS_TOKEN) } returns "mock_token"

        mockkObject(JWTHelper)
        every { JWTHelper.decode("mock_token") } returns mockToken

        assertTrue(!stepUpAuthenticator.isSteppedUp())
    }

    @Test
    fun `stepUp calls MultiFactorAuthenticator`() = runBlocking {
        val activity = mockk<Activity>(relaxed = true)
        val callback: (Exception?) -> Unit = mockk(relaxed = true)

        coEvery { api.generateStepUp(any()) } returns ""
        coEvery { multiFactorAuthenticator.start(activity, callback, "") } just Runs

        stepUpAuthenticator.stepUp(activity, callback = callback)

        coVerify { multiFactorAuthenticator.start(activity, callback, "") }
    }
}