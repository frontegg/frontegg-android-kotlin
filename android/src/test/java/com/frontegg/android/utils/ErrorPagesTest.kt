package com.frontegg.android.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorPagesTest {

    @Test
    fun `generateErrorPage contains message in output`() {
        val message = "Custom error message"
        val html = generateErrorPage(message = message)
        assertTrue(html.contains(message))
    }

    @Test
    fun `generateErrorPage contains Uh-oh title`() {
        val html = generateErrorPage(message = "test")
        assertTrue(html.contains("Uh-oh!"))
    }

    @Test
    fun `generateErrorPage contains Try again button`() {
        val html = generateErrorPage(message = "test")
        assertTrue(html.contains("Try again"))
    }

    @Test
    fun `generateErrorPage contains html structure`() {
        val html = generateErrorPage(message = "test")
        assertTrue(html.contains("<html"))
        assertTrue(html.contains("</html>"))
        assertTrue(html.contains("<body>"))
    }

    @Test
    fun `generateErrorPage with url contains url in script`() {
        val url = "https://example.com/retry"
        val html = generateErrorPage(message = "test", url = url)
        assertTrue(html.contains(url))
    }

    @Test
    fun `generateErrorPage with empty message still produces valid html`() {
        val html = generateErrorPage(message = "")
        assertFalse(html.isBlank())
        assertTrue(html.contains("</html>"))
    }
}
