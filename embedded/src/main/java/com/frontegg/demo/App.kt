package com.frontegg.demo

import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ProgressBar
import com.frontegg.android.FronteggApp
import com.frontegg.android.utils.NetworkGate
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.ui.DefaultLoader

class App : Application() {

    companion object {
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        DemoEmbeddedTestMode.consumeBootstrapIfPresent(this)?.let(::applyE2EBootstrap)
            ?: NetworkGate.setE2eForceNetworkPathOffline(false)

        // Initialize FronteggApp. Necessary for working with Frontegg SDK.
        // Optional parameters: useAssetsLinks, useChromeCustomTabs, mainActivityClass
        DefaultLoader.setLoaderProvider {
            val progressBar = ProgressBar(it)
            val colorStateList = ColorStateList.valueOf(Color.RED)
            progressBar.indeterminateTintList = colorStateList

            progressBar
        }
    }

    private fun applyE2EBootstrap(bootstrap: DemoEmbeddedTestMode.BootstrapConfig) {
        NetworkGate.setE2eForceNetworkPathOffline(bootstrap.forceNetworkPathOffline)
        if (bootstrap.resetState) {
            CredentialManager(this).wipeAllStoredCredentials()
        }
        FronteggApp.initializeEmbeddedForLocalE2E(
            context = this,
            fronteggDomain = bootstrap.baseUrl,
            clientId = bootstrap.clientId,
            applicationId = com.frontegg.demo.BuildConfig.FRONTEGG_APPLICATION_ID.takeIf { it.isNotBlank() },
            useAssetsLinks = false,
            useChromeCustomTabs = com.frontegg.demo.BuildConfig.FRONTEGG_USE_CHROME_CUSTOM_TABS,
            mainActivityClass = NavigationActivity::class.java,
            deepLinkScheme = com.frontegg.demo.BuildConfig.FRONTEGG_DEEP_LINK_SCHEME.takeIf { it.isNotBlank() },
            useDiskCacheWebview = false,
            disableAutoRefresh = com.frontegg.demo.BuildConfig.FRONTEGG_DISABLE_AUTO_REFRESH,
            enableOfflineMode = bootstrap.enableOfflineMode ?: true,
            enableSessionPerTenant = com.frontegg.demo.BuildConfig.FRONTEGG_ENABLE_SESSION_PER_TENANT,
            entitlementsEnabled = false,
        )
    }

    /**
     * Instrumented tests run in the same process as this app; they rewrite the bootstrap file then call this
     * on the main thread so SDK config matches the mock server without `am force-stop` (which would kill the test).
     */
    internal fun consumeAndApplyE2EBootstrapFromDisk() {
        DemoEmbeddedTestMode.consumeBootstrapIfPresent(this)?.let(::applyE2EBootstrap)
    }
}