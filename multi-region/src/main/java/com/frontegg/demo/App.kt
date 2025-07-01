package com.frontegg.demo

import android.app.Application
import com.frontegg.android.FronteggApp
import com.frontegg.android.regions.RegionConfig
import java.io.File


class App : Application() {
    companion object {
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Frontegg SDK with multiple regional configurations
        // This allows the app to work with different authentication endpoints based on user's region
        FronteggApp.initWithRegions(
            listOf(
                RegionConfig(
                    "eu",              // Region identifier for Europe
                    "autheu.davidantoon.me",  // Base URL for EU authentication endpoint
                    "b6adfe4c-d695-4c04-b95f-3ec9fd0c6cca"  // Client ID for EU region
                ),
                RegionConfig(
                    "us",              // Region identifier for United States
                    "authus.davidantoon.me",  // Base URL for US authentication endpoint
                    "6903cab0-9809-4a2e-97dd-b8c0f966c813"  // Client ID for US region
                )
            ),
            this  // Application context reference
        )
    }

    /**
     * Clears all application data except native libraries.
     * Removes cache and internal storage files by traversing through
     * application's data directory and deleting all files and folders
     * except the 'lib' directory.
     */
    fun clearApplicationData() {
        val cacheDirectory = cacheDir
        val applicationDirectory = cacheDirectory.parent?.let { File(it) }
        if (applicationDirectory?.exists() == true) {
            val fileNames = applicationDirectory.list() ?: arrayOf<String>()
            for (fileName in fileNames) {
                if (fileName != "lib") {
                    deleteFile(File(applicationDirectory, fileName))
                }
            }
        }
    }

    private fun deleteFile(file: File?): Boolean {
        var deletedAll = true
        if (file != null) {
            if (file.isDirectory) {
                val children = file.list()
                if (children != null) {
                    for (i in children.indices) {
                        deletedAll = deleteFile(File(file, children[i])) && deletedAll
                    }
                }
            } else {
                deletedAll = file.delete()
            }
        }

        return deletedAll
    }
}