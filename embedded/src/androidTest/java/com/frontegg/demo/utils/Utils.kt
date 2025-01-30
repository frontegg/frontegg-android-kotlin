package com.frontegg.demo.utils

import android.os.SystemClock
import androidx.test.uiautomator.By

fun delay(ms: Long = 1_000) = SystemClock.sleep(ms)

fun UiTestInstrumentation.tapLoginButton() {
    clickByText("LOGIN")

    waitForView(By.text("Sign-in"), timeout = 15_000)
        ?: throw Exception("WebView was not loaded")
}

fun UiTestInstrumentation.logout() {
    clickByText("LOGOUT")

    waitForView(By.text("Not authenticated"))
        ?: throw Exception("Logout exception")
}