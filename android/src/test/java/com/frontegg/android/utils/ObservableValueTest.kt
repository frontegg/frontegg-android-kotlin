package com.frontegg.android.utils

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test

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
}
