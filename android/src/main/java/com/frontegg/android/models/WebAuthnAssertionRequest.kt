package com.frontegg.android.models

class WebAuthnAssertionRequest(
    public var cookie: String,
    public var jsonChallenge: String
)