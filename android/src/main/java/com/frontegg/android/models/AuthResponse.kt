package com.frontegg.android.models

class AuthResponse {
    public lateinit var token_type: String
    public lateinit var refresh_token: String
    public lateinit var access_token: String
    public lateinit var id_token: String
}