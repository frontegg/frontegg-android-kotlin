package com.frontegg.android.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle

object FlutterInitHelper {
    fun initializeFlutterIfPresent(context: Context) {
        try {
            // 1) Reflectively load & init FlutterLoader once
            val loaderClass = Class.forName("io.flutter.embedding.engine.loader.FlutterLoader")
            val getInstance = loaderClass.getConstructor()
            val loader = getInstance.newInstance()
            val isInitialized = loaderClass
                .getMethod("initialized")
                .invoke(loader) as Boolean

            if (!isInitialized) {
                loaderClass
                    .getMethod("startInitialization", Context::class.java)
                    .invoke(loader, context)
                loaderClass
                    .getMethod(
                        "ensureInitializationComplete",
                        Context::class.java,
                        Array<String>::class.java
                    )
                    .invoke(loader, context, null)
            }

            // 2) Reflectively construct FlutterEngine(context, dartEntrypointArgs=null, autoRegister=false)
            val engineClass = Class.forName("io.flutter.embedding.engine.FlutterEngine")
            val constructor = engineClass.getConstructor(
                Context::class.java,
                Array<String>::class.java,
                Boolean::class.javaPrimitiveType!!
            )
            val engine = constructor.newInstance(context, null, false)

            // 3) Reflectively instantiate & register only your Frontegg plugin
            val pluginClass = Class.forName("com.frontegg.flutter.FronteggFlutterPlugin")
            val pluginInstance = pluginClass.getDeclaredConstructor().newInstance()

            val getPlugins = engineClass.getMethod("getPlugins")
            val registry = getPlugins.invoke(engine)
            // PluginRegistry.add(FlutterPlugin) — adds and calls onAttachedToEngine(…)
            registry.javaClass
                .getMethod(
                    "add",
                    Class.forName("io.flutter.embedding.engine.plugins.FlutterPlugin")
                )
                .invoke(registry, pluginInstance)


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