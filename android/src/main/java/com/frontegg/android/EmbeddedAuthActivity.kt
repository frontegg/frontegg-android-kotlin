package com.frontegg.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.frontegg.android.embedded.FronteggNativeBridge
import com.frontegg.android.embedded.FronteggWebView
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.FronteggState
import com.frontegg.android.services.StepUpAuthenticator
import com.frontegg.android.ui.DefaultLoader
import com.frontegg.android.ui.FronteggBaseActivity
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.NullableObject
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import kotlin.time.Duration


class EmbeddedAuthActivity : FronteggBaseActivity() {
    private val storage = FronteggInnerStorage()
    private lateinit var webView: FronteggWebView
    private var webViewUrl: String? = null
    private var directLoginLaunchedDone: Boolean = false
    private var directLoginLaunched: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_embedded_auth)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fadein, R.anim.fadeout)

        FronteggState.webLoading.value = false
        webView = findViewById(R.id.custom_webview)
        loaderContainer = findViewById(R.id.loaderContainer)
        val loaderView = DefaultLoader.create(this)
        loaderContainer?.addView(loaderView)
        loaderContainer?.visibility = View.VISIBLE

        consumeIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(DIRECT_LOGIN_ACTION_LAUNCHED, this.directLoginLaunched)
        outState.putBoolean(DIRECT_LOGIN_ACTION_LAUNCHED_DONE, this.directLoginLaunchedDone)
    }

    private fun consumeIntent(intent: Intent) {
        Log.d(TAG, "consumeIntent")
        val intentLaunched =
            intent.extras?.getBoolean(AUTH_LAUNCHED, false) ?: false

        this.directLoginLaunched =
            intent.extras?.getBoolean(DIRECT_LOGIN_ACTION_LAUNCHED, false) ?: false
        this.directLoginLaunchedDone =
            intent.extras?.getBoolean(DIRECT_LOGIN_ACTION_LAUNCHED_DONE, false) ?: false


        if (directLoginLaunchedDone) {
            return
        }
        
        // If no extras but we have data (social login redirect), continue processing
        if (intent.extras == null && intent.data == null) {
            return
        }
        if (intentLaunched) {
            val url = intent.extras!!.getString(AUTHORIZE_URI)
            if (url == null) {
                setResult(RESULT_CANCELED)
                finish()
                return
            }
            webViewUrl = url
        } else if (directLoginLaunched) {
            val type = intent.extras!!.getString(DIRECT_LOGIN_ACTION_TYPE)
            val data = intent.extras!!.getString(DIRECT_LOGIN_ACTION_DATA)
            if (type == null || data == null) {
                setResult(RESULT_CANCELED)
                finish()
                return
            }

            val authorizeUri = AuthorizeUrlGenerator(this).generate()
            FronteggNativeBridge.directLoginWithContext(
                this, mapOf(
                    "type" to type,
                    "data" to data,
                    "additionalQueryParams" to mapOf(
                        "prompt" to "consent"
                    )
                ), true
            )
            webViewUrl = authorizeUri.first
        } else {
            val intentUrl = intent.data
            if (intentUrl == null) {
                setResult(RESULT_CANCELED)
                finish()
                return
            }

            webViewUrl = intentUrl.toString()
        }

        Log.d(TAG, "webViewUrl: $webViewUrl")
        
        if (webViewUrl == null) {
            Log.d(TAG, "webViewUrl is null, returning")
            return
        }

        // Always load URL for password reset and other account actions that don't require authentication
        // These URLs should be accessible regardless of auth state (initializing/authenticated)
        // This is especially important for Flutter apps where initialization may complete after activity starts
        // Check this FIRST before accessing fronteggAuth properties to avoid initialization issues
        val isPasswordResetOrAccountAction = webViewUrl?.contains("/oauth/account/reset-password") == true ||
                webViewUrl?.contains("/oauth/account/verify-email") == true ||
                webViewUrl?.contains("/oauth/account/verify-phone") == true ||
                webViewUrl?.contains("/oauth/account/accept-invitation") == true ||
                webViewUrl?.contains("/oauth/account/activate") == true ||
                webViewUrl?.contains("/oauth/account/invitation/accept") == true
        
        // Always load URL for social login redirects (oauth/account/social/success)
        val isSocialLoginRedirect = webViewUrl?.contains("/oauth/account/social/success") == true
        
        Log.d(TAG, "isPasswordResetOrAccountAction: $isPasswordResetOrAccountAction, isSocialLoginRedirect: $isSocialLoginRedirect")
        
        // If it's an account action URL (reset-password, etc.), load immediately without checking auth state
        if (isPasswordResetOrAccountAction || isSocialLoginRedirect) {
            Log.d(TAG, "loadUrl (account action/social redirect): $webViewUrl")
            webView.loadUrl(webViewUrl!!)
            webViewUrl = null
            return
        }
        
        // For other URLs, check auth state
        Log.d(TAG, "Checking load conditions: isStepUpAuthorization=${fronteggAuth.isStepUpAuthorization.value}, initializing=${fronteggAuth.initializing.value}, isAuthenticated=${fronteggAuth.isAuthenticated.value}")
        
        if (fronteggAuth.isStepUpAuthorization.value ||
                (!fronteggAuth.initializing.value &&
                        !fronteggAuth.isAuthenticated.value)
        ) {
            Log.d(TAG, "loadUrl $webViewUrl")
            webView.loadUrl(webViewUrl!!)
            webViewUrl = null
        } else {
            Log.d(TAG, "URL not loaded due to conditions not met")
        }
    }

    private val disposables: ArrayList<Disposable> = arrayListOf()
    private var loaderContainer: LinearLayout? = null

    private val showLoaderConsumer: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "showLoaderConsumer: ${it.value}")
        runOnUiThread {
            if (applicationContext.fronteggAuth.isStepUpAuthorization.value) {
                loaderContainer?.visibility = if (it.value) View.VISIBLE else View.GONE
            } else {
                loaderContainer?.visibility =
                    if (it.value || applicationContext.fronteggAuth.isAuthenticated.value) View.VISIBLE else View.GONE
            }
        }
    }
    private val isAuthenticatedConsumer: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "isAuthenticatedConsumer: ${it.value}")
        if (it.value) {
            runOnUiThread {
                navigateToAuthenticated()
                onAuthFinishedCallback?.invoke(null)
                onAuthFinishedCallback = null
            }
        }
    }

    private val isStepUpAuthorization: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "isStepUpAuthorization: ${it.value}")
        if (!it.value) {
            runOnUiThread {
                navigateToAuthenticated()
                onAuthFinishedCallback?.invoke(null)
                onAuthFinishedCallback = null
            }
        }
    }

    private fun navigateToAuthenticated() {
        val mainActivityClass = storage.mainActivityClass
        if (mainActivityClass != null) {
            val intent = Intent(this, mainActivityClass)
            startActivity(intent)
            finish()
        } else {
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()

        StepUpAuthenticator.resumeEmbeddedActivity()
        disposables.add(fronteggAuth.showLoader.subscribe(this.showLoaderConsumer))

        Log.d(
            TAG,
            "onResume isStepUpAuthorization: ${fronteggAuth.isStepUpAuthorization.value}"
        )

        if (!fronteggAuth.isStepUpAuthorization.value) {
            disposables.add(fronteggAuth.isAuthenticated.subscribe(this.isAuthenticatedConsumer))
        } else if (fronteggAuth.isStepUpAuthorization.value) {
            disposables.add(fronteggAuth.isStepUpAuthorization.subscribe(this.isStepUpAuthorization))
        }

        if (directLoginLaunchedDone) {
            onAuthFinishedCallback?.invoke(null)
            onAuthFinishedCallback = null
            FronteggState.isLoading.value = false
            FronteggState.showLoader.value = false
            setResult(RESULT_OK)
            finish()
            return
        }

        if (directLoginLaunched) {
            directLoginLaunchedDone = true
        }

        // Consume intent data if available (for social login redirects)
        if (intent.data != null && webViewUrl == null) {
            consumeIntent(intent)
        }
        
        // Fallback: Retry loading URL if it wasn't loaded in onCreate due to initialization state
        // This can happen when app is opened from terminated state in Flutter
        // Even though reset-password URLs should load immediately, this ensures they load once initialization completes
        if (webViewUrl != null) {
            val isPasswordResetOrAccountAction = webViewUrl?.contains("/oauth/account/reset-password") == true ||
                    webViewUrl?.contains("/oauth/account/verify-email") == true ||
                    webViewUrl?.contains("/oauth/account/verify-phone") == true ||
                    webViewUrl?.contains("/oauth/account/accept-invitation") == true ||
                    webViewUrl?.contains("/oauth/account/activate") == true ||
                    webViewUrl?.contains("/oauth/account/invitation/accept") == true ||
                    webViewUrl?.contains("/oauth/account/social/success") == true
            
            // Always load account action URLs regardless of auth state
            if (isPasswordResetOrAccountAction || 
                fronteggAuth.isStepUpAuthorization.value ||
                (!fronteggAuth.initializing.value && !fronteggAuth.isAuthenticated.value)) {
                Log.d(TAG, "Retrying loadUrl in onResume: $webViewUrl")
                webView.loadUrl(webViewUrl!!)
                webViewUrl = null
            }
        }

    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        consumeIntent(intent)

    }

    override fun onPause() {
        super.onPause()
        disposables.forEach {
            it.dispose()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FronteggState.webLoading.value = false
        onAuthFinishedCallback?.invoke(CanceledByUserException())
        onAuthFinishedCallback = null
    }


    companion object {
        const val OAUTH_LOGIN_REQUEST = 100001
        const val AUTHORIZE_URI = "com.frontegg.android.AUTHORIZE_URI"
        const val DIRECT_LOGIN_ACTION_LAUNCHED = "com.frontegg.android.DIRECT_LOGIN_ACTION_LAUNCHED"
        const val DIRECT_LOGIN_ACTION_LAUNCHED_DONE =
            "com.frontegg.android.DIRECT_LOGIN_ACTION_LAUNCHED_DONE"
        const val DIRECT_LOGIN_ACTION_TYPE = "com.frontegg.android.DIRECT_LOGIN_ACTION_TYPE"
        const val DIRECT_LOGIN_ACTION_DATA = "com.frontegg.android.DIRECT_LOGIN_ACTION_DATA"
        private const val AUTH_LAUNCHED = "com.frontegg.android.AUTH_LAUNCHED"
        private val TAG = EmbeddedAuthActivity::class.java.simpleName
        var onAuthFinishedCallback: ((error: Exception?) -> Unit)? = null // Store callback

        @JvmStatic
        fun authenticate(
            activity: Activity,
            loginHint: String? = null,
            callback: ((error: Exception?) -> Unit)? = null
        ) {
            Log.d(TAG, "authenticate")
            val intent = Intent(activity, EmbeddedAuthActivity::class.java)

            val authorizeUri = AuthorizeUrlGenerator(activity).generate(loginHint = loginHint)
            intent.putExtra(AUTH_LAUNCHED, true)
            intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
            onAuthFinishedCallback = callback
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
        }

        fun directLoginAction(
            activity: Activity,
            type: String,
            data: String,
            callback: ((error: Exception?) -> Unit)? = null
        ) {
            Log.d(TAG, "directLoginAction")
            val intent = Intent(activity, EmbeddedAuthActivity::class.java)

            intent.putExtra(DIRECT_LOGIN_ACTION_LAUNCHED, true)
            intent.putExtra(DIRECT_LOGIN_ACTION_TYPE, type)
            intent.putExtra(DIRECT_LOGIN_ACTION_DATA, data)
            onAuthFinishedCallback = callback
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)


            /**
             * to be replaced with the following code after the hosted login-box is supported
             *
             * val directAction = mapOf(
             *  "type" to type,
             *  "data" to data
             * )
             * val directActionString = JSONObject(directAction).toString()
             * val directActionBase64 = android.util.Base64.encodeToString(directActionString.toByteArray(), android.util.Base64.DEFAULT)
             * val authorizeUri = AuthorizeUrlGenerator().generate(null, directActionBase64)
             * intent.putExtra(AUTH_LAUNCHED, true)
             * intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
             * activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
             */
        }

        fun authenticateWithMultiFactor(
            activity: Activity,
            mfaLoginAction: String? = null,
            callback: ((error: Exception?) -> Unit)? = null
        ) {
            Log.d(TAG, "authenticateWithMultiFactor")
            val intent = Intent(activity, EmbeddedAuthActivity::class.java)

            val authorizeUri =
                AuthorizeUrlGenerator(activity).generate(loginAction = mfaLoginAction)
            intent.putExtra(AUTH_LAUNCHED, true)
            intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
            onAuthFinishedCallback = callback
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
        }

        fun authenticateWithStepUp(
            activity: Activity,
            maxAge: Duration? = null,
            callback: ((error: Exception?) -> Unit)? = null,
        ) {
            Log.d(TAG, "authenticateWithStepUp")
            val intent = Intent(activity, EmbeddedAuthActivity::class.java)

            val authorizeUri = AuthorizeUrlGenerator(activity).generate(
                stepUp = true,
                maxAge = maxAge,
            )
            intent.putExtra(AUTH_LAUNCHED, true)
            intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
            onAuthFinishedCallback = callback
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
        }

        fun afterAuthentication(activity: Activity) {
            val intent = Intent(activity, EmbeddedAuthActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            activity.startActivity(intent)
        }

    }
}
