package com.frontegg.android.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.frontegg.android.exceptions.KeyNotFoundException
import com.frontegg.android.utils.CredentialKeys

open class CredentialManager(context: Context) {
    companion object {
        private const val SHARED_PREFERENCES_NAME: String =
            "com.frontegg.services.CredentialManager"
        private val TAG = CredentialManager::class.java.simpleName
    }

    private val sp: SharedPreferences;

    init {
        sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save value for key into the shared preference
     */
    fun save(key: CredentialKeys, value: String): Boolean {
        Log.d(TAG, "Saving Frontegg $key in shared preference")
        return sp.edit().putString(key.toString(), value).commit()
    }


    /**
     * Get value by key from the shared preference
     */
    fun get(key: CredentialKeys): String? {
        Log.d(TAG, "get Frontegg $key in shared preference ")
        return sp.getString(key.toString(), null)
    }


    /**
     * Remove all keys from shared preferences
     */
    @SuppressLint("ApplySharedPref")
    fun clear() {
        Log.d(TAG, "clear Frontegg shared preference ")

        val selectedRegion: String? = getSelectedRegion()
        sp.edit().clear().commit()
        if (selectedRegion != null) {
            sp.edit().putString(CredentialKeys.SELECTED_REGION.toString(), selectedRegion).commit()
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