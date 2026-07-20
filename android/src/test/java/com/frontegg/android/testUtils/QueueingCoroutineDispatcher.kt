package com.frontegg.android.testUtils

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * A dispatcher that captures dispatched work instead of running it inline, so a
 * test can assert what has (and has not) executed at the moment a method returns,
 * then release the queued work on demand.
 *
 * The opposite of [BlockCoroutineDispatcher] (which runs everything inline). Used
 * to prove that blocking work is offloaded to a background dispatcher rather than
 * executed on the caller's thread.
 */
class QueueingCoroutineDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    val size: Int get() = queue.size

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    /** Drops any queued work without running it (e.g. construction-time launches). */
    fun clearQueue() {
        queue.clear()
    }

    /** Runs all queued work, including anything queued while draining. */
    fun runQueued() {
        while (queue.isNotEmpty()) {
            queue.removeFirst().run()
        }
    }
}
