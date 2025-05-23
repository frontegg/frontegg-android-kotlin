package com.frontegg.android.services

import android.app.job.JobParameters
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.frontegg.android.FronteggApp
import com.frontegg.android.utils.ObservableValue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RefreshTokenJobServiceTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var service: RefreshTokenJobService
    private lateinit var tokenTimer: FronteggRefreshTokenTimer
    private lateinit var mockParams: JobParameters
    private var mockkFronteggAppService = mockk<FronteggAppService>()

    @Before
    fun setUp() {
        service = spyk(RefreshTokenJobService(), recordPrivateCalls = true)
        mockParams = mockk(relaxed = true)

        // Mock dependencies
        mockkObject(FronteggAuthService)
        mockkObject(FronteggRefreshTokenTimer)
        tokenTimer = mockk()

        // Mock FronteggAuthService singleton
        val mockAuthService = mockk<FronteggAuthService>()

        every { mockAuthService.accessToken } returns (ObservableValue(null))
        every { mockAuthService.isLoading } returns (ObservableValue(false))
        every { FronteggAuthService.instance } returns mockAuthService

        // Mock RefreshTokenTimer singleton
        every { tokenTimer.scheduleTimer(any()) } just Runs

        // Mock log statements to avoid actual logging
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(FronteggApp)
        every { FronteggApp.getInstance() } returns mockkFronteggAppService
    }

    @Test
    fun `onStartJob should return true and start background task`() {
        every { service.performBackgroundTask(any()) }.returns(Unit)
        every { mockkFronteggAppService.isAppInForeground() } returns false

        val result = service.onStartJob(mockParams)

        assertTrue(result)
        verify { service.performBackgroundTask(mockParams) }
    }

    @Test
    fun `onStopJob should return false`() {
        val result = service.onStopJob(mockParams)
        assertFalse(result) // Expected to return false
    }

    @Test
    fun `performBackgroundTask should call sendRefreshToken and finish job successfully`() {
        // Arrange
        every { FronteggAuthService.instance.sendRefreshToken() } returns true
        every { service.jobFinished(any(), any()) } just Runs

        // Act
        service.performBackgroundTask(mockParams)

        // Assert
        verify { FronteggAuthService.instance.sendRefreshToken() } // Ensure sendRefreshToken is called
        verify { service.jobFinished(mockParams, false) } // Ensure job finishes without errors
    }
}