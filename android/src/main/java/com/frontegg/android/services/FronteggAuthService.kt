package com.frontegg.android.services

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
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
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.utils.Constants
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.utils.calculateTimerOffset
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.time.Duration

@OptIn(DelicateCoroutinesApi::class)
@SuppressLint("CheckResult")
class FronteggAuthService(
    val credentialManager: CredentialManager,
    appLifecycle: FronteggAppLifecycle,
    val refreshTokenTimer: FronteggRefreshTokenTimer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : FronteggAuth {
    // use a dedicated scope instead of GlobalScope
    private val bgScope = CoroutineScope(ioDispatcher + SupervisorJob())


    companion object {
        private val TAG = FronteggAuth::class.java.simpleName
        val instance: FronteggAuthService
            get() {
                return FronteggApp.getInstance().auth as FronteggAuthService
            }

    }

    override val accessToken = FronteggState.accessToken
    override val refreshToken = FronteggState.refreshToken
    override val user = FronteggState.user
    override val isAuthenticated = FronteggState.isAuthenticated
    override val isLoading = FronteggState.isLoading
    override val webLoading = FronteggState.webLoading
    override val initializing = FronteggState.initializing
    override val showLoader = FronteggState.showLoader
    override val refreshingToken = FronteggState.refreshingToken
    override val isStepUpAuthorization = FronteggState.isStepUpAuthorization

    private val api = ApiProvider.getApi(credentialManager)
    private val storage = StorageProvider.getInnerStorage()
    private val multiFactorAuthenticator =
        MultiFactorAuthenticatorProvider.getMultiFactorAuthenticator()
    private val stepUpAuthenticator =
        StepUpAuthenticatorProvider.getStepUpAuthenticator(credentialManager)

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
        callback: ((Exception?) -> Unit)?,
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

        bgScope.launch {

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

            withContext(mainDispatcher) {
                isLoading.value = false
                callback()
            }

        }
    }


    override fun directLoginAction(
        activity: Activity,
        type: String,
        data: String,
        callback: ((Exception?) -> Unit)?
    ) {
        if (isEmbeddedMode) {
            EmbeddedAuthActivity.directLoginAction(activity, type, data, callback)
        } else {
            Log.w(TAG, "Direct login action is not supported in non-embedded mode")
        }
    }


    override fun switchTenant(
        tenantId: String,
        callback: (Boolean) -> Unit,
    ) {
        Log.d(TAG, "switchTenant()")
        bgScope.launch {
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
        ScopeProvider.mainScope.launch {
            try {

                val webAuthnPreloginRequest = withContext(ioDispatcher) { api.webAuthnPrelogin() }

                val result = passkeyManager.getPasskey(webAuthnPreloginRequest.jsonChallenge)

                val challengeResponse =
                    (result.credential as PublicKeyCredential).authenticationResponseJson

                isLoading.value = true
                val webAuthnPostLoginResponse = withContext(ioDispatcher) {
                    api.webAuthnPostlogin(webAuthnPreloginRequest.cookie, challengeResponse)
                }

                Log.i(TAG, "Login with Passkeys succeeded, exchanging oauth token")
                withContext(ioDispatcher) {
                    setCredentials(
                        webAuthnPostLoginResponse.access_token,
                        webAuthnPostLoginResponse.refresh_token
                    )
                }

                callback?.invoke(null)
            } catch (e: MfaRequiredException) {
                Log.e(TAG, "failed to login with passkeys", e)
                multiFactorAuthenticator.start(activity, callback, e.mfaRequestData)
            } catch (e: Exception) {
                Log.e(TAG, "failed to login with passkeys", e)
                callback?.invoke(e)
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

                val webAuthnRegsiterRequest = withContext(ioDispatcher) {
                    api.getWebAuthnRegisterChallenge()
                }

                val result = passkeyManager.createPasskey(webAuthnRegsiterRequest.jsonChallenge)

                val challengeResponse = result.registrationResponseJson

                withContext(ioDispatcher) {
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
            val authResponse = withContext(ioDispatcher) {
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
        bgScope.launch {
            try {
                val user = requestAuthorizeAsync(refreshToken, deviceTokenCookie)
                withContext(mainDispatcher) {
                    callback(Result.success(user))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to authenticate: ${e.message}", e)
                withContext(mainDispatcher) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    //region Step Up functionality

    override fun isSteppedUp(maxAge: Duration?): Boolean {
        return stepUpAuthenticator.isSteppedUp(maxAge)
    }

    override fun stepUp(
        activity: Activity,
        maxAge: Duration?,
        callback: ((error: Exception?) -> Unit)?,
    ) {
        stepUpAuthenticator.stepUp(
            activity,
            maxAge,
            callback
        )
    }

//endregion

    fun reinitWithRegion() {
        this.initializeSubscriptions()
    }


    private fun setCredentials(accessToken: String, refreshToken: String) {

        if (credentialManager.save(CredentialKeys.REFRESH_TOKEN, refreshToken)
            && credentialManager.save(CredentialKeys.ACCESS_TOKEN, accessToken)
        ) {
            
            Log.d(TAG, "setCredentials, going to get user info")
    
            try {
                val user = api.me()
                if (user != null) {
                    updateStateWithCredentials(accessToken, refreshToken, user)
                } else {
                    Log.e(TAG, "Failed to fetch user info via api.me(), user is null")
                    clearCredentials()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch user info via api.me()", e)
                clearCredentials()
            }
        } else {
            clearCredentials()
        }
        this.isLoading.value = false
        this.initializing.value = false
    }

    private fun updateStateWithCredentials(accessToken: String, refreshToken: String, user: User) {
        this.refreshToken.value = refreshToken
        this.accessToken.value = accessToken
        this.user.value = user
        this.isAuthenticated.value = true

        // Cancel previous job if it exists
        refreshTokenTimer.cancelLastTimer()

        val decoded = JWTHelper.decode(accessToken)
        if (decoded.exp > 0) {
            val offset = decoded.exp.calculateTimerOffset()
            Log.d(TAG, "setCredentials, schedule for $offset")

            refreshTokenTimer.scheduleTimer(offset)
        }
    }

    override fun updateCredentials(accessToken: String, refreshToken: String) {
        if (accessToken.isBlank() || refreshToken.isBlank()) {
            throw IllegalArgumentException("Access token and refresh token must not be blank")
        }
        setCredentials(accessToken, refreshToken)
    }

    private fun clearCredentials() {
        this.refreshToken.value = null
        this.accessToken.value = null
        this.user.value = null
        this.isAuthenticated.value = false
    }

    fun handleHostedLoginCallback(
        code: String,
        webView: WebView? = null,
        activity: Activity? = null,
        callback: (() -> Unit)? = null,
    ): Boolean {
        if (stepUpAuthenticator.handleHostedLoginCallback(activity)) {
            return false
        }

        val codeVerifier = credentialManager.getCodeVerifier()
        val redirectUrl = Constants.oauthCallbackUrl(baseUrl)

        if (codeVerifier == null) {
            return false
        }

        bgScope.launch {
            try {
                val data = api.exchangeToken(code, redirectUrl, codeVerifier)
                if (data != null) {
                    setCredentials(data.access_token, data.refresh_token)

                    callback?.invoke()
                } else {
                    Log.e(TAG, "Failed to exchange token")
                    handleFailedTokenExchange(webView, activity, callback)
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleHostedLoginCallback failed", e)
                handleFailedTokenExchange(webView, activity, callback)
            }
        }

        return true
    }

    private suspend fun handleFailedTokenExchange(
        webView: WebView?,
        activity: Activity?,
        callback: (() -> Unit)?
    ) {
        if (webView != null) {
            val authorizeUrl = AuthorizeUrlGeneratorProvider.getAuthorizeUrlGenerator()
            val url = authorizeUrl.generate()
            withContext(mainDispatcher) {
                webView.loadUrl(url.first)
            }
        } else if (activity != null && callback == null) {
            login(activity)
        }
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
            webLoading.observable,
        ).subscribe {
            showLoader.value =
                initializing.value || (!isAuthenticated.value && (isLoading.value || webLoading.value))
        }

        bgScope.launch {

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

    /**
     * Sends a refresh token to the server to obtain a new access token.
     *
     *  @return true if the refresh token was successfully sent and a new access token was obtained, false otherwise.
     * @throws Exception if an error occurs during the process.
     */
    @Throws(IllegalArgumentException::class, IOException::class)
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
            bgScope.launch {
                refreshTokenIfNeeded()
            }
            return
        }

        val decoded = JWTHelper.decode(accessToken)
        if (decoded.exp > 0) {
            val offset = decoded.exp.calculateTimerOffset()
            if (offset <= 0) {
                Log.d(TAG, "Refreshing Token...")
                bgScope.launch {
                    refreshTokenIfNeeded()
                }
            } else {
                Log.d(TAG, "Schedule Refreshing Token for $offset")
                refreshTokenTimer.scheduleTimer(offset)
            }
        }
    }
}