package com.frontegg.android.models

class WebAuthnRegistrationRequest(
    public var cookie: String,
    public var jsonChallenge: String
)