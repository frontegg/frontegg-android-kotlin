package com.frontegg.android.models

data class FronteggConstants(
    val baseUrl: String,
    val clientId: String,
    val applicationId: String?,
    val useAssetsLinks: Boolean,
    val useChromeCustomTabs: Boolean,
    val deepLinkScheme: String?,
    val useDiskCacheWebview: Boolean,
    val mainActivityClass: String?,
    val disableAutoRefresh: Boolean,
    val enableSessionPerTenant: Boolean,
    val sentryMaxQueueSize: Int,
    val fronteggOrganization: String?,
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            Pair("baseUrl", baseUrl),
            Pair("clientId", clientId),
            Pair("applicationId", applicationId),
            Pair("useAssetsLinks", useAssetsLinks),
            Pair("useChromeCustomTabs", useChromeCustomTabs),
            Pair("deepLinkScheme", deepLinkScheme),
            Pair("useDiskCacheWebview", useDiskCacheWebview),
            Pair("mainActivityClass", mainActivityClass),
            Pair("disableAutoRefresh", disableAutoRefresh),
            Pair("enableSessionPerTenant", enableSessionPerTenant),
            Pair("sentryMaxQueueSize", sentryMaxQueueSize),
            Pair("fronteggOrganization", fronteggOrganization),
        )
    }
}