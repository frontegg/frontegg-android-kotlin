package com.frontegg.android.services

import android.content.Context
import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.frontegg.android.FronteggApp
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.utils.ObservableValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RefreshTokenAlarmReceiverTest {
    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var receiver: RefreshTokenAlarmReceiver
    private lateinit var mockContext: Context
    private lateinit var mockParamsIntent: Intent

    private lateinit var mockkFronteggAppService: FronteggAppService
    private lateinit var mockAuthService: FronteggAuthService
    private lateinit var refreshTokenObservable: ObservableValue<String?>

    @Before
    fun setUp() {
        receiver = RefreshTokenAlarmReceiver()

        mockContext = mockk(relaxed = true)
        mockParamsIntent = mockk(relaxed = true)

        mockkFronteggAppService = mockk(relaxed = true)
        mockAuthService = mockk(relaxed = true)
        refreshTokenObservable = ObservableValue("token-before")

        mockkObject(FronteggApp)
        every { FronteggApp.instance } returns mockkFronteggAppService
        every { mockkFronteggAppService.auth } returns mockAuthService

        every { mockAuthService.refreshToken } returns refreshTokenObservable
        every { mockAuthService.clearCredentials() } returns Unit
    }

    @Test
    fun `onReceive should clearCredentials when refresh token unchanged`() {
        // Arrange
        every {
            mockAuthService.sendRefreshToken(isManualCall = true)
        } answers {
            refreshTokenObservable.value = "token-before"
            throw FailedToAuthenticateException(error = "Refresh token is not valid")
        }

        // Act
        receiver.onReceive(mockContext, mockParamsIntent)
        Thread.sleep(200)

        // Assert
        verify { mockAuthService.clearCredentials() }
    }

    @Test
    fun `onReceive should not clearCredentials when refresh token rotated`() {
        // Arrange
        every {
            mockAuthService.sendRefreshToken(isManualCall = true)
        } answers {
            refreshTokenObservable.value = "token-after"
            throw FailedToAuthenticateException(error = "Refresh token is not valid")
        }

        // Act
        receiver.onReceive(mockContext, mockParamsIntent)
        Thread.sleep(200)

        // Assert
        verify(exactly = 0) { mockAuthService.clearCredentials() }
    }
}

