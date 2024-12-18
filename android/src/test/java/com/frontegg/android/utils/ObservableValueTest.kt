package com.frontegg.android.utils

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

    @Test
    fun `ObservableValue_subscribe should provide correct value`() {
        val observableValue = ObservableValue("")

        var value = ""
        observableValue.subscribe { value = it.value }
        observableValue.value = "1"

        assert(value == "1")
    }
}