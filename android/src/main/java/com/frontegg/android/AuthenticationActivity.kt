package com.frontegg.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.frontegg.android.utils.AuthorizeUrlGenerator

class AuthenticationActivity : Activity() {

    var customTabLaunched = false
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
                setResult(RESULT_CANCELED)
                finish()
            }
        } else {
            val intentUrl = intent.data
            if(intentUrl == null){
                setResult(RESULT_CANCELED)
                finish()
                return
            }

            val code = intent.data?.getQueryParameter("code")
            if (code != null) {
                Log.d(TAG, "Got intent with oauth callback")
                FronteggAuth.instance.handleHostedLoginCallback(code)
                setResult(RESULT_OK)
                finish()
                return
            }

            /**
             * due to lake to support from android chrome tab
             * we are sharing magic link cookie with the chrome
             * instance to give the ability to login via magic link
             * in the native chrome browser, then the hosted login
             * will redirect the user directly to the application
             * with the exchange code to finalize the authorization process
             */
//            val path = intentUrl.path
//            if(path != null && path.startsWith("/oauth/account/login/magic-link?token=")){
//                Log.d(TAG, "Got intent with magic link")
//                var url = intentUrl.toString()
//                url = url.replace("magic-link?token=", "magic-link?chromeTab=true&token=")
//                startAuth(url)
//                return
//            }

            Log.d(TAG, "Got intent with unknown data")
            setResult(RESULT_CANCELED)
            finish()
        }
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

        fun authenticateUsingBrowser(activity: Activity) {
            val intent = Intent(activity, AuthenticationActivity::class.java)
            val authorizeUri = AuthorizeUrlGenerator().generate()
            intent.putExtra(AUTH_LAUNCHED, true)
            intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
        }
    }
}
