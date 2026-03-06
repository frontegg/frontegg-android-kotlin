package com.frontegg.android.utils

import com.frontegg.android.services.StorageProvider

object AppIdHeaderHelper {
    fun getHeaders(): Map<String, String> {
        val storage = StorageProvider.getInnerStorage()
        return storage.applicationId?.takeIf { it.isNotBlank() }?.let {
            mapOf("frontegg-requested-application-id" to it)
        } ?: emptyMap()
    }
}
