package com.frontegg.android.models

import com.google.gson.annotations.SerializedName

/**
 * Model for social login post-login request
 */
data class SocialLoginPostLoginRequest(
    @SerializedName("code")
    val code: String? = null,
    @SerializedName("id_token")
    val idToken: String? = null,
    @SerializedName("redirectUri")
    val redirectUri: String? = null,
    @SerializedName("code_verifier")
    val codeVerifier: String? = null,
    @SerializedName("code_verifier_pkce")
    val codeVerifierPkce: String? = null,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("metadata")
    val metadata: Map<String, Any>? = null,
    @SerializedName("invitationToken")
    val invitationToken: String? = null
)

