package com.frontegg.android.services

import android.app.Activity
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.exceptions.MfaRequiredException
import com.frontegg.android.utils.AuthorizeUrlGenerator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultiFactorAuthenticatorTest {
    private val storage = mockk<FronteggInnerStorage>(relaxed = true)
    private val mfaAuthorizeUrl = "https://base.url.com/oauth/authorize?mfa"

    @Before
    fun setUp() {
        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() } returns storage
        every { storage.baseUrl } returns "https://base.url.com"
        every { storage.isEmbeddedMode } returns true

        mockkConstructor(AuthorizeUrlGenerator::class)
        every { anyConstructed<AuthorizeUrlGenerator>().generate(loginAction = any()) } returns
            (mfaAuthorizeUrl to "code-verifier")

        mockkObject(EmbeddedAuthActivity.Companion)
        every { EmbeddedAuthActivity.authenticateWithMultiFactor(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        FronteggState.isStepUpAuthorization.value = false
        unmockkAll()
    }

    @Test
    fun `start continues MFA in the login box that is already open`() {
        val loginBox = mockk<EmbeddedAuthActivity>(relaxed = true)

        var reported: Exception? = null

        MultiFactorAuthenticator().start(loginBox, { reported = it }, """{"mfaRequired":true}""", continueInLoginBox = true)

        verify(exactly = 1) { loginBox.loadAuthorizeUrl(mfaAuthorizeUrl) }
        verify(exactly = 0) { EmbeddedAuthActivity.authenticateWithMultiFactor(any(), any(), any()) }
        assertTrue(reported is MfaRequiredException)
    }

    @Test
    fun `start ends the sign-in when the login box is finishing`() {
        val closingLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        every { closingLoginBox.isFinishing } returns true
        var reported: Exception? = null

        MultiFactorAuthenticator().start(closingLoginBox, { reported = it }, """{"mfaRequired":true}""", continueInLoginBox = true)

        verify(exactly = 0) { closingLoginBox.loadAuthorizeUrl(any()) }
        verify(exactly = 0) { EmbeddedAuthActivity.authenticateWithMultiFactor(any(), any(), any()) }
        assertTrue(reported is CanceledByUserException)
    }

    @Test
    fun `start ends the sign-in when the login box is destroyed`() {
        val destroyedLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        every { destroyedLoginBox.isDestroyed } returns true
        var reported: Exception? = null

        MultiFactorAuthenticator().start(destroyedLoginBox, { reported = it }, """{"mfaRequired":true}""", continueInLoginBox = true)

        verify(exactly = 0) { destroyedLoginBox.loadAuthorizeUrl(any()) }
        verify(exactly = 0) { EmbeddedAuthActivity.authenticateWithMultiFactor(any(), any(), any()) }
        assertTrue(reported is CanceledByUserException)
    }

    @Test
    fun `start continues MFA in the login box recreated during sign-in`() {
        val recreatedLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        val startingLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        every { startingLoginBox.isDestroyed } returns true
        every { EmbeddedAuthActivity.liveLoginBox(startingLoginBox, any()) } returns recreatedLoginBox
        var reported: Exception? = null

        MultiFactorAuthenticator().start(startingLoginBox, { reported = it }, """{"mfaRequired":true}""", continueInLoginBox = true)

        verify(exactly = 1) { recreatedLoginBox.loadAuthorizeUrl(mfaAuthorizeUrl) }
        verify(exactly = 0) { startingLoginBox.loadAuthorizeUrl(any()) }
        assertTrue(reported is MfaRequiredException)
    }

    @Test
    fun `start opens the login box for MFA from another activity`() {
        val appActivity = mockk<Activity>(relaxed = true)

        MultiFactorAuthenticator().start(appActivity, null, """{"mfaRequired":true}""", continueInLoginBox = true)

        verify(exactly = 1) { EmbeddedAuthActivity.authenticateWithMultiFactor(appActivity, any(), any()) }
    }

    @Test
    fun `start opens a new login box for MFA when the caller does not continue in the open one`() {
        val loginBox = mockk<EmbeddedAuthActivity>(relaxed = true)

        MultiFactorAuthenticator().start(loginBox, null, """{"mfaRequired":true}""")

        verify(exactly = 0) { loginBox.loadAuthorizeUrl(any()) }
        verify(exactly = 1) { EmbeddedAuthActivity.authenticateWithMultiFactor(loginBox, any(), any()) }
    }

    @Test
    fun `start opens a new login box for MFA during a step-up`() {
        val stepUpLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        FronteggState.isStepUpAuthorization.value = true

        MultiFactorAuthenticator().start(stepUpLoginBox, null, """{"mfaRequired":true}""", continueInLoginBox = true)

        verify(exactly = 0) { stepUpLoginBox.loadAuthorizeUrl(any()) }
        verify(exactly = 1) { EmbeddedAuthActivity.authenticateWithMultiFactor(stepUpLoginBox, any(), any()) }
    }

    @Test
    fun `native sign-in continues in the login box that started it`() {
        val startingLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)

        assertEquals(startingLoginBox, EmbeddedAuthActivity.liveLoginBox(startingLoginBox, lastCreated = null))
    }

    @Test
    fun `native sign-in continues in the login box a configuration change recreated`() {
        val startingLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        every { startingLoginBox.isDestroyed } returns true
        every { startingLoginBox.isChangingConfigurations } returns true
        val recreatedLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)

        assertEquals(recreatedLoginBox, EmbeddedAuthActivity.liveLoginBox(startingLoginBox, recreatedLoginBox))
    }

    @Test
    fun `native sign-in does not continue in a new login box opened after a recreated one was closed`() {
        val startingLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        every { startingLoginBox.isDestroyed } returns true
        every { startingLoginBox.isChangingConfigurations } returns true
        every { startingLoginBox.loginBoxId } returns "first-login-box"
        val laterLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        every { laterLoginBox.loginBoxId } returns "second-login-box"

        assertNull(EmbeddedAuthActivity.liveLoginBox(startingLoginBox, laterLoginBox))
    }

    @Test
    fun `native sign-in does not continue in a login box opened after the user closed the one that started it`() {
        val closedLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)
        every { closedLoginBox.isFinishing } returns true
        val laterLoginBox = mockk<EmbeddedAuthActivity>(relaxed = true)

        assertNull(EmbeddedAuthActivity.liveLoginBox(closedLoginBox, laterLoginBox))
    }
}
