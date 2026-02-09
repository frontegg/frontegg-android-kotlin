package com.frontegg.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.services.FronteggState
import com.frontegg.android.services.StepUpAuthenticator
import com.frontegg.android.ui.FronteggBaseActivity
import com.frontegg.android.utils.AuthorizeUrlGenerator
import kotlin.time.Duration

class AuthenticationActivity : FronteggBaseActivity() {
    private val storage = FronteggInnerStorage()
    private var customTabLaunched = false
    private fun startAuth(url: String) {
        val builder = CustomTabsIntent.Builder()
        builder.setShowTitle(true)
        builder.setToolbarCornerRadiusDp(14)
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(this, Uri.parse(url))
        customTabLaunched = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customTabLaunched = savedInstanceState?.getBoolean(CUSTOM_TAB_LAUNCHED, false) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(CUSTOM_TAB_LAUNCHED, customTabLaunched)
        super.onSaveInstanceState(outState)

    }

    override fun onResume() {
        super.onResume()

        StepUpAuthenticator.resumeAuthenticationActivity()

        val intentLaunched = intent.extras?.getBoolean(AUTH_LAUNCHED, false) ?: false
        Log.d(TAG, "onResume | intentLaunched: $intentLaunched")

        if (intentLaunched && !customTabLaunched) {
            val url = intent.extras!!.getString(AUTHORIZE_URI)
            if (url != null) {
                startAuth(url)
            } else {
                safeFinishActivity(RESULT_CANCELED)
            }
        } else {
            val intentUrl = intent.data
            if (intentUrl == null) {
                safeFinishActivity(RESULT_CANCELED, CanceledByUserException())
                return
            }

            val code = intent.data?.getQueryParameter("code")
            if (code != null) {
                Log.d(TAG, "Got intent with oauth callback")
                FronteggState.isLoading.value = true

                // Check if this is a social login callback
                val intentUrlString = intent.data?.toString()
                if (intentUrlString != null && intentUrlString.contains("/oauth/account/redirect/android/")) {
                    Log.d(TAG, "Detected social login callback")
                    val redirectUrl = (fronteggAuth as FronteggAuthService).handleSocialLoginCallback(intentUrlString)
                    if (redirectUrl != null && storage.isEmbeddedMode) {
                        // Load the redirect URL in EmbeddedAuthActivity WebView
                        val embeddedIntent = Intent(this, EmbeddedAuthActivity::class.java).apply {
                            data = android.net.Uri.parse(redirectUrl)
                        }
                        startActivity(embeddedIntent)
                        finish()
                    } else {
                        safeFinishActivity(RESULT_OK)
                    }
                } else {
                    // Regular OAuth callback
                    (fronteggAuth as FronteggAuthService).handleHostedLoginCallback(code, null, this)
                    if (storage.useChromeCustomTabs && storage.isEmbeddedMode) {
                        EmbeddedAuthActivity.afterAuthentication(this)
                    } else {
                        safeFinishActivity(RESULT_OK)
                    }
                }
                return
            }

            if (!customTabLaunched) {
                startAuth(intentUrl.toString())
                customTabLaunched = true
                return
            }

            Log.d(TAG, "Got intent with unknown data (no code)")
            safeFinishActivity(RESULT_CANCELED)
        }
    }

    /**
     * AuthFinishedCallback in AuthenticationActivity used only
     * when using external browser login
     */
    private fun invokeAuthFinishedCallback(exception: FronteggException? = null) {
        if (fronteggAuth.isEmbeddedMode && !FronteggState.isStepUpAuthorization.value) {
            return
        }
        onAuthFinishedCallback?.invoke(exception)
        onAuthFinishedCallback = null
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    fun safeFinishActivity(resultCode: Int, exception: FronteggException? = null) {
        invokeAuthFinishedCallback(exception)
        setResult(resultCode)
        if (isTaskRoot) {
            val intent = intent
            // 2) …and we didn’t come here via the normal launcher (ACTION_MAIN + CATEGORY_LAUNCHER)
            val isLauncher = intent.action == Intent.ACTION_MAIN &&
                    intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            if (!isLauncher) {
                // 3) Grab the real “main launcher” Intent for this package
                val launchIntent = packageManager
                    .getLaunchIntentForPackage(packageName)
                    ?.apply {
                        // wipe any existing history/task and start fresh
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                    }
                // 4) Fire it and kill yourself
                launchIntent?.let { startActivity(it) }
                finish()
                return
            }
        }
        finish()
    }


    @SuppressLint("QueryPermissionsNeeded")
    @Suppress("DEPRECATION")
    fun getMainActivity() {
        val pm: PackageManager = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        mainIntent.setPackage(applicationContext.packageName)
        val appList = pm.queryIntentActivities(mainIntent, 0)
        for (resolveInfo in appList) {
            val packageName = resolveInfo.activityInfo.packageName
            val activityName = resolveInfo.activityInfo.name
            Log.d("ActivityNames", "Package Name: $packageName Activity Name: $activityName")
        }
    }

    companion object {
        const val OAUTH_LOGIN_REQUEST = 100001
        const val AUTHORIZE_URI = "com.frontegg.android.AUTHORIZE_URI"
        private const val AUTH_LAUNCHED = "com.frontegg.android.AUTH_LAUNCHED"
        private const val CUSTOM_TAB_LAUNCHED = "com.frontegg.android.CUSTOM_TAB_LAUNCHED"
        private val TAG = AuthenticationActivity::class.java.simpleName
        var onAuthFinishedCallback: ((Exception?) -> Unit)? = null // Store callback

        /**
         * Authenticates the user using Chrome Custom Tabs or system browser.
         *
         * @param activity The activity launching the authentication.
         * @param loginHint Optional email hint to pre-fill the login form.
         * @param organization Optional tenant/organization alias for custom login per tenant.
         *                     When provided, users will see the customized login experience
         *                     configured for that specific tenant in the Frontegg portal.
         * @param callback Called when authentication completes or fails.
         */
        fun authenticate(
            activity: Activity,
            loginHint: String? = null,
            organization: String? = null,
            callback: ((Exception?) -> Unit)? = null
        ) {
            val intent = Intent(activity, AuthenticationActivity::class.java)
            val authorizeUri = AuthorizeUrlGenerator(activity).generate(
                loginHint = loginHint,
                organization = organization
            )
            intent.putExtra(AUTH_LAUNCHED, true)
            intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            onAuthFinishedCallback = callback
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
        }

        fun authenticateWithMultiFactor(
            activity: Activity,
            mfaLoginAction: String? = null,
            callback: ((Exception?) -> Unit)? = null
        ) {
            val intent = Intent(activity, AuthenticationActivity::class.java)
            val authorizeUri =
                AuthorizeUrlGenerator(activity).generate(loginAction = mfaLoginAction)
            intent.putExtra(AUTH_LAUNCHED, true)
            intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            onAuthFinishedCallback = callback
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
        }

        fun authenticateWithStepUp(
            activity: Activity,
            maxAge: Duration? = null,
            callback: ((Exception?) -> Unit)? = null,
        ) {
            val intent = Intent(activity, AuthenticationActivity::class.java)
            val authorizeUri = AuthorizeUrlGenerator(activity).generate(
                stepUp = true,
                maxAge = maxAge
            )
            intent.putExtra(AUTH_LAUNCHED, true)
            intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            onAuthFinishedCallback = callback
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
        }
    }
}
