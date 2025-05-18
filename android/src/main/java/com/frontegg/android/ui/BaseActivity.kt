package com.frontegg.android.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle

object FlutterInitHelper {
    fun initializeFlutterIfPresent(context: Context) {
        try {
            // 1. Load the FlutterLoader class
            val loaderClass = Class.forName("io.flutter.embedding.engine.loader.FlutterLoader")

            // 2. Create a new instance via its no-arg constructor
            val ctor = loaderClass.getConstructor()
            val loaderInstance = ctor.newInstance()

            // 3. Check initialization state: loader.initialized()
            val isInitialized = loaderClass
                .getMethod("initialized")
                .invoke(loaderInstance) as Boolean

            if (!isInitialized) {
                // 4a. Call startInitialization(Context)
                val startInit = loaderClass
                    .getMethod("startInitialization", android.content.Context::class.java)
                startInit.invoke(loaderInstance, context)

                // 4b. Call ensureInitializationComplete(Context, Array<String>?)
                val ensureInit = loaderClass.getMethod(
                    "ensureInitializationComplete",
                    android.content.Context::class.java,
                    Array<String>::class.java
                )
                // pass `null` for the Dart entrypoint args
                ensureInit.invoke(loaderInstance, context, null)
            }
        } catch (e: ClassNotFoundException) {
            // FlutterLoader isn’t on the classpath → nothing to do
        } catch (e: ReflectiveOperationException) {
            // Some other reflection issue (method not found, invocation failed, etc.)
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