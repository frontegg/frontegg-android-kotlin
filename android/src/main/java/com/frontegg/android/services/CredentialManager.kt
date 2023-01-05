package com.frontegg.android.services

import com.frontegg.android.utils.CredentialKeys

class CredentialManager {

    fun get(key: CredentialKeys): String? {

        return key.toString()

    }
}