package com.frontegg.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class LibraryManifestDeepLinkTest {

    private fun declaredPathPrefixes(): List<String> {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue(
            "library manifest not found at ${manifest.absolutePath}",
            manifest.exists()
        )

        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(manifest)

        val activities = document.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            if (activity.getAttribute("android:name") != EmbeddedAuthActivity::class.java.name) {
                continue
            }

            val data = activity.getElementsByTagName("data")
            return (0 until data.length)
                .map { (data.item(it) as Element).getAttribute("android:pathPrefix") }
                .filter { it.isNotEmpty() }
        }

        return emptyList()
    }

    @Test
    fun `library manifest declares the unlock deep link`() {
        assertTrue(
            "EmbeddedAuthActivity must declare /oauth/account/unlock so the OS routes the " +
                "unlock email link to the app instead of a browser",
            declaredPathPrefixes().contains("/oauth/account/unlock")
        )
    }

    @Test
    fun `library manifest keeps the previously working deep links`() {
        val prefixes = declaredPathPrefixes()
        listOf(
            "/oauth/account/activate",
            "/oauth/account/invitation/accept",
            "/oauth/account/reset-password",
            "/oauth/account/login/magic-link",
        ).forEach { assertTrue(it, prefixes.contains(it)) }
    }
}
