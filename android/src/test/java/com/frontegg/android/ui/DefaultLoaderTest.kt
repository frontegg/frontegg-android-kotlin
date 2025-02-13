package com.frontegg.android.ui

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DefaultLoaderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk()
        mockkObject(DefaultLoader)
        every { DefaultLoader.setUpLoader(any()) } returns Unit
    }

    @Test
    fun `test default loader creation`() {
        val progressBar = mockk<ProgressBar>()
        every { DefaultLoader.createDefault(context) } returns progressBar

        val loaderView = DefaultLoader.create(context)
        assertNotNull(loaderView)
        assertEquals(progressBar, loaderView)
    }

    @Test
    fun `test custom loader provider`() {
        val mockView = mockk<View>()
        val mockLoaderProvider = LoaderProvider { mockView }

        DefaultLoader.setLoaderProvider(mockLoaderProvider)
        val loaderView = DefaultLoader.create(context)

        assertNotNull(loaderView)
        assertEquals(mockView, loaderView)
    }
}
