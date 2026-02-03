package com.frontegg.android.utils

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class RequestQueueTest {

    private lateinit var requestQueue: RequestQueue

    @Before
    fun setUp() {
        requestQueue = RequestQueue()
        
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
    }

    @Test
    fun `enqueue adds request to queue`() = runBlocking {
        var executed = false
        
        requestQueue.enqueue("test-1", { executed = true })
        
        assert(requestQueue.hasQueuedRequests())
        assert(requestQueue.size() == 1)
    }

    @Test
    fun `processAll executes all requests`() = runBlocking {
        var count = 0
        
        requestQueue.enqueue("test-1", { count++ })
        requestQueue.enqueue("test-2", { count++ })
        requestQueue.enqueue("test-3", { count++ })
        
        val processed = requestQueue.processAll()
        
        assert(processed == 3)
        assert(count == 3)
        assert(requestQueue.isEmpty())
    }

    @Test
    fun `processAll respects priority order`() = runBlocking {
        val executionOrder = mutableListOf<String>()
        
        requestQueue.enqueue("low", { executionOrder.add("low") }, RequestPriority.LOW)
        requestQueue.enqueue("high", { executionOrder.add("high") }, RequestPriority.HIGH)
        requestQueue.enqueue("normal", { executionOrder.add("normal") }, RequestPriority.NORMAL)
        requestQueue.enqueue("critical", { executionOrder.add("critical") }, RequestPriority.CRITICAL)
        
        requestQueue.processAll()
        
        // Should be sorted by priority ordinal: LOW(0), NORMAL(1), HIGH(2), CRITICAL(3)
        assert(executionOrder == listOf("low", "normal", "high", "critical"))
    }

    @Test
    fun `processAll handles exceptions gracefully`() = runBlocking {
        var successCount = 0
        
        requestQueue.enqueue("success-1", { successCount++ })
        requestQueue.enqueue("fail", { throw RuntimeException("Test exception") })
        requestQueue.enqueue("success-2", { successCount++ })
        
        val processed = requestQueue.processAll()
        
        // Only 2 should succeed
        assert(processed == 2)
        assert(successCount == 2)
    }

    @Test
    fun `clear removes all requests`() = runBlocking {
        requestQueue.enqueue("test-1", { })
        requestQueue.enqueue("test-2", { })
        
        assert(requestQueue.size() == 2)
        
        requestQueue.clear()
        
        assert(requestQueue.isEmpty())
        assert(requestQueue.size() == 0)
    }

    @Test
    fun `isEmpty returns true for empty queue`() = runBlocking {
        assert(requestQueue.isEmpty())
    }

    @Test
    fun `isEmpty returns false for non-empty queue`() = runBlocking {
        requestQueue.enqueue("test-1", { })
        
        assert(!requestQueue.isEmpty())
    }

    @Test
    fun `size returns correct count`() = runBlocking {
        assert(requestQueue.size() == 0)
        
        requestQueue.enqueue("test-1", { })
        assert(requestQueue.size() == 1)
        
        requestQueue.enqueue("test-2", { })
        assert(requestQueue.size() == 2)
        
        requestQueue.processAll()
        assert(requestQueue.size() == 0)
    }

    @Test
    fun `hasQueuedRequests returns correct state`() = runBlocking {
        assert(!requestQueue.hasQueuedRequests())
        
        requestQueue.enqueue("test-1", { })
        assert(requestQueue.hasQueuedRequests())
        
        requestQueue.processAll()
        assert(!requestQueue.hasQueuedRequests())
    }

    @Test
    fun `removeExpired removes old requests`() = runBlocking {
        requestQueue.enqueue("test-1", { })
        
        // Wait a bit
        Thread.sleep(100)
        
        // Add another request
        requestQueue.enqueue("test-2", { })
        
        // Remove requests older than 50ms
        requestQueue.removeExpired(50)
        
        // First request should be removed, second should remain
        assert(requestQueue.size() == 1)
    }

    @Test
    fun `removeExpired keeps recent requests`() = runBlocking {
        requestQueue.enqueue("test-1", { })
        requestQueue.enqueue("test-2", { })
        
        // Remove requests older than 1 hour - none should be removed
        requestQueue.removeExpired(3600000)
        
        assert(requestQueue.size() == 2)
    }

    @Test
    fun `multiple enqueue operations work correctly`() = runBlocking {
        val count = 10
        
        for (i in 1..count) {
            requestQueue.enqueue("test-$i", { })
        }
        
        // Verify size after all enqueues
        val finalSize = requestQueue.size()
        assert(finalSize == count) { "Expected $count but got $finalSize" }
    }

    @Test
    fun `default priority is NORMAL`() = runBlocking {
        val executionOrder = mutableListOf<String>()
        
        // Enqueue without specifying priority (should use NORMAL)
        requestQueue.enqueue("default", { executionOrder.add("default") })
        requestQueue.enqueue("low", { executionOrder.add("low") }, RequestPriority.LOW)
        requestQueue.enqueue("high", { executionOrder.add("high") }, RequestPriority.HIGH)
        
        requestQueue.processAll()
        
        // LOW comes first, then NORMAL (default), then HIGH
        assert(executionOrder == listOf("low", "default", "high"))
    }
}
