package com.frontegg.android.utils

class FronteggCallback {
    private val callbacks = mutableListOf<() -> Unit>()

    fun addCallback(callback: () -> Unit) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: () -> Unit) {
        callbacks.remove(callback)
    }

    fun trigger() {
        for (callback in callbacks) {
            callback()
        }
    }
}