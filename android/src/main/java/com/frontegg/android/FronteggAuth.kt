package com.frontegg.android

import android.app.Activity
import android.webkit.WebView
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.utils.ReadOnlyObservableValue
import kotlin.time.Duration


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
    val webLoading: ReadOnlyObservableValue<Boolean>
    val initializing: ReadOnlyObservableValue<Boolean>
    val showLoader: ReadOnlyObservableValue<Boolean>
    val refreshingToken: ReadOnlyObservableValue<Boolean>
    val isStepUpAuthorization: ReadOnlyObservableValue<Boolean>

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
    
    var webview: WebView?
    val featureFlags: com.frontegg.android.services.FeatureFlags

    /**
     * Login user. Launch a user login process. Start [EmbeddedAuthActivity] or
     * [AuthenticationActivity] depending on [isEmbeddedMode] to show the Frontegg Login Box.
     * [accessToken], [refreshToken], and [user] should be not null, and [isAuthenticated]
     * should be true after calling the [login] process is finished.
     *
     * @param activity The activity of the application.
     * @param loginHint A value that auto-fills the login field in the Frontegg LoginBox.
     * @param organization The tenant/organization alias for custom login per tenant.
     *                     When provided, users will see the customized login experience
     *                     configured for that specific tenant in the Frontegg portal.
     *                     This enables "Login per Account" functionality where each tenant
     *                     can have different branding, social logins, and login methods.
     *
     *                     **Important:** When custom login is enabled for a tenant,
     *                     `switchTenant` is not supported between accounts with custom
     *                     login boxes. Users will need to re-login when switching.
     * @param callback Called after the login process is finished.
     */
    fun login(
        activity: Activity,
        loginHint: String? = null,
        organization: String? = null,
        callback: ((Exception?) -> Unit)? = null
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
    fun directLoginAction(
        activity: Activity,
        type: String,
        data: String,
        callback: ((Exception?) -> Unit)? = null
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
     * Process queued requests when network becomes available.
     */
    fun processQueuedRequests()

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
     * **Token source:** Use tokens obtained from Frontegg identity-server APIs, for example:
     * - [POST /frontegg/identity/resources/users/v1/signUp](https://docs.frontegg.com/reference/signup) (sign-up response)
     * - Other identity-server endpoints that return refresh token and optional device token cookie.
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
     * **Token source:** Use tokens obtained from Frontegg identity-server APIs, for example:
     * - [POST /frontegg/identity/resources/users/v1/signUp](https://docs.frontegg.com/reference/signup) (sign-up response)
     * - Other identity-server endpoints that return refresh token and optional device token cookie.
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

    /**
     * Checks whether step-up authentication has been performed and is still valid.
     *
     * @param maxAge The maximum duration allowed for authentication validity. If provided, the authentication time is validated against this duration.
     * @return `true` if step-up authentication is valid, otherwise `false`.
     */
    fun isSteppedUp(
        maxAge: Duration? = null,
    ): Boolean

    /**
     * Initiates a step-up authentication process for the given activity.
     *
     * @param activity The current activity where authentication should take place.
     * @param callback A callback to be invoked after the step-up authentication process completes, providing an optional error if one occurs.
     * @param maxAge The maximum duration allowed for authentication validity.
     */
    fun stepUp(
        activity: Activity,
        maxAge: Duration? = null,
        callback: ((error: Exception?) -> Unit)? = null,
    )

    /**
     * Sets the access token and refresh token for the current session.
     *
     * @param accessToken The access token to set.
     * @param refreshToken The refresh token to set.
     */
    fun updateCredentials(accessToken: String, refreshToken: String)

    /**
     * Get session start time.
     * @return Session start time in milliseconds since epoch, or 0 if not tracked
     */
    fun getSessionStartTime(): Long

    /**
     * Get session duration.
     * @return Session duration in milliseconds, or 0 if session not started
     */
    fun getSessionDuration(): Long

    /**
     * Check if we have session data.
     * @return true if we have session start time
     */
    fun hasSessionData(): Boolean
}

