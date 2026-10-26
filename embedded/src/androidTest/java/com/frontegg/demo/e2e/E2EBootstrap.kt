package com.frontegg.demo.e2e

import androidx.test.platform.app.InstrumentationRegistry
import com.frontegg.demo.DemoEmbeddedTestMode
import org.json.JSONObject
import java.io.File

internal object E2EBootstrap {

    fun write(
        baseUrl: String,
        clientId: String,
        resetState: Boolean,
        forceNetworkPathOffline: Boolean,
        enableOfflineMode: Boolean?,
        enableDPoP: Boolean = false,
        playIntegrityCloudProjectNumber: Long = 0L,
        useNativeGoogleSignIn: Boolean = false,
    ) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val jo = JSONObject()
        jo.put("baseUrl", baseUrl.trimEnd('/'))
        jo.put("clientId", clientId)
        jo.put("resetState", resetState)
        jo.put("forceNetworkPathOffline", forceNetworkPathOffline)
        if (enableOfflineMode != null) {
            jo.put("enableOfflineMode", enableOfflineMode)
        }
        jo.put("enableDPoP", enableDPoP)
        jo.put("playIntegrityCloudProjectNumber", playIntegrityCloudProjectNumber)
        jo.put("useNativeGoogleSignIn", useNativeGoogleSignIn)
        val f = File(ctx.filesDir, DemoEmbeddedTestMode.BOOTSTRAP_FILE_NAME)
        f.writeText(jo.toString())
    }
}
