package com.frontegg.android

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.LayoutRes
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.exceptions.FronteggException.Companion.FRONTEGG_APP_MUST_BE_INITIALIZED
import com.frontegg.android.exceptions.FronteggException.Companion.FRONTEGG_DOMAIN_MUST_START_WITH_HTTPS
import com.frontegg.android.services.*

class FronteggApp private constructor(
    val context: Context,
    val baseUrl: String,
    val clientId: String,
    loaderId: Int?
) {

    val auth: FronteggAuth
    val api: Api
    val credentialManager: CredentialManager

    @LayoutRes
    var loaderId: Int

    init {
        this.loaderId = loaderId ?: R.layout.default_frontegg_loader

        credentialManager = CredentialManager(context)
        api = Api(baseUrl, clientId, credentialManager)
        auth = FronteggAuth(baseUrl, clientId, api, credentialManager)
    }

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
            loaderId: Int?
        ) {
            val baseUrl: String = if (fronteggDomain.startsWith("http")) {
                fronteggDomain
            } else {
                throw FronteggException(FRONTEGG_DOMAIN_MUST_START_WITH_HTTPS)
            }

            instance = FronteggApp(context, baseUrl, clientId, loaderId)
        }
    }

}