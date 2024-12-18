package com.frontegg.android.testUtils

import FakeKeyGenerator
import java.security.Provider
import java.security.Security


/*
    Fake Android Key Store Provider according to
    https://medium.com/@wujingwe/write-unit-test-which-has-androidkeystore-dependency-f12181ae6311
 */
internal class FakeAndroidKeyStoreProvider : Provider(
    "AndroidKeyStore",
    1.0,
    "Fake AndroidKeyStore provider"
) {

    init {
        put(
            "KeyStore.AndroidKeyStore",
            FakeKeyStore::class.java.name
        )
        put(
            "KeyGenerator.AES",
            FakeKeyGenerator::class.java.name
        )
    }

    companion object {
        fun setup() {
            Security.addProvider(FakeAndroidKeyStoreProvider())
        }
    }
}