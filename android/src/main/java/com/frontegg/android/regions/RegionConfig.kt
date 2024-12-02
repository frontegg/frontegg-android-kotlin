package com.frontegg.android.regions

/**
 * Region Config.
 *
 * @property key is the key of the region;
 * @property baseUrl is the Frontegg base url of the region;
 * @property clientId is the Frontegg clientID of the region;
 * @property applicationId is the id of Frontegg application of the region;
 */
class RegionConfig(
    val key: String,
    baseUrl: String,
    val clientId: String,
    val applicationId: String? = null
) {
    val baseUrl: String = if (baseUrl.startsWith("http")) {
        baseUrl
    } else {
        "https://${baseUrl}"
    }
}