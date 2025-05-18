package com.frontegg.android.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle

object FlutterInitHelper {
    fun initializeFlutterIfPresent(context: Context) {
        try {
            val loaderClass = Class.forName("io.flutter.embedding.engine.loader.FlutterLoader")
            val instanceMethod = loaderClass.getMethod("getInstance")
            val flutterLoader = instanceMethod.invoke(null)

            val initializedMethod = loaderClass.getMethod("initialized")
            val isInitialized = initializedMethod.invoke(flutterLoader) as Boolean
            if (!isInitialized) {
                val startInitMethod =
                    loaderClass.getMethod("startInitialization", Context::class.java)
                startInitMethod.invoke(flutterLoader, context.applicationContext)

                val ensureInitMethod = loaderClass.getMethod(
                    "ensureInitializationComplete",
                    Context::class.java,
                    Array<String>::class.java
                )
                ensureInitMethod.invoke(
                    flutterLoader,
                    context.applicationContext,
                    emptyArray<String>()
                )
            }
        } catch (_: ClassNotFoundException) {
            // Flutter not present – skip
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

open class FronteggBaseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FlutterInitHelper.initializeFlutterIfPresent(this)
    }
}