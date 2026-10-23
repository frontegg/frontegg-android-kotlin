package com.frontegg.android.utils

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ObservableValueTest {
    @Test
    fun `ObservableValue_value should return default value`() {
        val observableValue = ObservableValue("")

        assert(observableValue.value == "")
    }

    @Test
    fun `ObservableValue_value should return value`() {
        val observableValue = ObservableValue("")

        observableValue.value = "1"

        assert(observableValue.value == "1")
    }

    @Suppress("DEPRECATION")
    @Test
    fun `ObservableValue_subscribe should provide correct value`() {
        val observableValue = ObservableValue("")

        var value = ""
        observableValue.subscribe { value = it.value }
        observableValue.value = "1"

        assert(value == "1")
    }

    @Suppress("DEPRECATION")
    @Test
    fun `subscribe still receives a value set twice`() {
        val observableValue = ObservableValue("a")
        val received = mutableListOf<String>()

        observableValue.subscribe { received.add(it.value) }
        observableValue.value = "b"
        observableValue.value = "b"

        assert(received == listOf("a", "b", "b"))
    }

    @Test
    fun `value returns the instance set last even when it equals the previous one`() {
        data class Box(val id: Int)
        val first = Box(1)
        val second = Box(1)
        val observableValue = ObservableValue(first)

        observableValue.value = second

        assert(observableValue.value === second)
        assert(observableValue.stateFlow.value == second)
    }

    @Test
    fun `stateFlow holds the initial and the latest value`() {
        val observableValue = ObservableValue<String?>(null)

        assert(observableValue.stateFlow.value == null)

        observableValue.value = "token"

        assert(observableValue.stateFlow.value == "token")
        assert(runBlocking { observableValue.stateFlow.first() } == "token")
    }

    @Test
    fun `stateFlow emits distinct changes to a collector`() = runBlocking {
        val observableValue = ObservableValue(false)
        val collected = mutableListOf<Boolean>()

        val job = launch {
            observableValue.stateFlow.take(2).toList(collected)
        }
        yield()
        observableValue.value = true
        job.join()

        assert(collected == listOf(false, true))
    }

    @Test
    fun `observe runs synchronously until removed`() {
        val observableValue = ObservableValue(0)
        val received = mutableListOf<Int>()

        val remove = observableValue.observe { received.add(it) }
        observableValue.value = 1
        remove()
        observableValue.value = 2

        assert(received == listOf(1))
    }

    @Test
    fun `after concurrent sets the last value equals the stateFlow value`() {
        repeat(20) {
            val observableValue = ObservableValue(0)
            val start = CountDownLatch(1)
            val setters = (1..8).map { setterIndex ->
                Thread {
                    start.await()
                    repeat(500) { iteration -> observableValue.value = setterIndex * 1000 + iteration }
                }.apply { start() }
            }

            start.countDown()
            setters.forEach { it.join() }

            assert(observableValue.stateFlow.value == observableValue.value)
        }
    }

    @Test
    fun `observeFromCurrent delivers the current value first`() {
        val observableValue = ObservableValue("old")
        val received = mutableListOf<String>()

        observableValue.observeFromCurrent { received.add(it) }
        assert(received == listOf("old")) { "received $received" }
        observableValue.value = "new"

        assert(received == listOf("old", "new")) { "received $received" }
    }

    @Test
    fun `observeFromCurrent does not register a listener whose first call throws`() {
        val observableValue = ObservableValue(0)
        val received = mutableListOf<Int>()

        runCatching {
            observableValue.observeFromCurrent { delivered ->
                received.add(delivered)
                if (delivered == 0) throw IllegalStateException("first call fails")
            }
        }
        observableValue.value = 1

        assert(received == listOf(0)) { "received $received" }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `listeners and subscribe consumers are called on the setter's thread`() {
        val observableValue = ObservableValue("initial")
        val listenerThreads = ConcurrentHashMap<String, Thread>()
        val consumerThreads = ConcurrentHashMap<String, Thread>()
        observableValue.observe { listenerThreads[it] = Thread.currentThread() }
        observableValue.subscribe { consumerThreads[it.value] = Thread.currentThread() }

        val setter = Thread { observableValue.value = "set" }.apply { start() }
        setter.join()

        assert(consumerThreads["initial"] == Thread.currentThread())
        assert(listenerThreads["set"] == setter)
        assert(consumerThreads["set"] == setter)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `a subscriber runs without the lock, so another thread can set the value meanwhile`() {
        val observableValue = ObservableValue("initial")
        val received = Collections.synchronizedList(mutableListOf<String>())
        val otherSetterFinished = CountDownLatch(1)
        var otherSetterFinishedInTime = false
        observableValue.subscribe { delivered ->
            received.add(delivered.value)
            if (delivered.value == "first") {
                Thread {
                    observableValue.value = "second"
                    otherSetterFinished.countDown()
                }.start()
                otherSetterFinishedInTime = otherSetterFinished.await(5, TimeUnit.SECONDS)
            }
        }

        observableValue.value = "first"

        assert(otherSetterFinishedInTime)
        assert(received == listOf("initial", "first", "second")) { "received $received" }
        assert(observableValue.value == "second")
    }

    @Test
    fun `a listener runs without the lock, so another thread can set the value meanwhile`() {
        val observableValue = ObservableValue("initial")
        val received = Collections.synchronizedList(mutableListOf<String>())
        val otherSetterFinished = CountDownLatch(1)
        var otherSetterFinishedInTime = false
        observableValue.observe { delivered ->
            received.add(delivered)
            if (delivered == "first") {
                Thread {
                    observableValue.value = "second"
                    otherSetterFinished.countDown()
                }.start()
                otherSetterFinishedInTime = otherSetterFinished.await(5, TimeUnit.SECONDS)
            }
        }

        observableValue.value = "first"

        assert(otherSetterFinishedInTime)
        assert(received == listOf("first", "second")) { "received $received" }
        assert(observableValue.value == "second")
    }
}
