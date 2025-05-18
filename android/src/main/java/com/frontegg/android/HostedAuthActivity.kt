package com.frontegg.android

import android.os.Bundle
import com.frontegg.android.ui.FlutterInitHelper
import com.google.androidbrowserhelper.trusted.LauncherActivity

class HostedAuthActivity : LauncherActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FlutterInitHelper.initializeFlutterIfPresent(this)
    }
}