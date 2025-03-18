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
import com.frontegg.android.utils.AuthorizeUrlGenerator
import kotlin.math.max
import kotlin.time.Duration

class AuthenticationActivity : Activity() {
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

        val intentLaunched = intent.extras?.getBoolean(AUTH_LAUNCHED, false) ?: false
        Log.d(TAG, "onResume | intentLaunched: $intentLaunched")

        if (intentLaunched && !customTabLaunched) {
            val url = intent.extras!!.getString(AUTHORIZE_URI)
            if (url != null) {
                startAuth(url)
            } else {
                invokeAuthFinishedCallback()
                setResult(RESULT_CANCELED)
                finish()
            }
        } else {
            val intentUrl = intent.data
            if (intentUrl == null) {
                invokeAuthFinishedCallback(CanceledByUserException())
                setResult(RESULT_CANCELED)
                finish()
                return
            }

            val code = intent.data?.getQueryParameter("code")
            if (code != null) {
                Log.d(TAG, "Got intent with oauth callback")
                FronteggAuthService.instance.isLoading.value = true

                FronteggAuthService.instance.handleHostedLoginCallback(code, null, this)
                if (storage.useChromeCustomTabs && storage.isEmbeddedMode) {
                    EmbeddedAuthActivity.afterAuthentication(this)
                } else {
                    invokeAuthFinishedCallback()
                    setResult(RESULT_OK)
                    finish()
                }
                return
            }

            Log.d(TAG, "Got intent with unknown data")
            setResult(RESULT_CANCELED)
            invokeAuthFinishedCallback()
            finish()
        }
    }

    /**
     * AuthFinishedCallback in AuthenticationActivity used only
     * when using external browser login
     */
    private fun invokeAuthFinishedCallback(exception: FronteggException? = null) {
        if (FronteggAuth.instance.isEmbeddedMode) {
            return
        }
        onAuthFinishedCallback?.invoke(exception)
        onAuthFinishedCallback = null
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
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

        fun authenticate(
            activity: Activity,
            loginHint: String? = null,
            callback: ((Exception?) -> Unit)? = null
        ) {
            val intent = Intent(activity, AuthenticationActivity::class.java)
            val authorizeUri = AuthorizeUrlGenerator().generate(loginHint = loginHint)
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
            val authorizeUri = AuthorizeUrlGenerator().generate(loginAction = mfaLoginAction)
            intent.putExtra(AUTH_LAUNCHED, true)
            intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            onAuthFinishedCallback = callback
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
        }

        fun authenticateWithStepUp(
            activity: Activity,
            maxAge: Duration? = null,
            callback: ((Exception?) -> Unit)? = null
        ) {
            val intent = Intent(activity, AuthenticationActivity::class.java)
            val authorizeUri = AuthorizeUrlGenerator().generate(
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
