package com.frontegg.android.utils

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Before
import org.junit.Test

class TraceIdLoggerTest {

    @Before
    fun setUp() {
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
    fun `extractAndLogTraceId handles response with trace ID header`() {
        val request = Request.Builder()
            .url("https://test.frontegg.com/api/test")
            .build()
        
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .addHeader("x-frontegg-trace-id", "trace-123-456")
            .build()
        
        // Should not throw
        TraceIdLogger.extractAndLogTraceId(response)
    }

    @Test
    fun `extractAndLogTraceId handles response without trace ID header`() {
        val request = Request.Builder()
            .url("https://test.frontegg.com/api/test")
            .build()
        
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        
        // Should not throw even without the header
        TraceIdLogger.extractAndLogTraceId(response)
    }

    @Test
    fun `extractAndLogTraceId handles error response`() {
        val request = Request.Builder()
            .url("https://test.frontegg.com/api/test")
            .build()
        
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .addHeader("x-frontegg-trace-id", "error-trace-789")
            .build()
        
        // Should not throw
        TraceIdLogger.extractAndLogTraceId(response)
    }

    @Test
    fun `extractAndLogTraceId handles 401 response`() {
        val request = Request.Builder()
            .url("https://test.frontegg.com/api/test")
            .build()
        
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .addHeader("x-frontegg-trace-id", "auth-trace-abc")
            .build()
        
        // Should not throw
        TraceIdLogger.extractAndLogTraceId(response)
    }

    @Test
    fun `extractAndLogTraceId handles multiple headers`() {
        val request = Request.Builder()
            .url("https://test.frontegg.com/api/test")
            .build()
        
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .addHeader("x-frontegg-trace-id", "trace-1")
            .addHeader("x-request-id", "request-2")
            .addHeader("x-correlation-id", "correlation-3")
            .build()
        
        // Should not throw
        TraceIdLogger.extractAndLogTraceId(response)
    }

    @Test
    fun `extractAndLogTraceId is called for each response`() {
        val request = Request.Builder()
            .url("https://test.frontegg.com/api/test")
            .build()
        
        val response1 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .addHeader("x-frontegg-trace-id", "trace-a")
            .build()
        
        val response2 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .addHeader("x-frontegg-trace-id", "trace-b")
            .build()
        
        TraceIdLogger.extractAndLogTraceId(response1)
        TraceIdLogger.extractAndLogTraceId(response2)
        
        // Both should be processed without error
    }
}
