package com.frontegg.android.utils

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import io.mockk.*

class FronteggCallbackTest {

    private lateinit var fronteggCallback: FronteggCallback

    @Before
    fun setup() {
        fronteggCallback = FronteggCallback()
    }

    @Test
    fun `should add callback correctly`() {
        // Arrange
        val mockCallback = mockk<() -> Unit>(relaxed = true)

        // Act
        fronteggCallback.addCallback(mockCallback)

        // Assert
        assertEquals(1, fronteggCallback.callbacks.size)
        assertTrue(fronteggCallback.callbacks.contains(mockCallback))
    }

    @Test
    fun `should remove callback correctly`() {
        // Arrange
        val mockCallback = mockk<() -> Unit>(relaxed = true)
        fronteggCallback.addCallback(mockCallback)

        // Act
        fronteggCallback.removeCallback(mockCallback)

        // Assert
        assertEquals(0, fronteggCallback.callbacks.size)
        assertFalse(fronteggCallback.callbacks.contains(mockCallback))
    }

    @Test
    fun `should trigger all added callbacks`() {
        // Arrange
        val mockCallback1 = mockk<() -> Unit>(relaxed = true)
        val mockCallback2 = mockk<() -> Unit>(relaxed = true)
        fronteggCallback.addCallback(mockCallback1)
        fronteggCallback.addCallback(mockCallback2)

        // Act
        fronteggCallback.trigger()

        // Assert
        verify { mockCallback1() }
        verify { mockCallback2() }
    }

    @Test
    fun `should not trigger removed callbacks`() {
        // Arrange
        val mockCallback = mockk<() -> Unit>(relaxed = true)
        fronteggCallback.addCallback(mockCallback)
        fronteggCallback.removeCallback(mockCallback)

        // Act
        fronteggCallback.trigger()

        // Assert
        verify(exactly = 0) { mockCallback() }
    }
}