package com.frontegg.demo.e2e

import java.lang.reflect.Proxy

/**
 * Stands in for Play Integrity, which the CI emulator cannot run without the Play Store.
 * The SDK's integrity types are internal to the library module, so the fake is installed by reflection.
 */
internal object FakeDeviceIntegrity {
    private const val INTEGRITY_PACKAGE = "com.frontegg.android.integrity"
    private const val TOKEN_PREFIX = "e2e-integrity-token."

    /** The token the fake issues for a request body: bound to its hash, as Play Integrity tokens are. */
    fun tokenFor(requestBody: String): String = TOKEN_PREFIX + DPoPProof.sha256Base64Url(requestBody)

    /** Replaces the attestor the SDK configured; call after [EmbeddedE2ETestCase.launchApp]. */
    fun install() {
        val tokenProvider = proxy("$INTEGRITY_PACKAGE.IntegrityTokenProvider") { methodName, args ->
            if (methodName == "requestToken") TOKEN_PREFIX + args[0] else null
        }
        val factoryType = Class.forName("$INTEGRITY_PACKAGE.IntegrityProviderFactory")
        val providerFactory = proxy(factoryType.name) { methodName, _ ->
            if (methodName == "prepare") tokenProvider else null
        }
        val runInline: (() -> Unit) -> Unit = { task -> task() }
        val attestorType = Class.forName("$INTEGRITY_PACKAGE.DeviceIntegrityAttestor")
        val attestor = attestorType.getConstructor(factoryType, Function1::class.java)
            .newInstance(providerFactory, runInline)
        attestorType.getMethod("warmUp").invoke(attestor)
        Class.forName("$INTEGRITY_PACKAGE.DeviceIntegrity").getDeclaredField("attestor")
            .apply { isAccessible = true }
            .set(null, attestor)
    }

    private fun proxy(interfaceName: String, answer: (String, Array<out Any?>) -> Any?): Any {
        val type = Class.forName(interfaceName)
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Fake ${type.simpleName}"
                else -> answer(method.name, args ?: emptyArray())
            }
        }
    }
}
