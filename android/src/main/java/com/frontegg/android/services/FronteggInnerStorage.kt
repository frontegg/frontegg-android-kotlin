package com.frontegg.android.services

import com.frontegg.android.regions.RegionConfig

class FronteggInnerStorage {
    val baseUrl: String
        get() = data["baseUrl"] as String? ?: ""
    val clientId: String
        get() = data["clientId"] as String? ?: ""
    val applicationId: String?
        get() = data["applicationId"] as String?

    val isEmbeddedMode: Boolean
        get() = data["isEmbeddedMode"] as Boolean? ?: true
    val regions: List<RegionConfig>
        get() {
            if (data["regions"] is List<*>) {
                val regions = data["regions"] as List<*>
                return regions.filterIsInstance<RegionConfig>()
            }

            return listOf()
        }
    val selectedRegion: RegionConfig?
        get() = data["selectedRegion"] as RegionConfig?
    val handleLoginWithSocialLogin: Boolean
        get() = data["handleLoginWithSocialLogin"] as Boolean? ?: true
    val handleLoginWithSocialLoginProvider: Boolean
        get() = data["handleLoginWithSocialLoginProvider"] as Boolean? ?: true
    val handleLoginWithCustomSocialLoginProvider: Boolean
        get() = data["handleLoginWithCustomSocialLoginProvider"] as Boolean? ?: true
    val customUserAgent: String?
        get() = data["customUserAgent"] as String?
    val handleLoginWithSSO: Boolean
        get() = data["handleLoginWithSSO"] as Boolean? ?: false
    val shouldPromptSocialLoginConsent: Boolean
        get() = data["shouldPromptSocialLoginConsent"] as Boolean? ?: true
    val useAssetsLinks: Boolean
        get() = data["useAssetsLinks"] as Boolean? ?: false
    val useChromeCustomTabs: Boolean
        get() = data["useChromeCustomTabs"] as Boolean? ?: false
    val mainActivityClass: Class<*>?
        get() = data["mainActivityClass"] as Class<*>?
    val packageName: String
        get() = data["packageName"] as String? ?: ""
    val deepLinkScheme: String?
        get() = data["deepLinkScheme"] as String?
    val useDiskCacheWebview: Boolean
        get() = data["useDiskCacheWebview"] as Boolean? ?: false
    val suggestSavePassword: Boolean
        get() = data["suggestSavePassword"] as Boolean? ?: false


    fun fill(
        baseUrl: String,
        clientId: String,
        applicationId: String?,
        isEmbeddedMode: Boolean = true,
        regions: List<RegionConfig> = listOf(),
        selectedRegion: RegionConfig? = null,
        handleLoginWithSocialLogin: Boolean = true,
        handleLoginWithSocialLoginProvider: Boolean = true,
        handleLoginWithCustomSocialLoginProvider: Boolean = true,
        customUserAgent: String? = null,
        handleLoginWithSSO: Boolean = false,
        shouldPromptSocialLoginConsent: Boolean = true,
        useAssetsLinks: Boolean = false,
        useChromeCustomTabs: Boolean = false,
        mainActivityClass: Class<*>? = null,
        packageName: String,
        deepLinkScheme: String? = null,
        useDiskCacheWebview: Boolean = false,
        suggestSavePassword: Boolean = false
    ) {
        data["baseUrl"] = baseUrl
        data["clientId"] = clientId
        data["applicationId"] = applicationId
        data["isEmbeddedMode"] = isEmbeddedMode
        data["regions"] = regions
        data["selectedRegion"] = selectedRegion
        data["handleLoginWithSocialLogin"] = handleLoginWithSocialLogin
        data["handleLoginWithSocialLoginProvider"] = handleLoginWithSocialLoginProvider
        data["handleLoginWithCustomSocialLoginProvider"] = handleLoginWithCustomSocialLoginProvider
        data["customUserAgent"] = customUserAgent
        data["handleLoginWithSSO"] = handleLoginWithSSO
        data["shouldPromptSocialLoginConsent"] = shouldPromptSocialLoginConsent
        data["useAssetsLinks"] = useAssetsLinks
        data["useChromeCustomTabs"] = useChromeCustomTabs
        data["mainActivityClass"] = mainActivityClass
        data["packageName"] = packageName
        data["deepLinkScheme"] = deepLinkScheme
        data["useDiskCacheWebview"] = useDiskCacheWebview
        data["suggestSavePassword"] = suggestSavePassword
    }

    companion object {
        private val data = mutableMapOf<String, Any?>()
    }
}