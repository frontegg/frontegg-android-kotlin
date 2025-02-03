package com.frontegg.android.services

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.util.Timer
import java.util.TimerTask

class FronteggRefreshTokenTimerTest {

    private lateinit var context: Context
    private lateinit var appLifecycle: FronteggAppLifecycle
    private lateinit var timer: FronteggRefreshTokenTimer
    private lateinit var jobScheduler: JobScheduler

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        appLifecycle = mockk(relaxed = true)
        jobScheduler = mockk(relaxed = true)

        every { context.getSystemService(Context.JOB_SCHEDULER_SERVICE) } returns jobScheduler

        timer = spyk(FronteggRefreshTokenTimer(context, appLifecycle))

        // Mock FronteggCallback
        mockkObject(timer.refreshTokenIfNeeded)
    }

    @Test
    fun `scheduleTimer should schedule a timer`() {
        every { appLifecycle.appInForeground } returns true

        // Capture the scheduled TimerTask
        val timerSlot = slot<TimerTask>()
        mockkConstructor(Timer::class)

        every { anyConstructed<Timer>().schedule(capture(timerSlot), any<Long>()) } answers {
            timerSlot.captured.run() // Manually trigger the timer task
        }

        timer.scheduleTimer(5000)

        verify {anyConstructed<Timer>().schedule(capture(timerSlot), any<Long>()) } // Ensure callback is triggered
    }

    @Test
    fun `scheduleTimer should schedule a job in the background`() {
        every { appLifecycle.appInForeground } returns false
        every { jobScheduler.schedule(any()) } returns JobScheduler.RESULT_SUCCESS

        val jobInfo = mockk<JobInfo>()
        every { timer.buildJobInfo(5000) }.returns(jobInfo)

        timer.scheduleTimer(5000)

        verify { jobScheduler.schedule(jobInfo) } // Ensure job scheduling
    }

    @Test
    fun `cancelLastTimer should cancel scheduled job`() {
        every { jobScheduler.cancel(FronteggRefreshTokenTimer.JOB_ID) } just Runs

        val jobInfo = mockk<JobInfo>()
        every { timer.buildJobInfo(5000) }.returns(jobInfo)

        timer.scheduleTimer(5000)

        timer.cancelLastTimer()

        verify { jobScheduler.cancel(FronteggRefreshTokenTimer.JOB_ID) }
    }
}
