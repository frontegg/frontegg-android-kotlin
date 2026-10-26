# Authentication and usage

Frontegg offers multiple authentication flows to enhance your Android app’s user experience. Whether you're aiming for a native experience, seamless social login, or modern passwordless security with passkeys, the SDK provides flexible and easy-to-integrate options:

* **Embedded webview**: A customizable in-app webview login experience, which is enabled by default.
* **Chrome Custom Tabs**: A secure and seamless way to handle social login using the device's default browser, offering a native-like user experience.
* **Passkeys**: A modern, passwordless login method using biometric authentication and WebAuthn standards, supported on Android devices with compatible hardware.

## Chrome custom tabs

To use Chrome Custom Tabs for social login instead of the default Embedded WebView:

1. Update `build.gradle`  adding `FRONTEGG_USE_CHROME_CUSTOM_TABS` to buildConfigField:

```gradle
android {
    defaultConfig {
        buildConfigField "boolean", "FRONTEGG_USE_CHROME_CUSTOM_TABS", "true"
    }
}
```

2. Update the manifest by disabling the embedded activity and enable the hosted Chrome tab activity by modifying your `AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools">
    <application>

        <!-- Disable Embedded WebView Auth -->
        <activity
            android:name="com.frontegg.android.EmbeddedAuthActivity"
            android:enabled="false"
            tools:replace="android:enabled" />

        <!-- Enable Hosted Auth (Chrome Custom Tabs) -->
        <activity
            android:name="com.frontegg.android.HostedAuthActivity"
            android:enabled="true"
            tools:replace="android:enabled" />

    </application>
</manifest>
```

## Login with Frontegg

To log in with Frontegg, follow these steps:

1. Call the `requireContext().fronteggAuth.login()` method.
2. Pass the current activity context (e.g., `requireActivity()`).
3. The login method will open the Frontegg hosted login page.
4. After successful authentication, it will return the user data.

```kotlin

import com.frontegg.android.FronteggAuth

class FirstFragment : Fragment() {
    // ...

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.loginButton.setOnClickListener {
            requireContext().fronteggAuth.login(requireActivity())
        }
    }
    // ...
}

```


## Switch account (tenant)

To switch user tenants, call the `requireContext().fronteggAuth.switchTenant()` method with the desired tenant ID. Make sure to retrieve the list of available tenant IDs from the current user session.

```kotlin

import com.frontegg.android.FronteggAuth

class FirstFragment : Fragment() {
    // ...

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val tenantIds = requireContext().fronteggAuth.user.value?.tenantIds ?: listOf()

        /**
         *  pick one from `tenantIds` list:
         */
        val tenantToSwitchTo = tenantIds[0]

        binding.switchTenant.setOnClickListener {
            requireContext().fronteggAuth.switchTenant(tenantToSwitchTo)
        }
    }
    // ...
}

```

## Observe authentication state

Every state value on `fronteggAuth` (`isAuthenticated`, `user`, `accessToken`, `isLoading`,
`showLoader`, and the rest) exposes a Kotlin `StateFlow` as `stateFlow`. Collect it from a
lifecycle-aware coroutine. `lifecycleScope` and `repeatOnLifecycle` need `androidx.lifecycle:lifecycle-runtime-ktx`:

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        fronteggAuth.isAuthenticated.stateFlow.collect { isAuthenticated ->
            showHome(isAuthenticated)
        }
    }
}
```

or read it in Compose:

```kotlin
val user by fronteggAuth.user.stateFlow.collectAsState()
```

The `com.frontegg.sdk:android-compose` artifact adds a shorter `fronteggAuth.user.collectAsState()`
and prebuilt components; see [Jetpack Compose](advanced.md#jetpack-compose).

> **RxJava is deprecated.** `subscribe { }` and `observable` still work and still emit every value
> that is set, but they will be removed in the next major version. `stateFlow`, like any
> `StateFlow`, skips a value equal to the current one. When you stop using `subscribe`, you can
> drop the `io.reactivex.rxjava3:rxkotlin` dependency from your app.

## Get a valid access token

Before calling your API, ask for the access token with `getOrRefreshAccessToken()` instead of reading
`accessToken` directly. After the app has been in the background, the cached token may already be
expired, which shows up as a `401` on the first request after resume.

`getOrRefreshAccessToken()` waits up to 20 seconds for an in-flight initialization, login or refresh,
returns the current token when it is valid for more than 15 seconds, and refreshes it otherwise:

```kotlin
lifecycleScope.launch {
    try {
        val accessToken = fronteggAuth.getOrRefreshAccessToken()
        if (accessToken == null) {
            showLogin()
        } else {
            callApi(accessToken)
        }
    } catch (e: Exception) {
        showRetry(e)
    }
}
```

It returns `null` when there is no session, when no region is selected yet in a multi-region app, or when
the server rejected the refresh token, in which case the session is cleared and the user has to log in again.
When the device is offline and the token needs a refresh, it does not attempt the refresh or wait for the network.
With [offline mode](advanced.md#offline-mode-ios-parity) enabled, being offline or a network failure returns the
cached token, which may be expired. Otherwise it throws: `IOException` right away when the device is offline, or for a
network failure or a server error, and `FailedToAuthenticateException` when in-flight work did not settle in time.

From Java, or without coroutines, use the callback variant. The callback runs on the main thread:

```kotlin
fronteggAuth.getOrRefreshAccessToken { accessToken, error ->
    when {
        error != null -> showRetry(error)
        accessToken == null -> showLogin()
        else -> callApi(accessToken)
    }
}
```

## Logout user

To log out the user, simply call the `requireContext().fronteggAuth.logout()` method. This will clear all user data from the device.

```kotlin

import com.frontegg.android.FronteggAuth

class FirstFragment : Fragment() {
    // ...

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.logoutButton.setOnClickListener {
            requireContext().fronteggAuth.logout()
        }
    }
    // ...
}

```

## Using `DefaultLoader`

You can customize the loader view by setting a custom `LoaderProvider`. This allows you to control the appearance of the loading indicator during authentication flows.

Here's an example of how to use `DefaultLoader` with a red `ProgressBar`:

```kotlin
DefaultLoader.setLoaderProvider {
    val progressBar = ProgressBar(it)
    val colorStateList = ColorStateList.valueOf(Color.RED)
    progressBar.indeterminateTintList = colorStateList
    progressBar
}
```

Once the `LoaderProvider` is set, our SDK will automatically use the customized loader when needed.