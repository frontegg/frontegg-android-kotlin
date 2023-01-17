package com.frontegg.demo

import android.content.Intent
import com.frontegg.android.AbstractFronteggLoginPage

class FronteggLoginPage: AbstractFronteggLoginPage() {
    override fun navigateToAuthenticated() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}