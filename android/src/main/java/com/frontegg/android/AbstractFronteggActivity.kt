package com.frontegg.android

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.frontegg.android.utils.AuthorizeUrlGenerator
import com.frontegg.android.utils.NullableObject
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer

abstract class AbstractFronteggActivity : Activity() {

    companion object {
        private val TAG = AbstractFronteggActivity::class.java.simpleName
    }

    lateinit var webView: FronteggWebView
    var webViewUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_frontegg_login)
        overridePendingTransition(R.anim.fadein, R.anim.fadeout)

        webView = findViewById(R.id.custom_webview)

        val authorizeUrl = AuthorizeUrlGenerator()
        val url = authorizeUrl.generate()

        webViewUrl = intent.data?.toString() ?: url

        loaderLayout = findViewById(R.id.loaderView)
        val loaderView = layoutInflater.inflate(FronteggApp.getInstance().loaderId,  null)

        loaderLayout!!.addView(loaderView)


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


    private val isInitializingConsumer: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "isInitializingConsumer: ${it.value}")
        Log.d(TAG, "isInitializingConsumer: isAuth ${FronteggAuth.instance.isAuthenticated.value}")

        if (!it.value && !FronteggAuth.instance.isAuthenticated.value) {
            runOnUiThread {
                if(webViewUrl != null) {
                    webView.loadUrl(webViewUrl!!)
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        disposables.add(FronteggAuth.instance.showLoader.subscribe(this.showLoaderConsumer))
        disposables.add(FronteggAuth.instance.isAuthenticated.subscribe(this.isAuthenticatedConsumer))
        disposables.add(FronteggAuth.instance.initializing.subscribe(this.isInitializingConsumer))

    }

    override fun onPause() {
        super.onPause()
        disposables.forEach {
            it.dispose()
        }
    }


    abstract fun navigateToAuthenticated()
}