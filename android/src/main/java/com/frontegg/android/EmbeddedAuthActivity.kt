package com.frontegg.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.frontegg.android.embedded.FronteggWebView
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.NullableObject
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer

class EmbeddedAuthActivity : Activity() {

    lateinit var webView: FronteggWebView
    var webViewUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_embedded_auth)
        overridePendingTransition(R.anim.fadein, R.anim.fadeout)

        webView = findViewById(R.id.custom_webview)
        loaderLayout = findViewById(R.id.loaderView)

        val intentLaunched =
            intent.extras?.getBoolean(AUTH_LAUNCHED, false) ?: false

        if (intentLaunched) {
            val url = intent.extras!!.getString(AUTHORIZE_URI)
            if (url == null) {
                setResult(RESULT_CANCELED)
                finish()
                return
            }
            webViewUrl = url
        } else {
            val intentUrl = intent.data
            if (intentUrl == null) {
                setResult(RESULT_CANCELED)
                finish()
                return
            }

            webViewUrl = intentUrl.toString()
        }


        if (!FronteggAuth.instance.initializing.value
            && !FronteggAuth.instance.isAuthenticated.value
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
            loaderLayout?.visibility = if (it.value) View.VISIBLE else View.GONE
        }
    }
    private val isAuthenticatedConsumer: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "isAuthenticatedConsumer: ${it.value}")
        if (it.value) {
            runOnUiThread {
                navigateToAuthenticated()
            }
        }
    }

    private fun navigateToAuthenticated() {
        setResult(RESULT_OK)
        finish()
    }

    override fun onResume() {
        super.onResume()
        disposables.add(FronteggAuth.instance.showLoader.subscribe(this.showLoaderConsumer))
        disposables.add(FronteggAuth.instance.isAuthenticated.subscribe(this.isAuthenticatedConsumer))

    }

    override fun onPause() {
        super.onPause()
        disposables.forEach {
            it.dispose()
        }
    }


    companion object {
        const val OAUTH_LOGIN_REQUEST = 100001
        const val AUTHORIZE_URI = "com.frontegg.android.AUTHORIZE_URI"
        private const val AUTH_LAUNCHED = "com.frontegg.android.AUTH_LAUNCHED"
        private val TAG = EmbeddedAuthActivity::class.java.simpleName

        fun authenticate(activity: Activity) {
            val intent = Intent(activity, EmbeddedAuthActivity::class.java)

            val authorizeUri = AuthorizeUrlGenerator().generate()
            intent.putExtra(AUTH_LAUNCHED, true)
            intent.putExtra(AUTHORIZE_URI, authorizeUri.first)
            activity.startActivityForResult(intent, OAUTH_LOGIN_REQUEST)
        }

    }
}
