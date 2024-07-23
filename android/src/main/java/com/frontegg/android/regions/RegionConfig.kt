package com.frontegg.android.regions

class RegionConfig(
    val key: String,
    baseUrl: String,
    val clientId: String,
    val applicationId: String?
) {
    val baseUrl: String

    init {

        this.baseUrl = if (baseUrl.startsWith("http")) {
            baseUrl
        } else {
            "https://${baseUrl}"
        }
    }
}