package com.frontegg.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.frontegg.android.embedded.FronteggNativeBridge
import com.frontegg.android.embedded.FronteggWebView
import com.frontegg.android.services.FronteggAuthService
import com.frontegg.android.services.FronteggInnerStorage
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.NullableObject
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer

class EmbeddedAuthActivity : Activity() {
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

        webView = findViewById(R.id.custom_webview)
        loaderLayout = findViewById(R.id.loaderView)


        consumeIntent(intent)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(DIRECT_LOGIN_ACTION_LAUNCHED, this.directLoginLaunched)
        outState.putBoolean(DIRECT_LOGIN_ACTION_LAUNCHED_DONE, this.directLoginLaunchedDone)
    }

    private fun consumeIntent(intent: Intent) {

        val intentLaunched =
            intent.extras?.getBoolean(AUTH_LAUNCHED, false) ?: false

        this.directLoginLaunched =
            intent.extras?.getBoolean(DIRECT_LOGIN_ACTION_LAUNCHED, false) ?: false
        this.directLoginLaunchedDone =
            intent.extras?.getBoolean(DIRECT_LOGIN_ACTION_LAUNCHED_DONE, false) ?: false


        if (directLoginLaunchedDone || intent.extras == null) {
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

            val authorizeUri = AuthorizeUrlGenerator().generate()
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


        if (FronteggAuth.instance.isStepUpAuthorization.value ||
            (!FronteggAuth.instance.initializing.value
                    && !FronteggAuth.instance.isAuthenticated.value)
        ) {
            webView.loadUrl(webViewUrl!!)
            webViewUrl = null
        }
    }

    private val disposables: ArrayList<Disposable> = arrayListOf()
    private var loaderLayout: LinearLayout? = null

    private val showLoaderConsumer: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "showLoaderConsumer: ${it.value}")
        runOnUiThread {
            if (FronteggAuth.instance.isStepUpAuthorization.value) {
                loaderLayout?.visibility = if (it.value) View.VISIBLE else View.GONE
            } else {
                loaderLayout?.visibility =
                    if (it.value || FronteggAuth.instance.isAuthenticated.value) View.VISIBLE else View.GONE
            }
        }
    }
    private val isAuthenticatedConsumer: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "isAuthenticatedConsumer: ${it.value}")
        if (it.value) {
            runOnUiThread {
                navigateToAuthenticated()
                onAuthFinishedCallback?.invoke()
                onAuthFinishedCallback = null
            }
        }
    }

    private val isStepUpAuthorization: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "isAuthenticatedConsumer: ${it.value}")
        if (!it.value) {
            runOnUiThread {
                navigateToAuthenticated()
                onAuthFinishedCallback?.invoke()
                onAuthFinishedCallback = null
            }
        }
    }

    private val isReAuthorization: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "isAuthenticatedConsumer: ${it.value}")
        if (!it.value) {
            runOnUiThread {
                navigateToAuthenticated()
                onAuthFinishedCallback?.invoke()
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

        disposables.add(FronteggAuth.instance.showLoader.subscribe(this.showLoaderConsumer))

        if (!FronteggAuth.instance.isStepUpAuthorization.value) {
            disposables.add(FronteggAuth.instance.isAuthenticated.subscribe(this.isAuthenticatedConsumer))
        } else if (FronteggAuth.instance.isStepUpAuthorization.value) {
            disposables.add(FronteggAuth.instance.isStepUpAuthorization.subscribe(this.isStepUpAuthorization))
        } else if (FronteggAuth.instance.isReAuthorization.value) {
            disposables.add(FronteggAuth.instance.isReAuthorization.subscribe(this.isReAuthorization))
        }

        if (directLoginLaunchedDone) {
            onAuthFinishedCallback?.invoke()
            onAuthFinishedCallback = null
            FronteggAuthService.instance.isLoading.value = false
            FronteggAuthService.instance.showLoader.value = false
            setResult(RESULT_OK)
            finish()
            return
        }

        if (directLoginLaunched) {
            directLoginLaunchedDone = true
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
        onAuthFinishedCallback?.invoke()
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
        var onAuthFinishedCallback: (() -> Unit)? = null // Store callback

        @JvmStatic
        fun authenticate(
            activity: Activity,
            loginHint: String? = null,
            callback: (() -> Unit)? = null
        ) {
            val intent = Intent(activity, EmbeddedAuthActivity::class.java)

            val authorizeUri = AuthorizeUrlGenerator().generate(loginHint = loginHint)
            intent.putExtra(AUTH_LAUNCHED, true)
            intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
            onAuthFinishedCallback = callback
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
        }

        fun directLoginAction(
            activity: Activity,
            type: String,
            data: String,
            callback: (() -> Unit)? = null
        ) {
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
            callback: (() -> Unit)? = null
        ) {
            val intent = Intent(activity, EmbeddedAuthActivity::class.java)

            val authorizeUri = AuthorizeUrlGenerator().generate(loginAction = mfaLoginAction)
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
