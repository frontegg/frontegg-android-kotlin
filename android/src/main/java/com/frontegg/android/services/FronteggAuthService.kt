package com.frontegg.android.services

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
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
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.utils.Constants
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.utils.SessionTracker
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
    private val sessionTracker = SessionTracker(credentialManager.context)
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

    @Volatile
    private var refreshingInProgress = false
    private var refreshRetryCount = 0
    private val maxRetries = 2
    private val baseRetryDelayMs = 3000L

    override fun refreshTokenIfNeeded(): Boolean {
        return try {
            refreshingInProgress = true
            
            val success = this.sendRefreshToken()
            if (success) {
                refreshRetryCount = 0 // Reset on success
            }
            success
        } catch (e: FailedToAuthenticateException) {
            Log.e(TAG, "Refresh token is invalid, clearing credentials", e)
            refreshRetryCount = 0
            // Clear credentials when refresh token is invalid
            clearCredentials()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send refresh token request (attempt ${refreshRetryCount + 1})", e)
            
            // Enhanced retry logic
            if (isNetworkError() && refreshRetryCount < maxRetries) {
                refreshRetryCount++
                val delayMs = baseRetryDelayMs * refreshRetryCount // 3s, 6s
                Log.d(TAG, "Scheduling retry in ${delayMs}ms (attempt ${refreshRetryCount}/$maxRetries)")
                
                // Schedule retry using the timer
                refreshTokenTimer.scheduleTimer(delayMs)
                return false
            } else {
                refreshRetryCount = 0
                // For network errors after max retries, don't consider it a failure
                // Keep user authenticated and let the system retry later
                if (isNetworkError()) {
                    Log.w(TAG, "Max retries reached for network error, keeping user authenticated")
                    return true // Return true to indicate "success" (user stays logged in)
                } else {
                    return false // Non-network errors are real failures
                }
            }
        } finally {
            refreshingInProgress = false
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


    private fun setCredentials(accessToken: String, refreshToken: String): Boolean {

        if (credentialManager.save(CredentialKeys.REFRESH_TOKEN, refreshToken)
            && credentialManager.save(CredentialKeys.ACCESS_TOKEN, accessToken)
        ) {
            try {
                val user = api.me()
                if (user != null) {
                    updateStateWithCredentials(accessToken, refreshToken, user)
                    return true
                } else {
                    // Don't clear credentials on network errors - keep tokens for retry
                    this.isLoading.value = false
                    this.initializing.value = false
                    return false
                }
            } catch (e: Exception) {
                // Don't clear credentials on network errors - keep tokens for retry
                this.isLoading.value = false
                this.initializing.value = false
                return false
            }
        } else {
            clearCredentials()
            return false
        }
    }

    private fun setCredentials(authResponse: com.frontegg.android.models.AuthResponse): Boolean {
        val accessToken = authResponse.access_token
        val refreshToken = authResponse.refresh_token

        if (credentialManager.save(CredentialKeys.REFRESH_TOKEN, refreshToken)
            && credentialManager.save(CredentialKeys.ACCESS_TOKEN, accessToken)
        ) {
            try {
                val user = api.me()
                if (user != null) {
                    updateStateWithCredentials(accessToken, refreshToken, user, authResponse)
                    return true
                } else {
                    // Don't clear credentials on network errors - keep tokens for retry
                    this.isLoading.value = false
                    this.initializing.value = false
                    return false
                }
            } catch (e: Exception) {
                // Don't clear credentials on network errors - keep tokens for retry
                this.isLoading.value = false
                this.initializing.value = false
                return false
            }
        } else {
            clearCredentials()
            return false
        }
    }

    private fun updateStateWithCredentials(accessToken: String, refreshToken: String, user: User) {
        this.refreshToken.value = refreshToken
        this.accessToken.value = accessToken
        this.user.value = user
        this.isAuthenticated.value = true

        // Track session start if this is a new session
        if (sessionTracker.getSessionStartTime() == 0L) {
            sessionTracker.trackSessionStart()
        }

        // Cancel previous job if it exists
        refreshTokenTimer.cancelLastTimer()

        val decoded = JWTHelper.decode(accessToken)
        if (decoded.exp > 0) {
            val offset = decoded.exp.calculateTimerOffset()

            refreshTokenTimer.scheduleTimer(offset)
        }
    }

    private fun updateStateWithCredentials(accessToken: String, refreshToken: String, user: User, authResponse: com.frontegg.android.models.AuthResponse) {
        this.refreshToken.value = refreshToken
        this.accessToken.value = accessToken
        this.user.value = user
        this.isAuthenticated.value = true

        // Track session start if this is a new session with lifetime info from API
        if (sessionTracker.getSessionStartTime() == 0L) {
            sessionTracker.trackSessionStart()
        }

        // Cancel previous job if it exists
        refreshTokenTimer.cancelLastTimer()

        val decoded = JWTHelper.decode(accessToken)
        if (decoded.exp > 0) {
            val offset = decoded.exp.calculateTimerOffset()

            refreshTokenTimer.scheduleTimer(offset)
        }
    }

    override fun updateCredentials(accessToken: String, refreshToken: String) {
        if (accessToken.isBlank() || refreshToken.isBlank()) {
            throw IllegalArgumentException("Access token and refresh token must not be blank")
        }
        setCredentials(accessToken, refreshToken)
    }

    override fun getSessionStartTime(): Long {
        return sessionTracker.getSessionStartTime()
    }

    override fun getSessionDuration(): Long {
        return sessionTracker.getSessionDuration()
    }

    override fun hasSessionData(): Boolean {
        return sessionTracker.hasSessionData()
    }

    fun clearCredentials() { 
        this.refreshToken.value = null
        this.accessToken.value = null
        this.user.value = null
        this.isAuthenticated.value = false
        
        // Clear session tracking data
        sessionTracker.clearSessionData()
    }

    fun handleHostedLoginCallback(
        code: String,
        webView: WebView? = null,
        activity: Activity? = null,
        callback: (() -> Unit)? = null,
        redirectUrlOverride: String? = null,
    ): Boolean {
        if (stepUpAuthenticator.handleHostedLoginCallback(activity)) {
            return false
        }

        val codeVerifier = credentialManager.getCodeVerifier()
        val redirectUrl = redirectUrlOverride ?: Constants.oauthCallbackUrl(baseUrl)

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
                // We have tokens, let's validate them
                accessToken.value = accessTokenSaved
                refreshToken.value = refreshTokenSaved
                
                // First, try to use the existing access token
                try {
                    val user = api.me()
                    if (user != null) {
                        // Access token is valid, user is authenticated
                        this@FronteggAuthService.user.value = user
                        this@FronteggAuthService.isAuthenticated.value = true
                        Log.d(TAG, "Access token is valid, user authenticated")
                        initializing.value = false
                        isLoading.value = false
                        return@launch
                    }
                } catch (e: FailedToAuthenticateException) {
                    // Access token is invalid (401), try to refresh
                    Log.d(TAG, "Access token invalid (401), attempting refresh")
                } catch (e: Exception) {
                    // Network error, keep tokens for later retry
                    Log.w(TAG, "Network error during token validation, keeping tokens", e)
                    if (isNetworkError()) {
                        // Offline mode, assume authenticated until network returns
                        this@FronteggAuthService.isAuthenticated.value = true
                        this@FronteggAuthService.isLoading.value = true
                        initializing.value = false
                        return@launch
                    }
                }
                
                // Try to refresh the token
                try {
                    val success = sendRefreshToken()
                    if (success) {
                        // Refresh successful, user is authenticated
                        Log.d(TAG, "Token refresh successful during initialization")
                        initializing.value = false
                        isLoading.value = false
                    } else {
                        // Refresh failed - check if it's a network error
                        Log.d(TAG, "Token refresh failed during initialization")
                        if (isNetworkError()) {
                            // Network error, keep tokens and assume authenticated until network returns
                            Log.w(TAG, "Network error during token refresh, keeping tokens for retry")
                            this@FronteggAuthService.isAuthenticated.value = true
                            this@FronteggAuthService.isLoading.value = true
                        } else {
                            // Non-network error, logout
                            Log.e(TAG, "Non-network error during token refresh, clearing credentials")
                            clearCredentials()
                        }
                        initializing.value = false
                    }
                } catch (e: FailedToAuthenticateException) {
                    // Refresh token is also invalid, logout
                    Log.e(TAG, "Refresh token is invalid during initialization, clearing credentials", e)
                    clearCredentials()
                    initializing.value = false
                    isLoading.value = false
                } catch (e: Exception) {
                    // Network error during refresh
                    Log.e(TAG, "Failed to refresh token during initialization", e)
                    if (isNetworkError()) {
                        // Offline mode, assume authenticated until network returns
                        this@FronteggAuthService.isAuthenticated.value = true
                        this@FronteggAuthService.isLoading.value = true
                    } else {
                        // Other error, logout
                        clearCredentials()
                    }
                    initializing.value = false
                }

            } else {
                // No tokens, show login screen
                Log.d(TAG, "No tokens found, showing login screen")
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
            lastRefreshError = null // Clear error on success
            
            // Save new tokens immediately to prevent rotation issues
            credentialManager.save(CredentialKeys.REFRESH_TOKEN, data.refresh_token)
            credentialManager.save(CredentialKeys.ACCESS_TOKEN, data.access_token)
            
            // Update state with new tokens
            this.refreshToken.value = data.refresh_token
            this.accessToken.value = data.access_token
            
            // Try to get user info to validate the new access token
            try {
                val user = api.me()
                if (user != null) {
                    this.user.value = user
                    this.isAuthenticated.value = true
                    
                    // Track successful token refresh
                    sessionTracker.trackTokenRefresh()
                    
                    // Schedule next refresh timer
                    refreshTokenTimer.cancelLastTimer()
                    val decoded = JWTHelper.decode(data.access_token)
                    if (decoded.exp > 0) {
                        val offset = decoded.exp.calculateTimerOffset()
                        refreshTokenTimer.scheduleTimer(offset)
                    }
                } else {
                    // User is null, but we have tokens - this shouldn't happen
                    Log.w(TAG, "User is null after successful token refresh")
                    this.isAuthenticated.value = false
                }
            } catch (e: Exception) {
                // If me() fails, the tokens might be invalid
                Log.w(TAG, "Failed to fetch user info after token refresh", e)
                this.isAuthenticated.value = false
                this.isLoading.value = true
            }
            
            // Always set initializing to false after successful token refresh
            this.initializing.value = false
            this.isLoading.value = false
            
            return true
        } catch (e: Exception) {
            lastRefreshError = e
            throw e
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

    private var lastRefreshError: Exception? = null

    private fun isNetworkError(): Boolean {
        return when (lastRefreshError) {
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.net.UnknownHostException,
            is IOException -> true
            is FailedToAuthenticateException -> false
            else -> false
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
                bgScope.launch {
                    refreshTokenIfNeeded()
                }
            } else {
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
            
            // Build query parameters (like Swift)
            val successQueryParams = mutableMapOf<String, String>()
            
            if (!code.isNullOrEmpty()) {
                successQueryParams["code"] = code
            }
            
            if (!idToken.isNullOrEmpty()) {
                successQueryParams["id_token"] = idToken
            }
            
            val redirectUri = defaultRedirectUri()
            // Encode redirectUri like iOS: allow only alphanumerics
            val alphaNum = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            successQueryParams["redirectUri"] = android.net.Uri.encode(redirectUri, alphaNum)
            
            if (!processedState.isNullOrEmpty()) {
                val alphaNumState = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                successQueryParams["state"] = android.net.Uri.encode(processedState, alphaNumState)
            }
            
            // Build query string safely (like Swift)
            val queryString = successQueryParams.entries.joinToString("&") { (key, value) ->
                "$key=$value"
            }
            
            val finalUrl = "${storage.baseUrl}/oauth/account/social/success?$queryString"
            
            return finalUrl
            
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
        
        
        try {
            val urlGenerator = com.frontegg.android.utils.SocialLoginUrlGenerator.getInstance()
            val authURL = urlGenerator.authorizeURL(activity, provider, action)
            
            if (authURL == null) {
                Log.e(TAG, "Failed to generate auth URL for provider: ${provider.value}")
                return
            }
            
            
            
            val webAuthenticator = com.frontegg.android.utils.WebAuthenticator.getInstance()
            webAuthenticator.start(activity, authURL, ephemeralSession ?: true) { callbackURL, error ->
                if (error != null) {
                    Log.e(TAG, "Social login error: ${error.message}")
                } else if (callbackURL != null) {
                    
                    
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
     * Get default redirect URI for social logins
     * @return Social login redirect URI: /oauth/account/social/success
     */
    fun defaultSocialLoginRedirectUri(): String {
        val baseUrl = storage.baseUrl
        val baseRedirectUri = "$baseUrl/oauth/account/social/success"
        // Return raw; encoding is applied where used as query param
        return baseRedirectUri
    }

    /**
     * Get default redirect URI for OAuth flows
     * @return OAuth redirect URI: /oauth/account/redirect/android/{packageName}
     */
    fun defaultRedirectUri(): String {
        val baseUrl = storage.baseUrl
        val packageName = storage.packageName
        val baseRedirectUri = "$baseUrl/oauth/account/redirect/android/$packageName"
        // Return raw; encoding is applied where used as query param
        return baseRedirectUri
    }

}