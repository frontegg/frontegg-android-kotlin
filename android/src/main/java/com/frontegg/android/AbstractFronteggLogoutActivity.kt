package com.frontegg.android

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import com.frontegg.android.FronteggApp
import com.frontegg.android.FronteggAuth
import com.frontegg.android.R

abstract class AbstractFronteggLogoutActivity : Activity() {

    companion object {
        private val TAG = AbstractFronteggLogoutActivity::class.java.simpleName
    }
    private var loaderLayout: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_frontegg_logout)

        loaderLayout = findViewById(R.id.loaderView)
        val loaderView = layoutInflater.inflate(FronteggApp.getInstance().loaderId, null)

        loaderLayout!!.addView(loaderView)

    }

    override fun onResume() {
        super.onResume()
        FronteggAuth.instance.logout(this) {
            navigateToFronteggLogin()
        }
    }

    abstract fun navigateToFronteggLogin()
}