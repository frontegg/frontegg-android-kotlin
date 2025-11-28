package com.frontegg.android.utils

import android.content.Context
import android.util.Log
import com.frontegg.android.models.FronteggConstants


const val TAG: String = "FronteggUtils"

object FronteggConstantsProvider {
    /**
     * Lazily retrieves [FronteggConstants] for the current [Context] by reading values
     * from the app's `BuildConfig` class using reflection.
     *
     * Determines the fully qualified launch activity class name and then resolves the appropriate
     * `BuildConfig` class. Fields like `FRONTEGG_DOMAIN`, `FRONTEGG_CLIENT_ID`, etc., are expected
     * to be present in the `BuildConfig`.
     *
     * This is useful for dynamic module or flavor support where the actual `BuildConfig` class
     * might not be in the base package.
     *
     * @receiver Context used to resolve the package name and launch activity.
     * @return A [FronteggConstants] object containing configuration values.
     */
    fun fronteggConstants(context: Context): FronteggConstants {
        val mainActivity = getLaunchActivityName(context)
        Log.d(TAG, "packageName: ${context.packageName}, mainActivity: $mainActivity")
        val buildConfigClass =
            getBuildConfigClass(mainActivity?.substringBeforeLast('.') ?: context.packageName)

        val baseUrl = safeGetValueFromBuildConfig(buildConfigClass, "FRONTEGG_DOMAIN", "")
        val clientId = safeGetValueFromBuildConfig(buildConfigClass, "FRONTEGG_CLIENT_ID", "")

        val applicationId =
            safeGetNullableValueFromBuildConfig(buildConfigClass, "FRONTEGG_APPLICATION_ID", "")

        val useAssetsLinks =
            safeGetValueFromBuildConfig(buildConfigClass, "FRONTEGG_USE_ASSETS_LINKS", false)
        val useChromeCustomTabs = safeGetValueFromBuildConfig(
            buildConfigClass, "FRONTEGG_USE_CHROME_CUSTOM_TABS", false
        )

        val deepLinkScheme = safeGetNullableValueFromBuildConfig(
            buildConfigClass, "FRONTEGG_DEEP_LINK_SCHEME", ""
        )

        val useDiskCacheWebview =
            safeGetValueFromBuildConfig(buildConfigClass, "FRONTEGG_USE_DISK_CACHE_WEBVIEW", false)

        val mainActivityClass =
            safeGetNullableValueFromBuildConfig(
                buildConfigClass,
                "FRONTEGG_MAIN_ACTIVITY_CLASS",
                ""
            )

        val disableAutoRefresh =
            safeGetValueFromBuildConfig(buildConfigClass, "FRONTEGG_DISABLE_AUTO_REFRESH", false)

        val enableSessionPerTenant =
            safeGetValueFromBuildConfig(buildConfigClass, "FRONTEGG_ENABLE_SESSION_PER_TENANT", false)

        return FronteggConstants(
            baseUrl = baseUrl,
            clientId = clientId,
            applicationId = applicationId,
            useAssetsLinks = useAssetsLinks,
            useChromeCustomTabs = useChromeCustomTabs,
            deepLinkScheme = deepLinkScheme,
            useDiskCacheWebview = useDiskCacheWebview,
            mainActivityClass = mainActivityClass,
            disableAutoRefresh = disableAutoRefresh,
            enableSessionPerTenant = enableSessionPerTenant,
        )
    }
}


/**
 * Returns the fully qualified name of the main launch activity of the application.
 *
 * Uses [PackageManager.getLaunchIntentForPackage] and resolves the activity info to obtain
 * the class name.
 *
 * @param context Application context.
 * @return The fully qualified class name of the launch activity, or `null` if not found.
 */
fun getLaunchActivityName(context: Context): String? {
    val launcherIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val launchActivityInfo = launcherIntent!!.resolveActivityInfo(context.packageManager, 0)
    return try {
        launchActivityInfo.name
    } catch (e: Exception) {
        null
    }
}

/**
 * Recursively finds the `BuildConfig` class for the given package name or its parent packages.
 *
 * This function attempts to locate the correct `BuildConfig` class by checking the full
 * package name first and then walking up the package hierarchy until it finds a valid one.
 *
 * This supports cases where `BuildConfig` might be generated in a parent module's package.
 *
 * @param packageName The package name to resolve the `BuildConfig` from.
 * @return The [Class] object representing the `BuildConfig`.
 * @throws ClassNotFoundException If no `BuildConfig` class is found in any package level.
 */
fun getBuildConfigClass(packageName: String): Class<*> {
    if (!packageName.contains('.')) {
        throw ClassNotFoundException("Invalid package name: $packageName. Failed to retrieve BuildConfig class.")
    }

    val className = "$packageName.BuildConfig"

    return try {
        Class.forName(className)
    } catch (e: ClassNotFoundException) {
        Log.d(TAG, "Class not found: $className, checking parent namespace")

        val parentPackageName = packageName.substringBeforeLast('.')

        if (parentPackageName.isNotEmpty()) {
            getBuildConfigClass(parentPackageName)
        } else {
            throw ClassNotFoundException("Failed to retrieve BuildConfig class for package: $packageName after checking all namespaces.")
        }
    }
}

/**
 * Safely retrieves a nullable value of type [T] from the given `BuildConfig` class by field name.
 *
 * If the field is not found or cannot be cast, `null` is returned.
 *
 * @param T The expected type of the field.
 * @param buildConfigClass The class from which to retrieve the field.
 * @param name The name of the field in `BuildConfig`.
 * @param default A default value used to infer the type, but not returned.
 * @return The value of the field, or `null` if not found or invalid.
 */
inline fun <reified T> safeGetNullableValueFromBuildConfig(
    buildConfigClass: Class<*>,
    name: String,
    default: T,
): T? {
    return try {
        val field = buildConfigClass.getField(name)
        field.get(default) as T
    } catch (e: Exception) {
        Log.e(
            TAG, "Field '$name' not found in BuildConfig, return null"
        )
        null
    }
}

/**
 * Safely retrieves a non-null value of type [T] from the given `BuildConfig` class by field name.
 *
 * If the field is not found or cannot be cast, the default value is returned.
 *
 * @param T The expected type of the field.
 * @param buildConfigClass The class from which to retrieve the field.
 * @param name The name of the field in `BuildConfig`.
 * @param default A default value to return in case of failure.
 * @return The value of the field, or [default] if not found or invalid.
 */
inline fun <reified T> safeGetValueFromBuildConfig(
    buildConfigClass: Class<*>,
    name: String,
    default: T
): T {
    return try {
        val field = buildConfigClass.getField(name)
        field.get(default) as T
    } catch (e: Exception) {
        Log.e(
            TAG, "Field '$name' not found in BuildConfig, return default $default"
        )
        default
    }
}