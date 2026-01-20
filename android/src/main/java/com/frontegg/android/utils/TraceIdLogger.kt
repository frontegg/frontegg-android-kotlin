package com.frontegg.android.utils

import io.sentry.SentryLevel
import okhttp3.Response
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object TraceIdLogger {
    private const val MAX_TRACE_IDS = 100
    private const val FILE_NAME = "frontegg-trace-ids.log"
    private val lock = ReentrantLock()

    fun extractAndLogTraceId(response: Response) {
        if (!SentryHelper.isEnabled()) return

        val traceId = response.header("frontegg-trace-id") ?: return

        // Breadcrumb for production correlation (matches iOS behavior)
        SentryHelper.addBreadcrumb(
            message = "frontegg-trace-id received",
            category = "network",
            level = SentryLevel.INFO,
            data = mapOf("frontegg_trace_id" to traceId),
        )

        // Local file (useful for local dev)
        logTraceId(traceId)
    }

    fun logTraceId(traceId: String) {
        if (!SentryHelper.isEnabled()) return

        val ctx = SentryHelper.getAppContextOrNull() ?: return
        val file = File(ctx.filesDir, FILE_NAME)

        lock.withLock {
            val existing = if (file.exists()) {
                file.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

            val updated = (listOf(traceId) + existing).take(MAX_TRACE_IDS)
            file.writeText(updated.joinToString("\n"))
        }
    }
}

