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
        val f = File(ctx.filesDir, DemoEmbeddedTestMode.BOOTSTRAP_FILE_NAME)
        f.writeText(jo.toString())
    }
}
