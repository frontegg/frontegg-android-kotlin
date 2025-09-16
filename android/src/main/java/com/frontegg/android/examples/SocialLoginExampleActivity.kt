package com.frontegg.android.examples

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import com.frontegg.android.fronteggAuth
import com.frontegg.android.models.SocialLoginProvider

/**
 * Example activity demonstrating how to use the new social login functionality
 */
class SocialLoginExampleActivity : Activity() {
    
    companion object {
        private const val TAG = "SocialLoginExample"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create a simple layout programmatically
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        
        // Google login button
        val googleButton = Button(this).apply {
            text = "Login with Google"
            setOnClickListener {
                loginWithGoogle()
            }
        }
        
        // Facebook login button
        val facebookButton = Button(this).apply {
            text = "Login with Facebook"
            setOnClickListener {
                loginWithFacebook()
            }
        }
        
        // GitHub login button
        val githubButton = Button(this).apply {
            text = "Login with GitHub"
            setOnClickListener {
                loginWithGitHub()
            }
        }
        
        // Apple login button
        val appleButton = Button(this).apply {
            text = "Login with Apple"
            setOnClickListener {
                loginWithApple()
            }
        }
        
        // Add buttons to layout
        layout.addView(googleButton)
        layout.addView(facebookButton)
        layout.addView(githubButton)
        layout.addView(appleButton)
        
        setContentView(layout)
    }
    
    private fun loginWithGoogle() {
        Log.d(TAG, "Starting Google login")
        fronteggAuth.loginWithSocialProvider(
            activity = this,
            provider = SocialLoginProvider.GOOGLE,
            callback = { error ->
                if (error == null) {
                    Log.d(TAG, "Google login successful")
                    Toast.makeText(this, "Google login successful!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "Google login failed: ${error.message}")
                    Toast.makeText(this, "Google login failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
    
    private fun loginWithFacebook() {
        Log.d(TAG, "Starting Facebook login")
        fronteggAuth.loginWithSocialProvider(
            activity = this,
            provider = SocialLoginProvider.FACEBOOK,
            callback = { error ->
                if (error == null) {
                    Log.d(TAG, "Facebook login successful")
                    Toast.makeText(this, "Facebook login successful!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "Facebook login failed: ${error.message}")
                    Toast.makeText(this, "Facebook login failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
    
    private fun loginWithGitHub() {
        Log.d(TAG, "Starting GitHub login")
        fronteggAuth.loginWithSocialProvider(
            activity = this,
            provider = SocialLoginProvider.GITHUB,
            callback = { error ->
                if (error == null) {
                    Log.d(TAG, "GitHub login successful")
                    Toast.makeText(this, "GitHub login successful!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "GitHub login failed: ${error.message}")
                    Toast.makeText(this, "GitHub login failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
    
    private fun loginWithApple() {
        Log.d(TAG, "Starting Apple login")
        fronteggAuth.loginWithSocialProvider(
            activity = this,
            provider = SocialLoginProvider.APPLE,
            callback = { error ->
                if (error == null) {
                    Log.d(TAG, "Apple login successful")
                    Toast.makeText(this, "Apple login successful!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "Apple login failed: ${error.message}")
                    Toast.makeText(this, "Apple login failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
