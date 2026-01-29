package com.frontegg.android.services

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeatureFlagsTest {

    private lateinit var featureFlags: FeatureFlags

    @Before
    fun setUp() {
        featureFlags = FeatureFlags.getInstance()
    }

    @Test
    fun `isOnSync returns false for unknown flag`() {
        assertFalse(featureFlags.isOnSync("unknown_flag"))
    }

    @Test
    fun `isOn returns false for unknown flag`() = runBlocking {
        assertFalse(featureFlags.isOn("unknown_flag"))
    }

    @Test
    fun `getAllFlags returns empty map when no flags loaded`() {
        val flags = featureFlags.getAllFlags()
        assertTrue(flags.isEmpty())
    }

    @Test
    fun `isOnSync returns false for empty string`() {
        assertFalse(featureFlags.isOnSync(""))
    }
}
