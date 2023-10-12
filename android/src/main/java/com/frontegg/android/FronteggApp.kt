package com.frontegg.android

import android.annotation.SuppressLint
import android.content.Context
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.exceptions.FronteggException.Companion.FRONTEGG_APP_MUST_BE_INITIALIZED
import com.frontegg.android.exceptions.FronteggException.Companion.FRONTEGG_DOMAIN_MUST_NOT_START_WITH_HTTPS
import com.frontegg.android.services.*

class FronteggApp private constructor(
    val context: Context,
    val baseUrl: String,
    val clientId: String,
    val isEmbeddedMode:Boolean = true
) {

    val credentialManager: CredentialManager = CredentialManager(context)
    val api: Api = Api(baseUrl, clientId, credentialManager)
    val auth: FronteggAuth = FronteggAuth(baseUrl, clientId, api, credentialManager)
    val packageName: String = context.packageName

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: FronteggApp? = null

        public fun getInstance(): FronteggApp {
            if (instance == null) {
                throw FronteggException(FRONTEGG_APP_MUST_BE_INITIALIZED)
            }
            return instance!!
        }

        public fun init(
            fronteggDomain: String,
            clientId: String,
            context: Context,
            isEmbeddedMode:Boolean = false,
        ) {
            val baseUrl: String = if (fronteggDomain.startsWith("https")) {
                throw FronteggException(FRONTEGG_DOMAIN_MUST_NOT_START_WITH_HTTPS)
            } else {
                "https://$fronteggDomain"
            }

            instance = FronteggApp(context, baseUrl, clientId, isEmbeddedMode)
        }
    }

}