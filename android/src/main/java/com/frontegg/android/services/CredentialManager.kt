package com.frontegg.android.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.frontegg.android.utils.CredentialKeys

open class CredentialManager(context: Context) {
    companion object {
        private const val SHARED_PREFERENCES_NAME: String =
            "com.frontegg.services.CredentialManager"
        private val TAG = CredentialManager::class.java.simpleName
    }

    private val sp: SharedPreferences;


    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sp = EncryptedSharedPreferences.create(
            SHARED_PREFERENCES_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Save value for key into the shared preference
     */
    fun save(key: CredentialKeys, value: String): Boolean {
        Log.d(TAG, "Saving Frontegg $key in shared preference")

        with(sp.edit()) {
            putString(key.toString(), value)
            apply()
            return commit()
        }
    }


    /**
     * Get value by key from the shared preference
     */
    fun get(key: CredentialKeys): String? {
        Log.d(TAG, "get Frontegg $key in shared preference ")
        with(sp) {
            return getString(key.toString(), null)
        }
    }


    /**
     * Remove all keys from shared preferences
     */
    @SuppressLint("ApplySharedPref")
    fun clear() {
        Log.d(TAG, "clear Frontegg shared preference ")

        val selectedRegion: String? = getSelectedRegion()

        with(sp.edit()) {
            remove(CredentialKeys.CODE_VERIFIER.toString())
            remove(CredentialKeys.ACCESS_TOKEN.toString())
            remove(CredentialKeys.REFRESH_TOKEN.toString())
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