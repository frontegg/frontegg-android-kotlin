package com.frontegg.android.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionsTest {

    @Test
    fun `calculateTimerOffset returns positive value when expiration is far in future`() {
        val expirationTimeSeconds = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val offset = expirationTimeSeconds.toLong().calculateTimerOffset()
        assertTrue(offset > 0)
    }

    @Test
    fun `calculateTimerOffset returns value less than remaining time when far in future`() {
        val nowSeconds = System.currentTimeMillis() / 1000
        val expirationTimeSeconds = nowSeconds + 3600 // 1 hour
        val remainingTimeMs = 3600 * 1000L
        val offset = expirationTimeSeconds.toLong().calculateTimerOffset()
        assertTrue(offset <= remainingTimeMs)
    }

    @Test
    fun `calculateTimerOffset returns zero or small value when expiration is in the past`() {
        val expirationTimeSeconds = (System.currentTimeMillis() / 1000) - 100 // 100 seconds ago
        val offset = expirationTimeSeconds.toLong().calculateTimerOffset()
        assertTrue(offset >= 0)
    }

    @Test
    fun `calculateTimerOffset with 20 seconds remaining returns non-negative`() {
        val expirationTimeSeconds = (System.currentTimeMillis() / 1000) + 20
        val offset = expirationTimeSeconds.toLong().calculateTimerOffset()
        assertTrue(offset >= 0)
    }
}
