package com.frontegg.android.utils

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal object EmptyBuildConfig

internal object NumericProjectNumberConfig {
    const val FRONTEGG_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER = 123456789012L
}

internal object StringProjectNumberConfig {
    const val FRONTEGG_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER = " 123456789012 "
}

internal object EmptyProjectNumberConfig {
    const val FRONTEGG_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER = " "
}

internal object NegativeProjectNumberConfig {
    const val FRONTEGG_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER = -123L
}

internal object InvalidProjectNumberConfig {
    const val FRONTEGG_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER = "not-a-number"
}

internal object DPoPEnabledConfig {
    const val FRONTEGG_ENABLE_DPOP = true
}

internal object DPoPDisabledConfig {
    const val FRONTEGG_ENABLE_DPOP = false
}

internal object StringDPoPEnabledConfig {
    const val FRONTEGG_ENABLE_DPOP = " true "
}

internal object StringDPoPDisabledConfig {
    const val FRONTEGG_ENABLE_DPOP = "false"
}

internal object InvalidStringDPoPConfig {
    const val FRONTEGG_ENABLE_DPOP = "yes"
}

internal object NumericDPoPConfig {
    const val FRONTEGG_ENABLE_DPOP = 1
}

class FronteggConstantsProviderTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `an absent project number turns attestation off without an error log`() {
        assertEquals(0L, FronteggConstantsProvider.cloudProjectNumber(EmptyBuildConfig::class.java))

        verify(exactly = 0) { Log.e(any(), any()) }
    }

    @Test
    fun `a project number is read from a numeric or string field`() {
        val numericProjectNumber = FronteggConstantsProvider.cloudProjectNumber(NumericProjectNumberConfig::class.java)
        val stringProjectNumber = FronteggConstantsProvider.cloudProjectNumber(StringProjectNumberConfig::class.java)

        assertEquals(123456789012L, numericProjectNumber)
        assertEquals(123456789012L, stringProjectNumber)
    }

    @Test
    fun `an empty or non-positive project number turns attestation off`() {
        assertEquals(0L, FronteggConstantsProvider.cloudProjectNumber(EmptyProjectNumberConfig::class.java))
        assertEquals(0L, FronteggConstantsProvider.cloudProjectNumber(NegativeProjectNumberConfig::class.java))

        verify(exactly = 0) { Log.e(any(), any()) }
    }

    @Test
    fun `a project number that is not a number turns attestation off with an error log`() {
        assertEquals(0L, FronteggConstantsProvider.cloudProjectNumber(InvalidProjectNumberConfig::class.java))

        verify(exactly = 1) { Log.e(any(), any()) }
    }

    @Test
    fun `an absent DPoP flag leaves DPoP off without an error log`() {
        assertFalse(FronteggConstantsProvider.isDPoPEnabled(EmptyBuildConfig::class.java))

        verify(exactly = 0) { Log.e(any(), any()) }
    }

    @Test
    fun `the DPoP flag is read as a boolean`() {
        assertTrue(FronteggConstantsProvider.isDPoPEnabled(DPoPEnabledConfig::class.java))
        assertFalse(FronteggConstantsProvider.isDPoPEnabled(DPoPDisabledConfig::class.java))

        verify(exactly = 0) { Log.e(any(), any()) }
    }

    @Test
    fun `the DPoP flag is read from a trimmed string`() {
        assertTrue(FronteggConstantsProvider.isDPoPEnabled(StringDPoPEnabledConfig::class.java))
        assertFalse(FronteggConstantsProvider.isDPoPEnabled(StringDPoPDisabledConfig::class.java))

        verify(exactly = 0) { Log.e(any(), any()) }
    }

    @Test
    fun `a DPoP flag string that is not true or false leaves DPoP off with an error log`() {
        assertFalse(FronteggConstantsProvider.isDPoPEnabled(InvalidStringDPoPConfig::class.java))

        verify(exactly = 1) { Log.e(any(), any()) }
    }

    @Test
    fun `a DPoP flag of another type leaves DPoP off with an error log`() {
        assertFalse(FronteggConstantsProvider.isDPoPEnabled(NumericDPoPConfig::class.java))

        verify(exactly = 1) { Log.e(any(), any()) }
    }
}
