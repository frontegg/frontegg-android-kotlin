package com.frontegg.android

import android.app.Activity
import com.frontegg.android.models.SocialProvider
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.utils.ReadOnlyObservableValue


/**
 * The authentication interface of Frontegg.
 * Contains all necessary properties and methods for authentication flow.
 *
 * @property accessToken is a changeable value of access token. Null if the user is unauthorized;
 * @property refreshToken is a changeable value of refresh token. Null if the user is unauthorized;
 * @property user is a changeable value of the user date. Contains all information about the user.
 *  Null if the user is unauthorized;
 * @property isAuthenticated is a changeable value. True if the user is authenticated;
 * @property isLoading is a changeable value. True if some process is running;
 * @property initializing is a changeable value. True if Frontegg SDK is initializing;
 * @property showLoader is a changeable value. True if need show loading UI;
 * @property refreshingToken is a changeable value. True if refreshing token in progress;
 *
 * @property baseUrl is the Frontegg base URL;
 * @property clientId is the Frontegg Client ID;
 * @property applicationId is the id of Frontegg application;
 * @property isMultiRegion is the flag which says if Frontegg SDK is multi-region;
 * @property regions is the list of initialized [RegionConfig];
 * @property selectedRegion is the current selected region;
 * @property isEmbeddedMode is the flag which says if Frontegg SDK in embedded mode;
 *
 */
interface FronteggAuth {
    val accessToken: ReadOnlyObservableValue<String?>
    val refreshToken: ReadOnlyObservableValue<String?>
    val user: ReadOnlyObservableValue<User?>
    val isAuthenticated: ReadOnlyObservableValue<Boolean>
    val isLoading: ReadOnlyObservableValue<Boolean>
    val initializing: ReadOnlyObservableValue<Boolean>
    val showLoader: ReadOnlyObservableValue<Boolean>
    val refreshingToken: ReadOnlyObservableValue<Boolean>

    val baseUrl: String
    val clientId: String
    val applicationId: String?
    val isMultiRegion: Boolean
    val regions: List<RegionConfig>
    val selectedRegion: RegionConfig?
    val isEmbeddedMode: Boolean

    val useAssetsLinks: Boolean
    val useChromeCustomTabs: Boolean
    val mainActivityClass: Class<*>?

    /**
     * Login user. Launch a user login process. Start [EmbeddedAuthActivity] or
     * [AuthenticationActivity] depending on [isEmbeddedMode] to show the Frontegg Login Box.
     * [accessToken], [refreshToken], and [user] should be not null, and [isAuthenticated]
     * should be true after calling the [login] process is finished.
     * @param activity is the activity of application;
     * @param loginHint is a value that auto filled to login field at Frontegg LoginBox.
     */
    fun login(
        activity: Activity,
        loginHint: String? = null,
        callback: (() -> Unit)? = null
    )

    /**
     * Logout user. Clear data about the user. Could make a network request.
     * [accessToken], [refreshToken], and [user] should be null, and [isAuthenticated]
     * should be false after calling the [logout] method and [callback] was triggered.
     * @param callback call after the logout process is finished.
     */
    fun logout(
        callback: () -> Unit = {},
    )

    /**
     * Direct Login Action process. Support only on Embedded Mode([EmbeddedAuthActivity] is enabled).
     * @param activity is the activity of application;
     * @param type is the type of direct login. Can be "direct", "social-login", or "custom-social-login";
     * @param data is a data of direct login. For "direct" [type] it can be any URL string.
     * For "social-login" [type] must be one of google, linkedin, facebook, github, apple, etc.
     * For "custom-social-login" [type] must be configured UUID;
     * @param callback call after the Direct Login Action process is finished.
     */
    @Deprecated(
        message = "Use directLogin(url), socialLogin(provider), or customSocialLogin(id) instead.",
        replaceWith = ReplaceWith("directLogin(url) or socialLogin(provider) or customSocialLogin(id)")
    )
    fun directLoginAction(
        activity: Activity,
        type: String,
        data: String,
        callback: (() -> Unit)? = null
    )

    /**
     * Initiates a direct login using a provided URL.
     *
     * @param activity is the activity of application;
     * @param url The URL for the direct login.
     * @param callback Callback function to be executed after the login process is completed.
     */
    fun directLogin(
        activity: Activity,
        url: String,
        callback: (() -> Unit)? = null
    )

    /**
     * Initiates a social login using the specified social provider.
     *
     * @param activity is the activity of application;
     * @param provider The social provider for authentication (e.g., Google, Facebook, LinkedIn).
     * @param callback Callback function to be executed after the login process is completed.
     */
    fun socialLogin(
        activity: Activity,
        provider: SocialProvider,
        callback: (() -> Unit)? = null
    )

    /**
     * Initiates a custom social login using a unique identifier.
     *
     * @param activity is the activity of application;
     * @param id The unique identifier for the custom social login.
     * @param callback Callback function to be executed after the login process is completed.
     */
    fun customSocialLogin(
        activity: Activity,
        id: String,
        callback: (() -> Unit)? = null
    )

    /**
     * Switches user tenant.
     * @param tenantId is available user tenants. Can extract them from [User].tenants.tenantId;
     * @param callback call after the tenant switching process is finished.
     */
    fun switchTenant(
        tenantId: String,
        callback: (Boolean) -> Unit = {},
    )

    /**
     * Refresh token if needed.
     * @return true if taken was successfully refreshed. False otherwise.
     */
    fun refreshTokenIfNeeded(): Boolean

    /**
     * Login with passkeys
     * @param activity is the activity of application;
     * @param callback call after the registration is finished or was intercepted by Exception.
     * [error] not null if some Exception happened during logging.
     */
    fun loginWithPasskeys(
        activity: Activity,
        callback: ((error: Exception?) -> Unit)? = null,
    )

    /**
     * Register passkeys.
     * @param activity is the activity of application;
     * @param callback call after the registration is finished or was intercepted by Exception.
     * [error] not null if some Exception happened during registration.
     */
    fun registerPasskeys(
        activity: Activity,
        callback: ((error: Exception?) -> Unit)? = null,
    )

    /**
     * Requests silent authorization using a refresh token and an optional device token.
     * This function runs asynchronously using a coroutine.
     *
     * @param refreshToken The refresh token used for authentication.
     * @param deviceTokenCookie Optional device token for additional authentication.
     * @return A [User] object if authentication succeeds.
     * @throws FailedToAuthenticateException If authentication fails.
     */
    suspend fun requestAuthorizeAsync(
        refreshToken: String,
        deviceTokenCookie: String? = null
    ): User

    /**
     * Initiates an asynchronous authorization request using a refresh token.
     * Calls a callback function with the authentication result.
     *
     * @param refreshToken The refresh token used for authentication.
     * @param deviceTokenCookie Optional device token for additional authentication.
     * @param callback A callback function that returns a [Result] object containing
     *                 either a successful [User] or an exception if authentication fails.
     */
    fun requestAuthorize(
        refreshToken: String,
        deviceTokenCookie: String? = null,
        callback: (Result<User>) -> Unit
    )


    companion object {

        /**
         * Instance of [FronteggAuth].
         */
        val instance: FronteggAuth
            get() {
                return FronteggApp.getInstance().auth
            }
    }
}

