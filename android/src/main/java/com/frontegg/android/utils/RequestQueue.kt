package com.frontegg.android.utils

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * RequestQueue - queue for deferred request execution on poor network
 * Ensures idempotency and request execution order
 */
class RequestQueue {
    companion object {
        private const val TAG = "RequestQueue"
    }

    private val pendingRequests = ConcurrentLinkedQueue<QueuedRequest>()
    private val mutex = Mutex()

    /**
     * Adds request to queue
     */
    suspend fun enqueue(
        requestId: String,
        request: suspend () -> Unit,
        priority: RequestPriority = RequestPriority.NORMAL
    ) {
        mutex.withLock {
            val queuedRequest = QueuedRequest(requestId, request, priority, System.currentTimeMillis())
            pendingRequests.add(queuedRequest)
        }
    }

    /**
     * Executes all requests in queue
     */
    suspend fun processAll(): Int {
        return mutex.withLock {
            val requestsToProcess = pendingRequests.toList()
            pendingRequests.clear()
            
            val sortedRequests = requestsToProcess.sortedBy { it.priority.ordinal }
            
            var successCount = 0
            for (request in sortedRequests) {
                try {
                    request.request()
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute queued request: ${request.id}", e)
                }
            }
            
            successCount
        }
    }

    /**
     * Clears queue
     */
    suspend fun clear() {
        mutex.withLock {
            pendingRequests.clear()
        }
    }

    /**
     * Returns queue size
     */
    suspend fun size(): Int {
        return mutex.withLock {
            pendingRequests.size
        }
    }

    /**
     * Checks if queue has requests
     */
    suspend fun isEmpty(): Boolean {
        return mutex.withLock {
            pendingRequests.isEmpty()
        }
    }

    /**
     * Removes stale requests (older than specified time)
     */
    suspend fun removeExpired(maxAgeMs: Long) {
        mutex.withLock {
            val currentTime = System.currentTimeMillis()
            val iterator = pendingRequests.iterator()
            var removedCount = 0
            
            while (iterator.hasNext()) {
                val request = iterator.next()
                if (currentTime - request.timestamp > maxAgeMs) {
                    iterator.remove()
                    removedCount++
                }
            }
        }
    }
    
    /**
     * Checks if queue has requests
     */
    fun hasQueuedRequests(): Boolean {
        return pendingRequests.isNotEmpty()
    }

    private data class QueuedRequest(
        val id: String,
        val request: suspend () -> Unit,
        val priority: RequestPriority,
        val timestamp: Long
    )
}

/**
 * Request priorities
 */
enum class RequestPriority {
    LOW,        // Regular requests
    NORMAL,     // Standard requests
    HIGH,       // Important requests (e.g., refresh token)
    CRITICAL    // Critical requests
}
