package com.frontegg.android.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface DispatcherProvider {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
}

object DefaultDispatcherProvider : DispatcherProvider {
    @Suppress("InjectDispatcher")
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Main
    @Suppress("InjectDispatcher")
    override val default: CoroutineDispatcher = Dispatchers.Default
}


