package com.frontegg.android.utils

import android.content.Context
import android.content.pm.PackageManager
import com.frontegg.android.BuildConfig
import com.frontegg.android.models.FronteggConstants
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User
import java.util.concurrent.atomic.AtomicBoolean

object SentryHelper {
    private const val CONFIGURED_DSN =
        "https://7f13156fe85003ccf1b968a476787bb1@o362363.ingest.us.sentry.io/4510708685471744"
    private const val SDK_NAME = "FronteggAndroidKotlin"

    private val initialized = AtomicBoolean(false)
    private val enabled = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    fun isEnabled(): Boolean = enabled.get() && initialized.get()

    fun getAppContextOrNull(): Context? = appContext

    fun initialize(context: Context, constants: FronteggConstants?) {
        if (initialized.get()) return

        val enableSentryLogging = constants?.enableSentryLogging ?: false
        if (!enableSentryLogging) return

        val applicationContext = context.applicationContext
        appContext = applicationContext

        // SentryAndroid.init is synchronous; we still guard against double-init.
        if (!initialized.compareAndSet(false, true)) return

        val maxCacheItems = (constants?.sentryMaxQueueSize ?: 30).coerceAtLeast(1)

        SentryAndroid.init(applicationContext) { options ->
            options.dsn = CONFIGURED_DSN
            options.isDebug = false
            options.isAttachStacktrace = true
            options.isEnableAutoSessionTracking = true
            options.tracesSampleRate = 1.0
            options.sessionTrackingIntervalMillis = 30_000

            // Offline queue size (mirrors iOS sentryMaxQueueSize -> maxCacheItems)
            options.maxCacheItems = maxCacheItems

            // Avoid extra network instrumentation; we do our own lightweight breadcrumbs.
            options.isEnableNetworkEventBreadcrumbs = false

            // Environment/release naming similar to iOS.
            options.environment = applicationContext.packageName
            options.release = buildReleaseName(applicationContext)
        }

        enabled.set(true)

        configureGlobalMetadata(constants)
    }

    private fun configureGlobalMetadata(constants: FronteggConstants?) {
        if (!isEnabled()) return

        val ctx = appContext ?: return
        Sentry.configureScope { scope ->
            scope.setTag("sdk.name", SDK_NAME)
            scope.setTag("sdk.version", BuildConfig.FRONTEGG_SDK_VERSION)
            scope.setTag("platform", "android")
            scope.setTag("package_name", ctx.packageName)

            constants?.let {
                scope.setTag("baseUrl", it.baseUrl)
                scope.setTag("embeddedMode", it.useChromeCustomTabs.not().toString())
                scope.setTag("useAssetsLinks", it.useAssetsLinks.toString())
                scope.setTag("useChromeCustomTabs", it.useChromeCustomTabs.toString())
                scope.setTag("useDiskCacheWebview", it.useDiskCacheWebview.toString())
                scope.setTag("disableAutoRefresh", it.disableAutoRefresh.toString())
                scope.setTag("enableSessionPerTenant", it.enableSessionPerTenant.toString())
                scope.setTag("enableSentryLogging", it.enableSentryLogging.toString())
                scope.setTag("sentryMaxQueueSize", it.sentryMaxQueueSize.toString())
            }
        }
    }

    fun setBaseUrl(baseUrl: String) {
        if (!isEnabled()) return
        Sentry.configureScope { scope ->
            scope.setTag("baseUrl", baseUrl)
            scope.setContexts("frontegg", mapOf("baseUrl" to baseUrl))
        }
    }

    fun logError(t: Throwable, context: Map<String, Map<String, Any?>> = emptyMap()) {
        if (!isEnabled()) return
        Sentry.captureException(t, Hint()) { scope ->
            context.forEach { (k, v) -> scope.setContexts(k, v) }
        }
    }

    fun logMessage(message: String, level: SentryLevel = SentryLevel.ERROR, context: Map<String, Map<String, Any?>> = emptyMap()) {
        if (!isEnabled()) return
        Sentry.captureMessage(message, level) { scope ->
            context.forEach { (k, v) -> scope.setContexts(k, v) }
        }
    }

    fun addBreadcrumb(
        message: String,
        category: String = "default",
        level: SentryLevel = SentryLevel.INFO,
        data: Map<String, Any?> = emptyMap(),
    ) {
        if (!isEnabled()) return

        val breadcrumb = Breadcrumb()
        breadcrumb.message = message
        breadcrumb.category = category
        breadcrumb.level = level
        data.forEach { (k, v) ->
            breadcrumb.setData(k, v?.toString() ?: "null")
        }
        Sentry.addBreadcrumb(breadcrumb)
    }

    fun setUser(userId: String?, email: String? = null, username: String? = null) {
        if (!isEnabled()) return
        val user = User().apply {
            id = userId
            this.email = email
            this.username = username
        }
        Sentry.setUser(user)
    }

    fun clearUser() {
        if (!isEnabled()) return
        Sentry.setUser(null)
    }

    fun setTag(key: String, value: String) {
        if (!isEnabled()) return
        Sentry.configureScope { scope -> scope.setTag(key, value) }
    }

    fun setContext(key: String, value: Map<String, Any?>) {
        if (!isEnabled()) return
        Sentry.configureScope { scope -> scope.setContexts(key, value) }
    }

    private fun buildReleaseName(ctx: Context): String {
        val pkg = ctx.packageName
        val pm = ctx.packageManager
        return try {
            val pi = pm.getPackageInfo(pkg, 0)
            val versionName = pi.versionName ?: "unknown"
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                pi.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toString()
            }
            "$pkg@$versionName+$versionCode (SDK: ${BuildConfig.FRONTEGG_SDK_VERSION})"
        } catch (_: PackageManager.NameNotFoundException) {
            "$pkg (SDK: ${BuildConfig.FRONTEGG_SDK_VERSION})"
        }
    }
}

