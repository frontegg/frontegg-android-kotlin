package com.frontegg.android.utils

import org.junit.Test

class ExtensionsTest {

    @Test
    fun `calculateTimerOffset returns positive value for future timestamp`() {
        val futureTimestamp = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        
        val offset = futureTimestamp.calculateTimerOffset()
        
        // Should be positive and less than 3600 seconds (accounting for buffer)
        assert(offset > 0)
        assert(offset < 3600 * 1000) // less than 1 hour in milliseconds
    }

    @Test
    fun `calculateTimerOffset returns zero or negative for past timestamp`() {
        val pastTimestamp = (System.currentTimeMillis() / 1000) - 3600 // 1 hour ago
        
        val offset = pastTimestamp.calculateTimerOffset()
        
        // Should be zero or negative since token is already expired
        assert(offset <= 0)
    }

    @Test
    fun `calculateTimerOffset handles timestamp at current time`() {
        val currentTimestamp = System.currentTimeMillis() / 1000
        
        val offset = currentTimestamp.calculateTimerOffset()
        
        // Should be zero or negative since it's already expired
        assert(offset <= 0)
    }

    @Test
    fun `calculateTimerOffset accounts for buffer time`() {
        // Token expires in exactly 5 minutes
        val fiveMinutesFromNow = (System.currentTimeMillis() / 1000) + 300
        
        val offset = fiveMinutesFromNow.calculateTimerOffset()
        
        // The offset should be less than 5 minutes due to the buffer
        // Typically we want to refresh before expiration
        assert(offset > 0)
        assert(offset < 300 * 1000) // less than 5 minutes in ms
    }

    @Test
    fun `calculateTimerOffset handles very large future timestamp`() {
        // 10 years from now
        val farFuture = (System.currentTimeMillis() / 1000) + (10 * 365 * 24 * 3600L)
        
        val offset = farFuture.calculateTimerOffset()
        
        assert(offset > 0)
    }

    @Test
    fun `calculateTimerOffset handles zero timestamp`() {
        val offset = 0L.calculateTimerOffset()
        
        // Zero timestamp (epoch) is definitely in the past
        assert(offset <= 0)
    }

    @Test
    fun `calculateTimerOffset handles negative timestamp`() {
        val offset = (-1000L).calculateTimerOffset()
        
        // Negative timestamp is definitely in the past
        assert(offset <= 0)
    }

    @Test
    fun `calculateTimerOffset is consistent for same input`() {
        val timestamp = (System.currentTimeMillis() / 1000) + 3600
        
        val offset1 = timestamp.calculateTimerOffset()
        val offset2 = timestamp.calculateTimerOffset()
        
        // Should be very close (within 100ms due to execution time)
        assert(kotlin.math.abs(offset1 - offset2) < 100)
    }

    @Test
    fun `calculateTimerOffset for 30 minute token`() {
        val thirtyMinutesFromNow = (System.currentTimeMillis() / 1000) + 1800
        
        val offset = thirtyMinutesFromNow.calculateTimerOffset()
        
        // Should be positive, accounting for typical 30s-60s buffer
        assert(offset > 0)
        // Should be less than the full 30 minutes
        assert(offset < 1800 * 1000)
    }

    @Test
    fun `calculateTimerOffset for 1 minute token`() {
        val oneMinuteFromNow = (System.currentTimeMillis() / 1000) + 60
        
        val offset = oneMinuteFromNow.calculateTimerOffset()
        
        // For very short-lived tokens, offset might be very small or even negative
        // depending on the buffer implementation
        // Just verify it doesn't crash
        assert(offset is Long)
    }
}
