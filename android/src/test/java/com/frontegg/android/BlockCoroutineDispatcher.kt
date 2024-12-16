package com.frontegg.android

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

class BlockCoroutineDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }
}