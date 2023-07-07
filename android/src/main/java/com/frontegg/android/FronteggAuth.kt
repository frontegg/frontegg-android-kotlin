package com.frontegg.android

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import com.frontegg.android.models.User
import com.frontegg.android.services.Api
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.utils.Constants
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.utils.ObservableValue
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.*
import kotlin.concurrent.schedule


@SuppressLint("CheckResult")
@OptIn(DelicateCoroutinesApi::class)
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
    var pendingAppLink: String? = null
    var timer: Timer = Timer()
    var refreshTaskRunner: TimerTask? = null

    init {

        Observable.merge(
            isLoading.observable,
            isAuthenticated.observable,
            initializing.observable,
        ).subscribe {
            showLoader.value = initializing.value || (!isAuthenticated.value && isLoading.value)
        }

        GlobalScope.launch(Dispatchers.IO) {

            val accessTokenSaved = credentialManager.getOrNull(CredentialKeys.ACCESS_TOKEN)
            val refreshTokenSaved = credentialManager.getOrNull(CredentialKeys.REFRESH_TOKEN)

            if (accessTokenSaved != null && refreshTokenSaved != null) {
                accessToken.value = accessTokenSaved
                refreshToken.value = refreshTokenSaved

                if (!refreshTokenIfNeeded()) {
                    accessToken.value = null
                    refreshToken.value = null
                    isLoading.value = false
                    initializing.value = false
                }
            } else {
                isLoading.value = false
                initializing.value = false
            }
        }
    }

    private fun refreshTokenIfNeeded(): Boolean {
        val refreshToken = this.refreshToken.value ?: return false
        Log.d(TAG, "refreshTokenIfNeeded()")
        val data = api.refreshToken(refreshToken)
        return if (data != null) {
            setCredentials(data.access_token, data.refresh_token)
            true
        } else {
            false
        }
    }

    private fun setCredentials(accessToken: String, refreshToken: String) {

        if (credentialManager.save(CredentialKeys.REFRESH_TOKEN, refreshToken)
            && credentialManager.save(CredentialKeys.ACCESS_TOKEN, accessToken)
        ) {

            val decoded = JWTHelper.decode(accessToken)
            val user = api.me(accessToken)

            this.refreshToken.value = refreshToken
            this.accessToken.value = accessToken
            this.user.value = user
            this.isAuthenticated.value = true
            this.pendingAppLink = null

            refreshTaskRunner?.cancel()

            if (decoded.exp > 0) {
                val now: Long = Instant.now().toEpochMilli()
                val offset = (((decoded.exp * 1000) - now) * 0.80).toLong()
                Log.d(TAG, "setCredentials, schedule for $offset")
                refreshTaskRunner = timer.schedule(offset) {
                    refreshTokenIfNeeded()
                }
            }
        } else {
            this.refreshToken.value = null
            this.accessToken.value = null
            this.user.value = null
            this.isAuthenticated.value = false
        }

        this.isLoading.value = false
        this.initializing.value = false
    }

    fun handleHostedLoginCallback(code: String): Boolean {

        val codeVerifier = credentialManager.get(CredentialKeys.CODE_VERIFIER)
        val redirectUrl = Constants.oauthCallbackUrl(baseUrl)

        GlobalScope.launch(Dispatchers.IO) {
            val data = api.exchangeToken(code, redirectUrl, codeVerifier)
            if (data != null) {
                setCredentials(data.access_token, data.refresh_token)
            } else {
                // TODO: handle error
            }
        }

        return true
    }

    fun logout(callback: () -> Unit = {}) {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            GlobalScope.launch(Dispatchers.IO) {
                refreshTaskRunner?.cancel()
                isLoading.value = true
                isAuthenticated.value = false
                accessToken.value = null
                refreshToken.value = null
                user.value = null
                api.logout()
                credentialManager.clear()
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    callback()
                }
            }
        }

    }
}
