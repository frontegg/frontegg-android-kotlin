package com.frontegg.android.services

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import com.frontegg.android.AuthenticationActivity
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.FronteggApp
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.Constants
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.utils.ObservableValue
import com.frontegg.android.utils.calculateTimerOffset
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
@SuppressLint("CheckResult")
class FronteggAuthService(
    val credentialManager: CredentialManager,
    appLifecycle: FronteggAppLifecycle,
    val refreshTokenManager: FronteggRefreshTokenTimer
) : FronteggAuth {

    companion object {
        private val TAG = FronteggAuth::class.java.simpleName
        val instance: FronteggAuthService
            get() {
                return FronteggApp.getInstance().auth as FronteggAuthService
            }

    }

    override var accessToken: ObservableValue<String?> = ObservableValue(null)
    override var refreshToken: ObservableValue<String?> = ObservableValue(null)
    override val user: ObservableValue<User?> = ObservableValue(null)
    override val isAuthenticated: ObservableValue<Boolean> = ObservableValue(false)
    override val isLoading: ObservableValue<Boolean> = ObservableValue(true)
    override val initializing: ObservableValue<Boolean> = ObservableValue(true)
    override val showLoader: ObservableValue<Boolean> = ObservableValue(true)
    override val refreshingToken: ObservableValue<Boolean> = ObservableValue(false)

    private val api = Api(credentialManager)
    private val storage = FronteggInnerStorage()
    private var pendingAppLink: String? = null
    override val isMultiRegion: Boolean = regions.isNotEmpty()

    private val baseUrl: String
        get() = storage.baseUrl
    private val clientId: String
        get() = storage.clientId
    private val applicationId: String?
        get() = storage.applicationId
    private val regions: List<RegionConfig>
        get() = storage.regions
    override val selectedRegion: RegionConfig?
        get() = storage.selectedRegion
    private val isEmbeddedMode: Boolean
        get() = storage.isEmbeddedMode


    init {

        if (!isMultiRegion || selectedRegion !== null) {
            this.initializeSubscriptions()
        }

        appLifecycle.startApp.addCallback {
            refreshTokenWhenNeeded()
        }

        appLifecycle.stopApp.addCallback {
            refreshTokenWhenNeeded()
        }

        refreshTokenManager.refreshTokenIfNeeded.addCallback {
            refreshTokenIfNeeded()
        }
    }


    override fun login(activity: Activity, loginHint: String?) {
        if (isEmbeddedMode) {
            EmbeddedAuthActivity.authenticate(activity, loginHint)
        } else {
            AuthenticationActivity.authenticate(activity, loginHint)
        }
    }

    override fun logout(callback: () -> Unit) {
        isLoading.value = true
        refreshTokenManager.cancelLastTimer()

        GlobalScope.launch(Dispatchers.IO) {

            val logoutCookies = getDomainCookie(baseUrl)
            val logoutAccessToken = accessToken.value

            if (logoutCookies != null &&
                logoutAccessToken != null &&
                isEmbeddedMode
            ) {
                api.logout(logoutCookies, logoutAccessToken)
            }

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


    override fun directLoginAction(
        activity: Activity,
        type: String,
        data: String,
        callback: (() -> Unit)?
    ) {
        if (isEmbeddedMode) {
            EmbeddedAuthActivity.directLoginAction(activity, type, data, callback)
        } else {
            Log.w(TAG, "Direct login action is not supported in non-embedded mode")
        }
    }


    override fun switchTenant(tenantId: String, callback: (Boolean) -> Unit) {
        Log.d(TAG, "switchTenant()")
        GlobalScope.launch(Dispatchers.IO) {
            val handler = Handler(Looper.getMainLooper())

            isLoading.value = true
            try {
                api.switchTenant(tenantId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send switch tenant request", e)
                handler.post {
                    isLoading.value = false
                    callback(false)
                }
                return@launch
            }

            val success = refreshTokenIfNeeded()

            handler.post {
                isLoading.value = false
                callback(success)
            }
        }
    }

    override fun refreshTokenIfNeeded(): Boolean {

        Log.d(TAG, "refreshTokenIfNeeded()")

        return try {
            this.sendRefreshToken()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send refresh token request", e)
            false
        }
    }


    fun reinitWithRegion() {
        this.initializeSubscriptions()
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

            // Cancel previous job if it exists
            refreshTokenManager.cancelLastTimer()


            if (decoded.exp > 0) {
                val offset = decoded.exp.calculateTimerOffset()
                Log.d(TAG, "setCredentials, schedule for $offset")

                refreshTokenManager.scheduleTimer(offset)
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

    fun handleHostedLoginCallback(
        code: String,
        webView: WebView? = null,
        activity: Activity? = null,
        callback: (() -> Unit)? = null,
    ): Boolean {

        val codeVerifier = credentialManager.getCodeVerifier()
        val redirectUrl = Constants.oauthCallbackUrl(baseUrl)

        if (codeVerifier == null) {
            return false
        }

        GlobalScope.launch(Dispatchers.IO) {
            val data = api.exchangeToken(code, redirectUrl, codeVerifier)
            if (data != null) {
                setCredentials(data.access_token, data.refresh_token)
                callback?.invoke()
            } else {
                Log.e(TAG, "Failed to exchange token")
                if (webView != null) {
                    val authorizeUrl = AuthorizeUrlGenerator()
                    val url = authorizeUrl.generate()
                    Handler(Looper.getMainLooper()).post {
                        webView.loadUrl(url.first)
                    }
                } else if (activity != null && callback == null) {
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


    private fun initializeSubscriptions() {
        Log.d(TAG, "initializeSubscriptions")
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
                    initializing.value = false
                    isLoading.value = false
                }

            } else {
                initializing.value = false
                isLoading.value = false
            }
        }
    }

    fun sendRefreshToken(): Boolean {
        val refreshToken = this.refreshToken.value ?: return false
        this.refreshingToken.value = true
        try {

            val data = api.refreshToken(refreshToken)
            return if (data != null) {
                setCredentials(data.access_token, data.refresh_token)
                true
            } else {
                Log.e(TAG, "Failed to refresh token, data = null")
                false
            }
        } finally {
            this.refreshingToken.value = false
        }
    }

    private fun refreshTokenWhenNeeded() {
        val accessToken = this.accessToken.value

        if (this.refreshToken.value == null) {
            return
        }


        refreshTokenManager.cancelLastTimer()

        if (accessToken == null) {
            // when we have valid refreshToken without accessToken => failed to refresh in background
            GlobalScope.launch(Dispatchers.IO) {
                sendRefreshToken()
            }
            return;
        }

        val decoded = JWTHelper.decode(accessToken)
        if (decoded.exp > 0) {
            val offset = decoded.exp.calculateTimerOffset()
            if (offset <= 0) {
                Log.d(TAG, "Refreshing Token...")
                GlobalScope.launch(Dispatchers.IO) {
                    sendRefreshToken()
                }
            } else {
                Log.d(TAG, "Schedule Refreshing Token for $offset")
                refreshTokenManager.scheduleTimer(offset)
            }
        }
    }
}