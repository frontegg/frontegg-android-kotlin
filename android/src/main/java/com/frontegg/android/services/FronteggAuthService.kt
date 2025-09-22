package com.frontegg.android.services

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.credentials.PublicKeyCredential
import com.frontegg.android.AuthenticationActivity
import com.frontegg.android.EmbeddedAuthActivity
import com.frontegg.android.FronteggAuth
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.exceptions.MfaRequiredException
import com.frontegg.android.exceptions.WebAuthnAlreadyRegisteredInLocalDeviceException
import com.frontegg.android.exceptions.isWebAuthnRegisteredBeforeException
import com.frontegg.android.models.SocialLoginPostLoginRequest
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.utils.Constants
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.utils.calculateTimerOffset
import com.google.gson.JsonParser
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
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
    
    override var webview: WebView? = null
    override val featureFlags: com.frontegg.android.services.FeatureFlags = com.frontegg.android.services.FeatureFlags.getInstance()


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

        // Load feature flags on initialization
        bgScope.launch {
            try {
                featureFlags.loadFlags(credentialManager.context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load feature flags during initialization", e)
            }
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

        val rt = this.refreshToken.value
        val at = this.accessToken.value
        Log.d(TAG, "refreshTokenIfNeeded() currentTokens access='${maskToken(at)}' refresh='${maskToken(rt)}'")

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
            Log.d(TAG, "setCredentials() saved tokens access='${maskToken(accessToken)}' refresh='${maskToken(refreshToken)}', going to get user info")

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
        Log.d(TAG, "clearCredentials()")
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
            val authorizeUrl =
                AuthorizeUrlGeneratorProvider.getAuthorizeUrlGenerator(credentialManager.context)
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
            Log.d(TAG, "initializeSubscriptions() saved tokens access='${maskToken(accessTokenSaved)}' refresh='${maskToken(refreshTokenSaved)}'")

            if (accessTokenSaved != null && refreshTokenSaved != null) {
                accessToken.value = accessTokenSaved
                refreshToken.value = refreshTokenSaved

                if (!refreshTokenIfNeeded()) {
                    // Offline-safe: keep saved refresh token to allow later reconnect
                    Log.d(TAG, "initializeSubscriptions(): refresh failed; preserving saved refresh token")
                    accessToken.value = null
                    // keep refreshToken.value as is
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
            Log.d(TAG, "sendRefreshToken() with refresh='${maskToken(refreshToken)}'")
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

    private fun maskToken(token: String?): String {
        if (token.isNullOrBlank()) return "<null>"
        val head = token.take(6)
        val tail = token.takeLast(4)
        return "$head…$tail"
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

    /**
     * Handle social login callback from custom scheme or redirect URL
     * @param url The callback URL
     * @return URL to redirect to after successful callback processing, or null if failed
     */
    fun handleSocialLoginCallback(url: String): String? {
        Log.d(TAG, "handleSocialLoginCallback called with url: $url")
        
        return try {
            val uri = Uri.parse(url)
            val host = uri.host
            val path = uri.path ?: ""
            
            // 1) Host must match Frontegg base URL host
            val baseUrl = storage.baseUrl
            val allowedHost = Uri.parse(baseUrl).host
            if (host != allowedHost) {
                Log.e(TAG, "Host mismatch: $host != $allowedHost")
                return null
            }
            
            // 2) Path: /oauth/account/redirect/android/{packageName}/{provider} or /android/oauth/callback
            val packageName = storage.packageName
            val expectedPrefix = "/oauth/account/redirect/android/"
            val expectedPrefix2 = "/android/oauth/callback"
            
            if (!path.startsWith(expectedPrefix) && !path.startsWith(expectedPrefix2)) {
                Log.e(TAG, "Invalid callback path: $path")
                return null
            }
            
            if (packageName.isEmpty()) {
                Log.e(TAG, "Package name is empty")
                return null
            }
            
            // Extract query parameters
            val queryParams = mutableMapOf<String, String>()
            uri.queryParameterNames.forEach { key: String ->
                uri.getQueryParameter(key)?.let { value: String ->
                    queryParams[key] = value
                }
            }
            
            // Extract supported params
            val code = queryParams["code"]
            val idToken = queryParams["id_token"]
            val state = queryParams["state"]
            
            if (code.isNullOrEmpty() && idToken.isNullOrEmpty()) {
                Log.e(TAG, "No code or id_token in callback")
                return null
            }
            
            // Process state
            val processedState = if (!state.isNullOrEmpty()) {
                try {
                    val stateJson = JsonParser.parseString(state).asJsonObject
                    stateJson.remove("platform")
                    stateJson.remove("bundleId")
                    stateJson.toString()
                } catch (e: Exception) {
                    state // fallback if state is not valid JSON
                }
            } else {
                state
            }
            
            // Cancel current session if exists
            com.frontegg.android.utils.WebAuthenticator.getInstance().cancel()
            
            // Build redirect URL
            val redirectUrl = "$baseUrl/oauth/account/social/success"
            val redirectUri = Uri.parse(redirectUrl).buildUpon()
            
            if (!code.isNullOrEmpty()) {
                redirectUri.appendQueryParameter("code", code)
            }
            if (!idToken.isNullOrEmpty()) {
                redirectUri.appendQueryParameter("id_token", idToken)
            }
            if (!processedState.isNullOrEmpty()) {
                redirectUri.appendQueryParameter("state", processedState)
            }
            
            val finalUrl = redirectUri.build().toString()
            Log.d(TAG, "Social login callback processed successfully, redirecting to: $finalUrl")
            finalUrl
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle social login callback", e)
            null
        }
    }

    /**
     * Login with social login provider
     */
    suspend fun loginWithSocialLoginProvider(
        activity: Activity,
        provider: com.frontegg.android.models.SocialLoginProvider,
        action: com.frontegg.android.models.SocialLoginAction = com.frontegg.android.models.SocialLoginAction.LOGIN,
        ephemeralSession: Boolean? = true
    ) {
        Log.d(TAG, "loginWithSocialLoginProvider: ${provider.value}, action: ${action.value}")
        
        try {
            val urlGenerator = com.frontegg.android.utils.SocialLoginUrlGenerator.getInstance()
            val authURL = urlGenerator.authorizeURL(activity, provider, action)
            
            if (authURL == null) {
                Log.e(TAG, "Failed to generate auth URL for provider: ${provider.value}")
                return
            }
            
            Log.d(TAG, "Generated auth URL: $authURL")
            
            val webAuthenticator = com.frontegg.android.utils.WebAuthenticator.getInstance()
            webAuthenticator.start(activity, authURL, ephemeralSession ?: true) { callbackURL, error ->
                if (error != null) {
                    Log.e(TAG, "Social login error: ${error.message}")
                } else if (callbackURL != null) {
                    Log.d(TAG, "Social login callback URL: $callbackURL")
                    
                    // Handle the callback
                    val redirectURL = handleSocialLoginCallback(callbackURL)
                    if (redirectURL != null && webview != null) {
                        // Load the redirect URL in the webview
                        webview?.loadUrl(redirectURL)
                    }
                } else {
                    Log.i(TAG, "Social login callback invoked with no URL and no error")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start social login for provider: ${provider.value}", e)
        }
    }

    /**
     * Get social login configuration
     */
    suspend fun getSocialLoginConfig(): com.frontegg.android.models.SocialLoginConfig {
        return try {
            api.getSocialLoginConfig()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get social login config", e)
            throw e
        }
    }

    /**
     * Get feature flags
     */
    suspend fun getFeatureFlags(): String {
        return try {
            api.getFeatureFlags()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get feature flags", e)
            throw e
        }
    }

    /**
     * Generate custom social login URL
     * @param activity Android activity
     * @param provider Social login provider
     * @param action Social login action (login or signup)
     * @return Generated social login URL or null if failed
     */
    suspend fun generateCustomSocialLoginUrl(
        activity: Activity,
        provider: com.frontegg.android.models.SocialLoginProvider,
        action: com.frontegg.android.models.SocialLoginAction = com.frontegg.android.models.SocialLoginAction.LOGIN
    ): String? {
        Log.d(TAG, "generateCustomSocialLoginUrl: ${provider.value}, action: ${action.value}")
        
        return try {
            val urlGenerator = com.frontegg.android.utils.SocialLoginUrlGenerator.getInstance()
            urlGenerator.authorizeURL(activity, provider, action)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate custom social login URL for provider: ${provider.value}", e)
            null
        }
    }
}