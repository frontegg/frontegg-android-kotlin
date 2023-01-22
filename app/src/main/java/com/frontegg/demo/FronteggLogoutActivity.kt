package com.frontegg.demo

import com.frontegg.android.AbstractFronteggLogoutActivity
import android.content.Intent

class FronteggLogoutActivity: AbstractFronteggLogoutActivity() {
    override fun navigateToFronteggLogin() {
        val intent = Intent(this, FronteggActivity::class.java)
        startActivity(intent)
        finish()
    }
}