package com.frontegg.android.dpop

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.UnrecoverableKeyException
import java.security.spec.ECGenParameterSpec

internal interface DPoPKeyProvider {
    @Throws(GeneralSecurityException::class, IOException::class)
    fun getOrCreateKeyPair(): KeyPair

    @Throws(GeneralSecurityException::class, IOException::class)
    fun deleteKeyPair()
}

/** Non-exportable P-256 DPoP key held in the Android Keystore. */
internal class AndroidKeyStoreDPoPKeyProvider(
    private val alias: String = DEFAULT_ALIAS,
) : DPoPKeyProvider {

    @Synchronized
    @Throws(GeneralSecurityException::class, IOException::class)
    override fun getOrCreateKeyPair(): KeyPair {
        val keyStore = loadKeyStore()
        storedKeyPair(keyStore)?.let { return it }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec(P256_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKeyPair()
    }

    // An entry that can no longer be read is replaced, so a corrupted key does not disable DPoP for good.
    @Throws(GeneralSecurityException::class)
    private fun storedKeyPair(keyStore: KeyStore): KeyPair? {
        return try {
            val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            val publicKey = keyStore.getCertificate(alias)?.publicKey
            if (privateKey != null && publicKey != null) KeyPair(publicKey, privateKey) else null
        } catch (e: UnrecoverableKeyException) {
            keyStore.deleteEntry(alias)
            null
        }
    }

    @Synchronized
    @Throws(GeneralSecurityException::class, IOException::class)
    override fun deleteKeyPair() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    companion object {
        const val DEFAULT_ALIAS = "frontegg_dpop_key"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val P256_CURVE = "secp256r1"
    }
}
