package com.frontegg.android.models

/**
 * Social login provider enumeration
 * Matches iOS SocialLoginProvider implementation
 */
enum class SocialLoginProvider(val value: String) {
    FACEBOOK("facebook"),
    GOOGLE("google"),
    MICROSOFT("microsoft"),
    GITHUB("github"),
    SLACK("slack"),
    APPLE("apple"),
    LINKEDIN("linkedin");

    companion object {
        fun fromString(value: String): SocialLoginProvider? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Social login action enumeration
 * Matches iOS SocialLoginAction implementation
 */
enum class SocialLoginAction(val value: String) {
    LOGIN("login"),
    SIGNUP("signUp");

    companion object {
        fun fromString(value: String): SocialLoginAction? {
            return values().find { it.value == value }
        }
    }
}
