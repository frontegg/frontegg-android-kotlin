package com.frontegg.android.services

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.credentials.PublicKeyCredential
import com.frontegg.android.AuthenticationActivity
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.FronteggApp
import com.frontegg.android.FronteggAuth
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.exceptions.MfaRequiredException
import com.frontegg.android.exceptions.WebAuthnAlreadyRegisteredInLocalDeviceException
import com.frontegg.android.exceptions.isWebAuthnRegisteredBeforeException
import com.frontegg.android.models.SocialProvider
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
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
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(DelicateCoroutinesApi::class)
@SuppressLint("CheckResult")
class FronteggAuthService(
    val credentialManager: CredentialManager,
    appLifecycle: FronteggAppLifecycle,
    val refreshTokenTimer: FronteggRefreshTokenTimer
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

    private val api = ApiProvider.getApi(credentialManager)
    private val storage = StorageProvider.getInnerStorage()

    override val isMultiRegion: Boolean
        get() = regions.isNotEmpty()
    override val baseUrl: String
        get() = storage.baseUrl
    override val clientId: String
        get() = storage.clientId
    override val applicationId: String?
        get() = storage.applicationId
    override val regions: List<RegionConfig>
        get() = storage.regions
    override val selectedRegion: RegionConfig?
        get() = storage.selectedRegion
    override val isEmbeddedMode: Boolean
        get() = storage.isEmbeddedMode
    override val useAssetsLinks: Boolean
        get() = storage.useAssetsLinks
    override val useChromeCustomTabs: Boolean
        get() = storage.useChromeCustomTabs
    override val mainActivityClass: Class<*>?
        get() = storage.mainActivityClass


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

        refreshTokenTimer.refreshTokenIfNeeded.addCallback {
            refreshTokenIfNeeded()
        }
    }


    override fun login(
        activity: Activity,
        loginHint: String?,
        callback: (() -> Unit)?,
    ) {
        if (isEmbeddedMode) {
            EmbeddedAuthActivity.authenticate(activity, loginHint, callback)
        } else {
            AuthenticationActivity.authenticate(activity, loginHint, callback)
        }
    }

    override fun logout(
        callback: () -> Unit,
    ) {
        isLoading.value = true
        refreshTokenTimer.cancelLastTimer()

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


    @Deprecated(
        "Use directLogin(url), socialLogin(provider), or customSocialLogin(id) instead.",
        replaceWith = ReplaceWith("directLogin(url) or socialLogin(provider) or customSocialLogin(id)")
    )
    override fun directLoginAction(
        activity: Activity,
        type: String,
        data: String,
        callback: (() -> Unit)?
    ) = loginAction(
        activity = activity,
        type = type,
        data = data,
        callback = callback,
    )

    override fun directLogin(
        activity: Activity,
        url: String,
        callback: (() -> Unit)?,
    ) = loginAction(
        activity = activity,
        type = "direct",
        data = url,
        callback = callback,
    )

    override fun socialLogin(
        activity: Activity,
        provider: SocialProvider,
        callback: (() -> Unit)?,
    ) = loginAction(
        activity = activity,
        type = "social-login",
        data = provider.type,
        callback = callback,
    )

    override fun customSocialLogin(
        activity: Activity,
        id: String,
        callback: (() -> Unit)?,
    ) = loginAction(
        activity = activity,
        type = "custom-social-login",
        data = id,
        callback = callback,
    )

    override fun switchTenant(
        tenantId: String,
        callback: (Boolean) -> Unit,
    ) {
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

    override fun loginWithPasskeys(
        activity: Activity,
        callback: ((error: Exception?) -> Unit)?,
    ) {

        val passkeyManager = CredentialManagerHandlerProvider.getCredentialManagerHandler(
            activity
        )
        val scope = ScopeProvider.mainScope
        scope.launch {
            try {

                val webAuthnPreloginRequest = withContext(Dispatchers.IO) { api.webAuthnPrelogin() }

                val result = passkeyManager.getPasskey(webAuthnPreloginRequest.jsonChallenge)

                val challengeResponse =
                    (result.credential as PublicKeyCredential).authenticationResponseJson

                isLoading.value = true
                val webAuthnPostLoginResponse = withContext(Dispatchers.IO) {
                    api.webAuthnPostlogin(webAuthnPreloginRequest.cookie, challengeResponse)
                }

                Log.i(TAG, "Login with Passkeys succeeded, exchanging oauth token")
                withContext(Dispatchers.IO) {
                    setCredentials(
                        webAuthnPostLoginResponse.access_token,
                        webAuthnPostLoginResponse.refresh_token
                    )
                }

                callback?.invoke(null)
            } catch (e: Exception) {
                Log.e(TAG, "failed to login with passkeys", e)
                if (e is MfaRequiredException) {
                    startMultiFactorAuthenticator(activity, callback, e.mfaRequestData)
                } else {
                    callback?.invoke(e)
                }
            }
        }
    }

    override fun registerPasskeys(
        activity: Activity,
        callback: ((error: Exception?) -> Unit)?,
    ) {

        val passkeyManager = CredentialManagerHandlerProvider.getCredentialManagerHandler(activity)
        val scope = ScopeProvider.mainScope
        scope.launch {
            try {

                val webAuthnRegsiterRequest = withContext(Dispatchers.IO) {
                    api.getWebAuthnRegisterChallenge()
                }

                val result = passkeyManager.createPasskey(webAuthnRegsiterRequest.jsonChallenge)

                val challengeResponse = result.registrationResponseJson

                withContext(Dispatchers.IO) {
                    api.verifyWebAuthnDevice(webAuthnRegsiterRequest.cookie, challengeResponse)
                }

                callback?.invoke(null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to login with passkeys", e)
                if (isWebAuthnRegisteredBeforeException(e)) {
                    callback?.invoke(WebAuthnAlreadyRegisteredInLocalDeviceException())
                } else {
                    callback?.invoke(e)
                }
            }
        }
    }

    override suspend fun requestAuthorizeAsync(
        refreshToken: String,
        deviceTokenCookie: String?
    ): User {
        isLoading.value = true
        try {
            Log.d(TAG, "Requesting silent authorization with refresh and device tokens")

            // Call API to authorize with tokens
            val authResponse = withContext(Dispatchers.IO) {
                api.authorizeWithTokens(refreshToken, deviceTokenCookie)
            }

            // Set credentials and return the user
            setCredentials(authResponse.access_token, authResponse.refresh_token)
            user.value?.let {
                return it
            }

            throw FailedToAuthenticateException(error = "Failed to authenticate")
        } catch (e: Exception) {
            Log.e(TAG, "Authorization request failed: ${e.message}", e)
            isLoading.value = false
            throw e
        }
    }


    override fun requestAuthorize(
        refreshToken: String,
        deviceTokenCookie: String?,
        callback: (Result<User>) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val user = requestAuthorizeAsync(refreshToken, deviceTokenCookie)
                withContext(Dispatchers.Main) {
                    callback(Result.success(user))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to authenticate: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    private fun loginAction(
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

    private fun startMultiFactorAuthenticator(
        activity: Activity,
        callback: ((error: Exception?) -> Unit)?,
        mfaRequestData: String
    ) {

        val authCallback: () -> Unit = {
            if (this.isAuthenticated.value) {
                callback?.invoke(null)
            } else {
                val error = FailedToAuthenticateException(error = "Failed to authenticate with MFA")
                callback?.invoke(error)
            }
        }

        val multiFactorStateBase64 =
            Base64.encodeToString(mfaRequestData.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val directLogin = mapOf(
            "type" to "direct",
            "data" to "$baseUrl/oauth/account/mfa-mobile-authenticator?state=$multiFactorStateBase64",
            "additionalQueryParams" to mapOf(
                "prompt" to "consent"
            )
        )
        val jsonData = JSONObject(directLogin).toString().toByteArray(Charsets.UTF_8)
        val loginDirectAction = Base64.encodeToString(jsonData, Base64.NO_WRAP)

        if (isEmbeddedMode) {
            EmbeddedAuthActivity.authenticateWithMultiFactor(
                activity,
                loginDirectAction,
                authCallback
            )
        } else {
            AuthenticationActivity.authenticateWithMultiFactor(
                activity,
                loginDirectAction,
                authCallback
            )
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

            // Cancel previous job if it exists
            refreshTokenTimer.cancelLastTimer()


            if (decoded.exp > 0) {
                val offset = decoded.exp.calculateTimerOffset()
                Log.d(TAG, "setCredentials, schedule for $offset")

                refreshTokenTimer.scheduleTimer(offset)
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
                    val authorizeUrl = AuthorizeUrlGeneratorProvider.getAuthorizeUrlGenerator()
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
        return cookieManager.getCookie(siteName)
    }


    @VisibleForTesting
    internal fun initializeSubscriptions() {
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

    @VisibleForTesting
    internal fun refreshTokenWhenNeeded() {
        val accessToken = this.accessToken.value

        if (this.refreshToken.value == null) {
            return
        }

        refreshTokenTimer.cancelLastTimer()

        if (accessToken == null) {
            // when we have valid refreshToken without accessToken => failed to refresh in background
            GlobalScope.launch(Dispatchers.IO) {
                sendRefreshToken()
            }
            return
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
                refreshTokenTimer.scheduleTimer(offset)
            }
        }
    }
}