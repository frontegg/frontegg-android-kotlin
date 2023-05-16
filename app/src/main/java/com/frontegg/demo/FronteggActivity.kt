package com.frontegg.demo

import android.content.Intent
import com.frontegg.android.AbstractFronteggActivity

class FronteggActivity : AbstractFronteggActivity() {
    override fun navigateToAuthenticated() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }
}