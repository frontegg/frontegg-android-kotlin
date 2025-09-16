package com.frontegg.android.models

/**
 * Enum representing available social login providers.
 * 
 * @property value The string value used in API calls
 */
enum class SocialLoginProvider(val value: String) {
    GOOGLE("google"),
    GITHUB("github"),
    MICROSOFT("microsoft"),
    FACEBOOK("facebook"),
    SLACK("slack"),
    LINKEDIN("linkedin"),
    APPLE("apple");

    companion object {
        /**
         * Get SocialLoginProvider by string value
         * @param value The string value to search for
         * @return SocialLoginProvider or null if not found
         */
        fun fromValue(value: String): SocialLoginProvider? {
            return values().find { it.value == value }
        }

        /**
         * Get all available provider values as list of strings
         * @return List of all provider values
         */
        fun getAllValues(): List<String> {
            return values().map { it.value }
        }
    }
}
