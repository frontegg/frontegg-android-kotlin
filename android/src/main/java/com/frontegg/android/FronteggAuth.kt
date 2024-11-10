package com.frontegg.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialException
import com.frontegg.android.embedded.CredentialManagerHandler
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.exceptions.MfaRequiredException
import com.frontegg.android.exceptions.WebAuthnAlreadyRegisteredInLocalDeviceException
import com.frontegg.android.exceptions.isWebAuthnRegisteredBeforeException
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.Api
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.services.RefreshTokenService
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.Constants
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.utils.ObservableValue
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule


@SuppressLint("CheckResult")
@OptIn(DelicateCoroutinesApi::class)
class FronteggAuth(
    var baseUrl: String,
    var clientId: String,
    var applicationId: String?,
    val credentialManager: CredentialManager,
    val regions: List<RegionConfig>,
    var selectedRegion: RegionConfig?
) {


    companion object {
        private val TAG = FronteggAuth::class.java.simpleName
        const val JOB_ID = 1234 // Unique ID for the JobService

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
    val refreshingToken: ObservableValue<Boolean> = ObservableValue(false)
    var pendingAppLink: String? = null
    val isMultiRegion: Boolean = regions.isNotEmpty()
    var refreshTokenJob: JobInfo? = null;
    var timerTask: TimerTask? = null;
    private var _api: Api? = null

    init {

        if (!isMultiRegion || selectedRegion !== null) {
            this.initializeSubscriptions()
        }
    }

    val api: Api
        get() = (if (this._api == null) {
            this._api = Api(this.baseUrl, this.clientId, this.applicationId, credentialManager)
            this._api
        } else {
            this._api
        })!!


    fun reinitWithRegion(region: RegionConfig) {
        selectedRegion = region

        this.baseUrl = region.baseUrl
        this.clientId = region.clientId
        this.applicationId = region.applicationId
        this._api = null

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

    fun refreshTokenWhenNeeded() {
        val accessToken = this.accessToken.value

        if (this.refreshToken.value == null) {
            return
        }


        cancelLastTimer()

        if (accessToken == null) {
            // when we have valid refreshToken without accessToken => failed to refresh in background
            GlobalScope.launch(Dispatchers.IO) {
                sendRefreshToken()
            }
            return;
        }

        val decoded = JWTHelper.decode(accessToken)
        if (decoded.exp > 0) {
            val offset = calculateTimerOffset(decoded.exp)
            if (offset <= 0) {
                Log.d(TAG, "Refreshing Token...")
                GlobalScope.launch(Dispatchers.IO) {
                    sendRefreshToken()
                }
            } else {
                Log.d(TAG, "Schedule Refreshing Token for $offset")
                this.scheduleTimer(offset)
            }
        }
    }

    fun refreshTokenIfNeeded(): Boolean {

        Log.d(TAG, "refreshTokenIfNeeded()")

        return try {
            this.sendRefreshToken()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send refresh token request", e)
            false
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

            // Cancel previous job if it exists
            this.cancelLastTimer()


            if (decoded.exp > 0) {
                val offset = calculateTimerOffset(decoded.exp)
                Log.d(TAG, "setCredentials, schedule for $offset")

                this.scheduleTimer(offset)
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

    private fun cancelLastTimer() {
        Log.d(TAG, "Cancel Last Timer")
        if (timerTask != null) {
            timerTask?.cancel()
            timerTask = null
        }
        if (refreshTokenJob != null) {
            val context = FronteggApp.getInstance().context
            val jobScheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            this.refreshTokenJob = null
        }
    }

    fun scheduleTimer(offset: Long) {
        FronteggApp.getInstance().lastJobStart = Instant.now().toEpochMilli()
        if (FronteggApp.getInstance().appInForeground) {
            Log.d(TAG, "[foreground] Start Timer task (${offset} ms)")

            this.timerTask = Timer().schedule(offset) {
                Log.d(
                    TAG,
                    "[foreground] Job started, (${
                        Instant.now().toEpochMilli() - FronteggApp.getInstance().lastJobStart
                    } ms)"
                )
                refreshTokenIfNeeded()
            }

        } else {
            Log.d(TAG, "[background] Start Job Scheduler task (${offset} ms)")
            val context = FronteggApp.getInstance().context
            val jobScheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            // Schedule the job
            val jobInfo = JobInfo.Builder(
                JOB_ID, ComponentName(context, RefreshTokenService::class.java)
            )
                .setMinimumLatency(offset / 2) // Schedule the job to run after the offset
                .setOverrideDeadline(offset) // Add a buffer to the deadline
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Require network
                .setBackoffCriteria(10000, JobInfo.BACKOFF_POLICY_LINEAR)
                .build()
            this.refreshTokenJob = jobInfo
            jobScheduler.schedule(jobInfo)
        }
    }

    fun handleHostedLoginCallback(
        code: String,
        webView: WebView? = null,
        activity: Activity? = null,
        callback: (() -> Unit)? = null
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


    fun logout(callback: () -> Unit = {}) {
        isLoading.value = true
        this.cancelLastTimer()

        GlobalScope.launch(Dispatchers.IO) {

            val logoutCookies = getDomainCookie(baseUrl)
            val logoutAccessToken = accessToken.value

            if (logoutCookies != null &&
                logoutAccessToken != null &&
                FronteggApp.getInstance().isEmbeddedMode
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

    fun login(activity: Activity, loginHint: String? = null, callback: (() -> Unit)? = null) {
        if (FronteggApp.getInstance().isEmbeddedMode) {
            EmbeddedAuthActivity.authenticate(activity, loginHint, callback)
        } else {
            AuthenticationActivity.authenticate(activity, loginHint, callback)
        }
    }

    fun directLoginAction(
        activity: Activity,
        type: String,
        data: String,
        callback: (() -> Unit)? = null
    ) {
        if (FronteggApp.getInstance().isEmbeddedMode) {
            EmbeddedAuthActivity.directLoginAction(activity, type, data, callback)
        } else {
            Log.w(TAG, "Direct login action is not supported in non-embedded mode")
        }
    }


    fun switchTenant(tenantId: String, callback: (Boolean) -> Unit = {}) {
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

    private fun calculateTimerOffset(expirationTime: Long): Long {
        val now: Long = Instant.now().toEpochMilli()
        val remainingTime = (expirationTime * 1000) - now

        val minRefreshWindow = 20000 // minimum 20 seconds before exp
        val adaptiveRefreshTime = remainingTime * 0.8 // 80% of remaining time

        return if (remainingTime > minRefreshWindow) {
            adaptiveRefreshTime.toLong()
        } else {
            (remainingTime - minRefreshWindow).coerceAtLeast(0)
        }
    }


    fun loginWithPasskeys(activity: Activity, callback: ((error: Exception?) -> Unit)? = null) {

        val passkeyManager = CredentialManagerHandler(activity)
        val scope = MainScope()
        scope.launch {
            try {

                val webAuthnPreloginRequest = withContext(Dispatchers.IO) { api.webAuthnPrelogin() }

                val result = passkeyManager.getPasskey(webAuthnPreloginRequest.jsonChallenge)

                val challengeResponse = (result.credential as PublicKeyCredential).authenticationResponseJson

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

    fun registerPasskeys(activity: Activity, callback: ((error: Exception?) -> Unit)? = null) {

        val passkeyManager = CredentialManagerHandler(activity)
        val scope = MainScope()
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

        if (FronteggApp.getInstance().isEmbeddedMode) {
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
}
