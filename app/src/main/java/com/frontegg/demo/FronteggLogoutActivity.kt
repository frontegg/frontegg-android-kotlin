package com.frontegg.demo

import android.content.Intent
import com.frontegg.android.AbstractFronteggLogoutActivity

class FronteggLogoutActivity : AbstractFronteggLogoutActivity() {
    override fun navigateToFronteggLogin() {
        val intent = Intent(this, FronteggActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }
}