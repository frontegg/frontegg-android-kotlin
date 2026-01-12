package com.frontegg.android.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.frontegg.android.utils.CredentialKeys
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey


open class CredentialManager(val context: Context) {
    companion object {
        private const val SHARED_PREFERENCES_NAME: String =
            "com.frontegg.services.CredentialManager"
        private val TAG = CredentialManager::class.java.simpleName
    }

    private var sp: SharedPreferences;
    private var enableSessionPerTenant: Boolean = false

    private fun createKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        return keyStore
    }

    fun clearSharedPreference(context: Context) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear()
            .apply()
    }

    private fun createSecretKey(alias: String): SecretKey {
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")

        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )

        return keyGenerator.generateKey()
    }

    private fun getSecretKey(keyStore: KeyStore, alias: String): SecretKey {
        return if (keyStore.containsAlias(alias)) {
            (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            this.createSecretKey(alias)
        }
    }

    init {

        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            sp = EncryptedSharedPreferences.create(
                context,
                SHARED_PREFERENCES_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            val cause: Throwable = e.cause!!
            if (cause.message!!.contains("Signature/MAC verification failed")) {
                Log.w(TAG, "Master key is corrupted. Recreating the master key")
                // Recreate the Master Key
                val masterKey = reinitializeMasterKey()

                // Recreate EncryptedSharedPreferences
                sp = EncryptedSharedPreferences.create(
                    context,
                    SHARED_PREFERENCES_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } else {
                // Handle other exceptions
                throw e
            }
        }
    }

    private fun reinitializeMasterKey(): MasterKey {
        val keyStore = this.createKeyStore()
        getSecretKey(keyStore, SHARED_PREFERENCES_NAME)
        clearSharedPreference(this.context)
        return MasterKey.Builder(context)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            .build()
    }

    fun setEnableSessionPerTenant(enabled: Boolean) {
        this.enableSessionPerTenant = enabled
    }

    fun getCurrentTenantId(): String? {
        return sp.getString(CredentialKeys.CURRENT_TENANT_ID.toString(), null)
    }

    fun setCurrentTenantId(tenantId: String?): Boolean {
        if (tenantId == null) {
            with(sp.edit()) {
                remove(CredentialKeys.CURRENT_TENANT_ID.toString())
                apply()
                return commit()
            }
        } else {
            return save(CredentialKeys.CURRENT_TENANT_ID, tenantId)
        }
    }

    private fun getTenantScopedKey(key: CredentialKeys, tenantId: String?): String {
        if (!enableSessionPerTenant || tenantId == null) {
            return key.toString()
        }
        return "${key}_tenant_$tenantId"
    }

    fun save(key: CredentialKeys, value: String, tenantId: String? = null): Boolean {
        if (key == CredentialKeys.CURRENT_TENANT_ID || key == CredentialKeys.CODE_VERIFIER) {
            with(sp.edit()) {
                putString(key.toString(), value)
                apply()
                return commit()
            }
        }
        
        val effectiveTenantId = tenantId ?: getCurrentTenantId()
        val scopedKey = getTenantScopedKey(key, effectiveTenantId)

        with(sp.edit()) {
            putString(scopedKey, value)
            apply()
            return commit()
        }
    }


    fun get(key: CredentialKeys, tenantId: String? = null): String? {
        if (key == CredentialKeys.CURRENT_TENANT_ID || key == CredentialKeys.CODE_VERIFIER) {
            return sp.getString(key.toString(), null)
        }
        
        val effectiveTenantId = tenantId ?: getCurrentTenantId()
        val scopedKey = getTenantScopedKey(key, effectiveTenantId)
        with(sp) {
            return getString(scopedKey, null)
        }
    }


    @SuppressLint("ApplySharedPref")
    fun clear(tenantId: String? = null) {
        val selectedRegion: String? = getSelectedRegion()
        val effectiveTenantId = tenantId ?: getCurrentTenantId()

        with(sp.edit()) {
            if (enableSessionPerTenant && effectiveTenantId != null) {
                remove(getTenantScopedKey(CredentialKeys.ACCESS_TOKEN, effectiveTenantId))
                remove(getTenantScopedKey(CredentialKeys.REFRESH_TOKEN, effectiveTenantId))
            } else {
                remove(CredentialKeys.CODE_VERIFIER.toString())
                remove(CredentialKeys.ACCESS_TOKEN.toString())
                remove(CredentialKeys.REFRESH_TOKEN.toString())
                remove(CredentialKeys.CURRENT_TENANT_ID.toString())
            }
            if (selectedRegion != null) {
                putString(CredentialKeys.SELECTED_REGION.toString(), selectedRegion)
            }
            apply()
            commit()
        }
    }

    @SuppressLint("ApplySharedPref")
    fun clearAllTokens() {
        val selectedRegion: String? = getSelectedRegion()
        val accessTokenKey = CredentialKeys.ACCESS_TOKEN.toString()
        val refreshTokenKey = CredentialKeys.REFRESH_TOKEN.toString()
        
        with(sp.edit()) {
            val allKeys = sp.all.keys
            for (key in allKeys) {
                if (key == accessTokenKey || key == refreshTokenKey) {
                    remove(key)
                } else if (key.startsWith("${accessTokenKey}_tenant_") || 
                         key.startsWith("${refreshTokenKey}_tenant_")) {
                    remove(key)
                }
            }
            
            remove(CredentialKeys.CODE_VERIFIER.toString())
            remove(CredentialKeys.CURRENT_TENANT_ID.toString())
            
            if (selectedRegion != null) {
                putString(CredentialKeys.SELECTED_REGION.toString(), selectedRegion)
            }
            apply()
            commit()
        }
    }

    fun getCodeVerifier(): String? {
        return this.get(CredentialKeys.CODE_VERIFIER)
    }

    fun saveCodeVerifier(codeVerifier: String): Boolean {
        return this.save(CredentialKeys.CODE_VERIFIER, codeVerifier)
    }


    fun getSelectedRegion(): String? {
        return this.get(CredentialKeys.SELECTED_REGION)
    }

    fun saveSelectedRegion(selectedRegion: String): Boolean {
        return this.save(CredentialKeys.SELECTED_REGION, selectedRegion)
    }
}