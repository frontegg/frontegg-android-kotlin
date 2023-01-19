package com.frontegg.android

import android.app.Activity
import android.content.Context
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
import java.util.Timer
import java.util.TimerTask


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
    val externalLink: ObservableValue<Boolean> = ObservableValue(false)
    var pendingAppLink: String? = null
    var timer: Timer = Timer()
    var taskRunner: TimerTask? = null

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

        val data = api.refreshToken(refreshToken)
        return if (data != null) {
            setCredentials(data.access_token, data.refresh_token)
            true
        } else {
            false
        }
    }

    fun setCredentials(accessToken: String, refreshToken: String) {

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

            if (taskRunner != null) {
                taskRunner?.cancel()
            }
//            taskRunner = timer.scheduleAtFixedRate(0, decoded.exp) {
//                refreshTokenIfNeeded()
//            }
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


    fun logout(context: Context, callback: () -> Unit) {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush();
            GlobalScope.launch(Dispatchers.IO) {
                taskRunner?.cancel()
                isLoading.value = true
                isAuthenticated.value = false
                accessToken.value = null
                refreshToken.value = null
                user.value = null
                api.logout()
                credentialManager.clear()
                (context as Activity).runOnUiThread {
                    callback()
                }
            }
        }

    }
}
