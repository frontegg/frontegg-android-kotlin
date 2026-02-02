package com.frontegg.android.utils

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class SessionTrackerTest {

    private val mockContext = mockk<Context>()
    private val mockSharedPreferences = mockk<SharedPreferences>()
    private val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    
    private val storedValues = mutableMapOf<String, Long>()
    
    private lateinit var sessionTracker: SessionTracker

    @Before
    fun setUp() {
        storedValues.clear()
        
        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        
        // Capture put operations
        val keySlot = slot<String>()
        val valueSlot = slot<Long>()
        every { mockEditor.putLong(capture(keySlot), capture(valueSlot)) } answers {
            storedValues[keySlot.captured] = valueSlot.captured
            mockEditor
        }
        every { mockEditor.apply() } returns Unit
        
        // Return stored values on get
        every { mockSharedPreferences.getLong(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Long>()
            storedValues[key] ?: default
        }
        
        // Capture remove operations
        val removeKeySlot = slot<String>()
        every { mockEditor.remove(capture(removeKeySlot)) } answers {
            storedValues.remove(removeKeySlot.captured)
            mockEditor
        }
        
        sessionTracker = SessionTracker(mockContext)
    }

    @Test
    fun `trackSessionStart stores current time`() {
        sessionTracker.trackSessionStart()
        
        verify { mockEditor.putLong("session_start_time", any()) }
        verify { mockEditor.putLong("last_refresh_time", any()) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `trackSessionStart with tenant ID uses tenant-scoped key`() {
        sessionTracker.setEnableSessionPerTenant(true)
        sessionTracker.trackSessionStart("tenant-123")
        
        verify { mockEditor.putLong("session_start_time_tenant_tenant-123", any()) }
        verify { mockEditor.putLong("last_refresh_time_tenant_tenant-123", any()) }
    }

    @Test
    fun `trackTokenRefresh updates last refresh time`() {
        sessionTracker.trackTokenRefresh()
        
        verify { mockEditor.putLong("last_refresh_time", any()) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `trackTokenRefresh with tenant ID uses tenant-scoped key`() {
        sessionTracker.setEnableSessionPerTenant(true)
        sessionTracker.trackTokenRefresh("tenant-456")
        
        verify { mockEditor.putLong("last_refresh_time_tenant_tenant-456", any()) }
    }

    @Test
    fun `hasSessionData returns false when no session`() {
        every { mockSharedPreferences.getLong("session_start_time", 0L) } returns 0L
        
        val result = sessionTracker.hasSessionData()
        
        assert(!result)
    }

    @Test
    fun `hasSessionData returns true when session exists`() {
        storedValues["session_start_time"] = System.currentTimeMillis()
        
        val result = sessionTracker.hasSessionData()
        
        assert(result)
    }

    @Test
    fun `getSessionStartTime returns stored time`() {
        val expectedTime = 1234567890L
        storedValues["session_start_time"] = expectedTime
        
        val result = sessionTracker.getSessionStartTime()
        
        assert(result == expectedTime)
    }

    @Test
    fun `getSessionStartTime returns 0 when no session`() {
        val result = sessionTracker.getSessionStartTime()
        
        assert(result == 0L)
    }

    @Test
    fun `getLastRefreshTime returns stored time`() {
        val expectedTime = 9876543210L
        storedValues["last_refresh_time"] = expectedTime
        
        val result = sessionTracker.getLastRefreshTime()
        
        assert(result == expectedTime)
    }

    @Test
    fun `clearSessionData removes session data`() {
        storedValues["session_start_time"] = 123L
        storedValues["last_refresh_time"] = 456L
        
        sessionTracker.clearSessionData()
        
        verify { mockEditor.remove("session_start_time") }
        verify { mockEditor.remove("last_refresh_time") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `clearSessionData with tenant ID removes tenant-scoped data`() {
        sessionTracker.setEnableSessionPerTenant(true)
        
        sessionTracker.clearSessionData("tenant-789")
        
        verify { mockEditor.remove("session_start_time_tenant_tenant-789") }
        verify { mockEditor.remove("last_refresh_time_tenant_tenant-789") }
    }

    @Test
    fun `getSessionDuration returns 0 when no session`() {
        val result = sessionTracker.getSessionDuration()
        
        assert(result == 0L)
    }

    @Test
    fun `getSessionDuration returns correct duration`() {
        val startTime = System.currentTimeMillis() - 5000L // 5 seconds ago
        storedValues["session_start_time"] = startTime
        
        val result = sessionTracker.getSessionDuration()
        
        // Should be approximately 5000ms (allow some margin for test execution)
        assert(result >= 4900L && result <= 6000L)
    }

    @Test
    fun `hasMinimumSessionDuration returns false when session too short`() {
        val startTime = System.currentTimeMillis() - 1000L // 1 second ago
        storedValues["session_start_time"] = startTime
        
        val result = sessionTracker.hasMinimumSessionDuration(5000L) // 5 seconds minimum
        
        assert(!result)
    }

    @Test
    fun `hasMinimumSessionDuration returns true when session long enough`() {
        val startTime = System.currentTimeMillis() - 10000L // 10 seconds ago
        storedValues["session_start_time"] = startTime
        
        val result = sessionTracker.hasMinimumSessionDuration(5000L) // 5 seconds minimum
        
        assert(result)
    }

    @Test
    fun `setEnableSessionPerTenant affects key generation`() {
        // Without tenant per session
        sessionTracker.setEnableSessionPerTenant(false)
        sessionTracker.trackSessionStart("tenant-test")
        
        verify { mockEditor.putLong("session_start_time", any()) }
        
        // With tenant per session
        sessionTracker.setEnableSessionPerTenant(true)
        sessionTracker.trackSessionStart("tenant-test")
        
        verify { mockEditor.putLong("session_start_time_tenant_tenant-test", any()) }
    }

    @Test
    fun `null tenant ID uses non-scoped keys even when enabled`() {
        sessionTracker.setEnableSessionPerTenant(true)
        sessionTracker.trackSessionStart(null)
        
        verify { mockEditor.putLong("session_start_time", any()) }
    }
}
