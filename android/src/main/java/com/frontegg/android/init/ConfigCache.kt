package com.frontegg.android.init

import android.content.Context
import android.content.SharedPreferences
import com.frontegg.android.regions.RegionConfig
import org.json.JSONArray
import org.json.JSONObject

internal object ConfigCache {
    private const val PREFS = "frontegg_init_cache"
    private const val KEY_REGIONS_JSON = "regions_json"
    private const val KEY_FLAGS_JSON = "regions_flags_json"

    data class RegionsInitFlags(
        val useAssetsLinks: Boolean,
        val useChromeCustomTabs: Boolean,
        val mainActivityClassName: String?,
        val useDiskCacheWebview: Boolean,
    )

    fun saveLastRegionsInit(
        context: Context,
        regions: List<RegionConfig>,
        flags: RegionsInitFlags
    ) {
        val prefs = prefs(context)
        val arr = JSONArray()
        regions.forEach { r ->
            val o = JSONObject()
                .put("key", r.key)
                .put("baseUrl", r.baseUrl)
                .put("clientId", r.clientId)
                .put("applicationId", r.applicationId)
            arr.put(o)
        }
        val flagsJson = JSONObject()
            .put("useAssetsLinks", flags.useAssetsLinks)
            .put("useChromeCustomTabs", flags.useChromeCustomTabs)
            .put("mainActivityClassName", flags.mainActivityClassName)
            .put("useDiskCacheWebview", flags.useDiskCacheWebview)

        prefs.edit()
            .putString(KEY_REGIONS_JSON, arr.toString())
            .putString(KEY_FLAGS_JSON, flagsJson.toString())
            .apply()
    }

    fun loadLastRegionsInit(context: Context): Pair<List<RegionConfig>, RegionsInitFlags>? {
        val prefs = prefs(context)
        val regionsStr = prefs.getString(KEY_REGIONS_JSON, null) ?: return null
        val flagsStr = prefs.getString(KEY_FLAGS_JSON, null) ?: return null
        return try {
            val arr = JSONArray(regionsStr)
            val regions = ArrayList<RegionConfig>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                regions.add(
                    RegionConfig(
                        key = o.getString("key"),
                        baseUrl = o.getString("baseUrl"),
                        clientId = o.getString("clientId"),
                        applicationId = if (o.has("applicationId") && !o.isNull("applicationId")) o.getString("applicationId") else null
                    )
                )
            }
            val fj = JSONObject(flagsStr)
            val flags = RegionsInitFlags(
                useAssetsLinks = fj.optBoolean("useAssetsLinks", false),
                useChromeCustomTabs = fj.optBoolean("useChromeCustomTabs", false),
                mainActivityClassName = fj.optString("mainActivityClassName", null).takeIf { !it.isNullOrEmpty() },
                useDiskCacheWebview = fj.optBoolean("useDiskCacheWebview", false),
            )
            regions to flags
        } catch (_: Throwable) {
            null
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}


