package com.frontegg.android

import android.util.Log
import com.frontegg.android.models.User
import com.frontegg.android.services.Api
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.JWTHelper
import com.google.gson.JsonObject
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class NullableObject<K>(var value: K)

class ObservableValue<T>(value: T) {

    val observable: PublishSubject<NullableObject<T>> = PublishSubject.create()

    private var nullableObject: NullableObject<T>
    var value: T
        set(newValue) {
            nullableObject.value = newValue
            observable.onNext(nullableObject)
        }
        get() {
            return nullableObject.value
        }

    init {
        this.nullableObject = NullableObject(value)
    }

    fun subscribe(onNext: Consumer<NullableObject<T>>): Disposable {
        return observable.subscribe(onNext)
    }
}

class FronteggAuth(
    val baseUrl: String,
    val clientId: String,
    val api: Api,
    val credentialManager: CredentialManager
) {

    companion object {
        private val TAG = FronteggAuth::class.java.simpleName

        val instance: FronteggAuth
            get() {
                return FronteggApp.getInstance().auth
            }

    }

    var accessToken: ObservableValue<String?> = ObservableValue(null)
    var refreshToken: ObservableValue<String?> = ObservableValue(null)
    val user: ObservableValue<User?> = ObservableValue(null)
    val isAuthenticated: ObservableValue<Boolean> = ObservableValue(false)
    val isLoading: ObservableValue<Boolean> = ObservableValue(true)
    val initializing: ObservableValue<Boolean> = ObservableValue(true)
    val showLoader: ObservableValue<Boolean> = ObservableValue(true)
    val externalLink: ObservableValue<Boolean> = ObservableValue(false);
    var pendingAppLink: String? = null

    init {

        Observable.merge(
            isLoading.observable,
            isAuthenticated.observable,
            initializing.observable,
        ).subscribe {
            showLoader.value = initializing.value || (!isAuthenticated.value && isLoading.value)
            Log.d(TAG, "showLoader subscription, ")
        }


        val accessToken = credentialManager.getOrNull(CredentialKeys.ACCESS_TOKEN)
        val refreshToken = credentialManager.getOrNull(CredentialKeys.REFRESH_TOKEN)

        if (accessToken != null && refreshToken != null) {
            this.accessToken.value = accessToken
            this.refreshToken.value = refreshToken
            this.isLoading.value = false

            GlobalScope.launch(Dispatchers.IO) {
                refreshTokenIfNeeded()
            }
        } else {
            this.isLoading.value = false
            this.initializing.value = true
        }
    }

    private fun refreshTokenIfNeeded() {
        val refreshToken = this.refreshToken.value ?: return


        try {

            val data = api.refreshToken(refreshToken)

            if (data != null) {
                setCredentials(data.access_token, data.refresh_token)
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, e.message, e)
        }
    }

    fun setCredentials(accessToken: String, refreshToken: String) {

        if (credentialManager.save(CredentialKeys.REFRESH_TOKEN, refreshToken)
            && credentialManager.save(CredentialKeys.ACCESS_TOKEN, accessToken)
        ) {

            val decoded = JWTHelper.decode(accessToken)
            var user = api.me()

            this.refreshToken.value = refreshToken
            this.accessToken.value = accessToken
            this.user.value = user
            this.isAuthenticated.value = true
            this.pendingAppLink = null

//            let offset = Double ((decode["exp"] as ! Int) - Int(Date().timeIntervalSince1970)) * 0.9
//            DispatchQueue.global(qos:.utility).asyncAfter(deadline: .now()+offset) {
//                Task {
//                    await self . refreshTokenIfNeeded ()
//                }
        } else {
            this.refreshToken.value = null
            this.accessToken.value = null
            this.user.value = null
            this.isAuthenticated.value = false
        }

        this.isLoading.value = false
        this.initializing.value = false
    }

    fun handleHostedLoginCallback(webView: FronteggWebView, code: String): Boolean {

        val codeVerifier = credentialManager.get(CredentialKeys.CODE_VERIFIER)
        val redirectUrl = Constants.OauthCallbackUrl(baseUrl)

        GlobalScope.launch(Dispatchers.IO) {
            val data = api.exchangeToken(code, redirectUrl, codeVerifier)
            if (data != null) {
                setCredentials(data.access_token, data.refresh_token)
            } else {
                launch(Dispatchers.Main) {
                    webView.loadOauthAuthorize()
                }
            }
        }

        return true

    }
}
