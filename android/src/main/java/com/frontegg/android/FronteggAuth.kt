package com.frontegg.android

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.Api
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.utils.AuthorizeUrlGenerator
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
    var baseUrl: String,
    var clientId: String,

    val credentialManager: CredentialManager,
    val regions: List<RegionConfig>,
    var selectedRegion: RegionConfig?
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
    val isMultiRegion: Boolean = regions.isNotEmpty()

    private var _api: Api? = null

    init {

        if (!isMultiRegion || selectedRegion !== null) {
            this.initializeSubscriptions()
        }
    }

    val api: Api
        get() = (if (this._api == null) {
            this._api = Api(this.baseUrl, this.clientId, credentialManager)
            this._api
        } else {
            this._api
        })!!


    fun reinitWithRegion(region: RegionConfig) {
        selectedRegion = region

        this.baseUrl = region.baseUrl
        this.clientId = region.clientId

        this.initializeSubscriptions()
    }


    fun initializeSubscriptions() {
        Observable.merge(
            isLoading.observable,
            isAuthenticated.observable,
            initializing.observable,
        ).subscribe {
            showLoader.value = initializing.value || (!isAuthenticated.value && isLoading.value)
        }

        GlobalScope.launch(Dispatchers.IO) {

            val accessTokenSaved = credentialManager.get(CredentialKeys.ACCESS_TOKEN)
            val refreshTokenSaved = credentialManager.get(CredentialKeys.REFRESH_TOKEN)

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

    fun refreshTokenIfNeeded(): Boolean {
        val refreshToken = this.refreshToken.value ?: return false
        Log.d(TAG, "refreshTokenIfNeeded()")

        return try {
            val data = api.refreshToken(refreshToken)
            if (data != null) {
                setCredentials(data.access_token, data.refresh_token)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send refresh token request", e)
            false;
        }
    }

    private fun setCredentials(accessToken: String, refreshToken: String) {

        if (credentialManager.save(CredentialKeys.REFRESH_TOKEN, refreshToken)
            && credentialManager.save(CredentialKeys.ACCESS_TOKEN, accessToken)
        ) {

            val decoded = JWTHelper.decode(accessToken)
            val user = api.me()

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

    fun handleHostedLoginCallback(code: String, webView: WebView? = null, activity: Activity? = null): Boolean {

        val codeVerifier = credentialManager.getCodeVerifier()
        val redirectUrl = Constants.oauthCallbackUrl(baseUrl)

        if (codeVerifier == null) {
            return false
        }

        GlobalScope.launch(Dispatchers.IO) {
            val data = api.exchangeToken(code, redirectUrl, codeVerifier)
            if (data != null) {
                setCredentials(data.access_token, data.refresh_token)
            } else {
                Log.e(TAG, "Failed to exchange token" )
                if(webView != null){
                    val authorizeUrl = AuthorizeUrlGenerator()
                    val url = authorizeUrl.generate()
                    Handler(Looper.getMainLooper()).post {
                        webView.loadUrl(url.first)
                    }
                } else if (activity != null){
                    login(activity)
                }

            }
        }

        return true
    }

    private fun getDomainCookie(siteName: String): String? {
        val cookieManager = CookieManager.getInstance()
        return cookieManager.getCookie(siteName);
    }


    fun logout(callback: () -> Unit = {}) {
        isLoading.value = true
        refreshTaskRunner?.cancel()
        GlobalScope.launch(Dispatchers.IO) {

            val logoutCookies = getDomainCookie(baseUrl)
            val logoutAccessToken = accessToken.value

            if (logoutCookies != null &&
                logoutAccessToken != null &&
                FronteggApp.getInstance().isEmbeddedMode
            ) {
                api.logout(logoutCookies, logoutAccessToken)
            }

            isLoading.value = true
            isAuthenticated.value = false
            accessToken.value = null
            refreshToken.value = null
            user.value = null
            credentialManager.clear()

            val handler = Handler(Looper.getMainLooper())
            handler.post {
                isLoading.value = false
                callback()
            }

        }

    }

    fun login(activity: Activity) {
        if (FronteggApp.getInstance().isEmbeddedMode) {
            EmbeddedAuthActivity.authenticate(activity)
        } else {
            AuthenticationActivity.authenticate(activity)
        }
    }


    fun switchTenant(tenantId: String, callback: () -> Unit = {}) {
        GlobalScope.launch(Dispatchers.IO) {

            isLoading.value = true
            api.switchTenant(tenantId)
            refreshTokenIfNeeded()

            val handler = Handler(Looper.getMainLooper())
            handler.post {
                isLoading.value = false
                callback()
            }
        }
    }
}
