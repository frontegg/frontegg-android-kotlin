package com.frontegg.android

import android.app.Activity
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

    /**
     * Login user. Launch a user login process. Start [EmbeddedAuthActivity] or
     * [AuthenticationActivity] depending on [isEmbeddedMode] to show the Frontegg Login Box.
     * [accessToken], [refreshToken], and [user] should be not null, and [isAuthenticated]
     * should be true after calling the [login] process is finished.
     * @param activity is the activity of application;
     * @param loginHint is a value that auto filled to login field at Frontegg LoginBox.
     */
    fun login(activity: Activity, loginHint: String? = null)

    /**
     * Logout user. Clear data about the user. Could make a network request.
     * [accessToken], [refreshToken], and [user] should be null, and [isAuthenticated]
     * should be false after calling the [logout] method and [callback] was triggered.
     * @param callback call after the logout process is finished.
     */
    fun logout(callback: () -> Unit = {})

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
        callback: (() -> Unit)? = null
    )

    /**
     * Switches user tenant.
     * @param tenantId is available user tenants. Can extract them from [User].tenants.tenantId;
     * @param callback call after the tenant switching process is finished.
     */
    fun switchTenant(tenantId: String, callback: (Boolean) -> Unit = {})

    /**
     * Refresh token if needed.
     * @return true if taken was successfully refreshed. False otherwise.
     */
    fun refreshTokenIfNeeded(): Boolean

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

