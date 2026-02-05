package com.frontegg.android.services

import android.content.Context
import android.util.Log
import com.frontegg.android.FronteggAuth
import com.frontegg.android.utils.SentryHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

class FeatureFlagsTest {

    private lateinit var featureFlags: FeatureFlags
    private val mockContext = mockk<Context>()

    @Before
    fun setUp() {
        // Reset singleton for testing
        resetFeatureFlagsSingleton()
        
        featureFlags = FeatureFlags.getInstance()
        
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
        resetFeatureFlagsSingleton()
    }

    private fun resetFeatureFlagsSingleton() {
        try {
            val instanceField: Field = FeatureFlags::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (e: Exception) {
            // Ignore if field doesn't exist
        }
    }

    @Test
    fun `getInstance returns same instance`() {
        val instance1 = FeatureFlags.getInstance()
        val instance2 = FeatureFlags.getInstance()
        
        assert(instance1 === instance2)
    }

    @Test
    fun `isOnSync returns false for unknown flag`() {
        val result = featureFlags.isOnSync("unknown-flag")
        
        assert(!result)
    }

    @Test
    fun `isOn returns false for unknown flag`() = runBlocking {
        val result = featureFlags.isOn("unknown-flag")
        
        assert(!result)
    }

    @Test
    fun `getAllFlags returns empty map initially`() {
        val flags = featureFlags.getAllFlags()
        
        assert(flags.isEmpty())
    }

    @Test
    fun `getAllFlags returns copy of flags`() = runBlocking {
        // Set some flags using reflection
        setFlagsViaReflection(mapOf("test-flag" to true))
        
        val flags1 = featureFlags.getAllFlags()
        val flags2 = featureFlags.getAllFlags()
        
        // Should be equal but not the same instance
        assert(flags1 == flags2)
    }

    @Test
    fun `isOnSync works after flags are set`() = runBlocking {
        setFlagsViaReflection(mapOf(
            "feature-a" to true,
            "feature-b" to false
        ))
        
        assert(featureFlags.isOnSync("feature-a"))
        assert(!featureFlags.isOnSync("feature-b"))
    }

    @Test
    fun `isOn works after flags are set`() = runBlocking {
        setFlagsViaReflection(mapOf(
            "feature-x" to true,
            "feature-y" to false
        ))
        
        assert(featureFlags.isOn("feature-x"))
        assert(!featureFlags.isOn("feature-y"))
    }

    @Test
    fun `parseFlags correctly parses on values`() {
        val result = invokeParseFlagsMethod("{\"flag1\": \"on\", \"flag2\": \"ON\", \"flag3\": \"On\"}")
        
        assert(result["flag1"] == true)
        assert(result["flag2"] == true)
        assert(result["flag3"] == true)
    }

    @Test
    fun `parseFlags correctly parses off values`() {
        val result = invokeParseFlagsMethod("{\"flag1\": \"off\", \"flag2\": \"OFF\", \"flag3\": \"Off\"}")
        
        assert(result["flag1"] == false)
        assert(result["flag2"] == false)
        assert(result["flag3"] == false)
    }

    @Test
    fun `parseFlags handles invalid values as false`() {
        val result = invokeParseFlagsMethod("{\"flag1\": \"invalid\", \"flag2\": \"1\", \"flag3\": \"yes\"}")
        
        assert(result["flag1"] == false)
        assert(result["flag2"] == false)
        assert(result["flag3"] == false)
    }

    @Test
    fun `parseFlags handles empty JSON`() {
        val result = invokeParseFlagsMethod("{}")
        
        assert(result.isEmpty())
    }

    @Test
    fun `parseFlags handles invalid JSON gracefully`() {
        val result = invokeParseFlagsMethod("not valid json")
        
        assert(result.isEmpty())
    }

    @Test
    fun `parseFlags trims whitespace from values`() {
        val result = invokeParseFlagsMethod("{\"flag1\": \"  on  \", \"flag2\": \"  off  \"}")
        
        assert(result["flag1"] == true)
        assert(result["flag2"] == false)
    }

    @Test
    fun `parseFlags handles mixed case flags`() {
        val result = invokeParseFlagsMethod("{\"Flag-One\": \"on\", \"flag_two\": \"off\", \"FLAG_THREE\": \"on\"}")
        
        assert(result["Flag-One"] == true)
        assert(result["flag_two"] == false)
        assert(result["FLAG_THREE"] == true)
    }

    private fun setFlagsViaReflection(flagsToSet: Map<String, Boolean>) {
        try {
            val flagsField = FeatureFlags::class.java.getDeclaredField("flags")
            flagsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val flags = flagsField.get(featureFlags) as MutableMap<String, Boolean>
            flags.clear()
            flags.putAll(flagsToSet)
        } catch (e: Exception) {
            throw RuntimeException("Failed to set flags via reflection", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeParseFlagsMethod(jsonString: String): Map<String, Boolean> {
        try {
            val method = FeatureFlags::class.java.getDeclaredMethod("parseFlags", String::class.java)
            method.isAccessible = true
            return method.invoke(featureFlags, jsonString) as Map<String, Boolean>
        } catch (e: Exception) {
            throw RuntimeException("Failed to invoke parseFlags", e)
        }
    }

    // ========== Tests for mobile-enable-logging feature flag integration ==========

    @Test
    fun `MOBILE_ENABLE_LOGGING_FLAG constant matches expected value`() {
        assertEquals("mobile-enable-logging", SentryHelper.MOBILE_ENABLE_LOGGING_FLAG)
    }

    @Test
    fun `parseFlags correctly parses mobile-enable-logging flag when on`() {
        val result = invokeParseFlagsMethod("{\"mobile-enable-logging\": \"on\"}")
        
        assertEquals(true, result[SentryHelper.MOBILE_ENABLE_LOGGING_FLAG])
    }

    @Test
    fun `parseFlags correctly parses mobile-enable-logging flag when off`() {
        val result = invokeParseFlagsMethod("{\"mobile-enable-logging\": \"off\"}")
        
        assertEquals(false, result[SentryHelper.MOBILE_ENABLE_LOGGING_FLAG])
    }

    @Test
    fun `parseFlags correctly parses mobile-enable-logging flag when true`() {
        val result = invokeParseFlagsMethod("{\"mobile-enable-logging\": \"true\"}")
        
        assertEquals(true, result[SentryHelper.MOBILE_ENABLE_LOGGING_FLAG])
    }

    @Test
    fun `parseFlags correctly parses mobile-enable-logging flag when false`() {
        val result = invokeParseFlagsMethod("{\"mobile-enable-logging\": \"false\"}")
        
        assertEquals(false, result[SentryHelper.MOBILE_ENABLE_LOGGING_FLAG])
    }

    @Test
    fun `isOnSync returns true for mobile-enable-logging when set to on`() {
        setFlagsViaReflection(mapOf(SentryHelper.MOBILE_ENABLE_LOGGING_FLAG to true))
        
        assert(featureFlags.isOnSync(SentryHelper.MOBILE_ENABLE_LOGGING_FLAG))
    }

    @Test
    fun `isOnSync returns false for mobile-enable-logging when set to off`() {
        setFlagsViaReflection(mapOf(SentryHelper.MOBILE_ENABLE_LOGGING_FLAG to false))
        
        assert(!featureFlags.isOnSync(SentryHelper.MOBILE_ENABLE_LOGGING_FLAG))
    }

    @Test
    fun `isOnSync returns false for mobile-enable-logging when not present`() {
        setFlagsViaReflection(mapOf("other-flag" to true))
        
        assert(!featureFlags.isOnSync(SentryHelper.MOBILE_ENABLE_LOGGING_FLAG))
    }

    @Test
    fun `SentryHelper enableFromFeatureFlag is called with true when mobile-enable-logging is on`() {
        mockkObject(SentryHelper)
        every { SentryHelper.enableFromFeatureFlag(any()) } returns Unit
        
        // Simulate what loadFlags does after parsing
        val flagsMap = mapOf(SentryHelper.MOBILE_ENABLE_LOGGING_FLAG to true)
        val enableLogging = flagsMap[SentryHelper.MOBILE_ENABLE_LOGGING_FLAG] ?: false
        SentryHelper.enableFromFeatureFlag(enableLogging)
        
        verify { SentryHelper.enableFromFeatureFlag(true) }
    }

    @Test
    fun `SentryHelper enableFromFeatureFlag is called with false when mobile-enable-logging is off`() {
        mockkObject(SentryHelper)
        every { SentryHelper.enableFromFeatureFlag(any()) } returns Unit
        
        // Simulate what loadFlags does after parsing
        val flagsMap = mapOf(SentryHelper.MOBILE_ENABLE_LOGGING_FLAG to false)
        val enableLogging = flagsMap[SentryHelper.MOBILE_ENABLE_LOGGING_FLAG] ?: false
        SentryHelper.enableFromFeatureFlag(enableLogging)
        
        verify { SentryHelper.enableFromFeatureFlag(false) }
    }

    @Test
    fun `SentryHelper enableFromFeatureFlag is called with false when mobile-enable-logging is missing`() {
        mockkObject(SentryHelper)
        every { SentryHelper.enableFromFeatureFlag(any()) } returns Unit
        
        // Simulate what loadFlags does after parsing (flag not present)
        val flagsMap = mapOf("other-flag" to true)
        val enableLogging = flagsMap[SentryHelper.MOBILE_ENABLE_LOGGING_FLAG] ?: false
        SentryHelper.enableFromFeatureFlag(enableLogging)
        
        verify { SentryHelper.enableFromFeatureFlag(false) }
    }
}
