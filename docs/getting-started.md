# Getting started with Frontegg Android SDK

Welcome to the Frontegg Android Kotlin SDK! Easily integrate Frontegg's out-of-the-box authentication and user management functionalities into your Android applications for a seamless and secure user experience.

The Frontegg Android SDK can be used in two ways:

1. With the hosted Frontegg login that will be called through a webview, enabling all login methods supported on the login box
2. By directly using Frontegg APIs from your custom UI, with available methods

The SDK automatically handles token refresh behind the scenes, ensuring your users maintain authenticated sessions without manual intervention.

## Supported languages
- Java: The minimum supported Java version is 8 (Java 1.8).
- Kotlin: The minimum supported JVM target is 1.8.

## Supported platforms

- Android: The minimum supported SDK version is 26 (Android 8.0).

## Prepare your Frontegg environment

- Navigate to Frontegg Portal [ENVIRONMENT] → `Keys & domains`
- Copy your Frontegg domain from the Frontegg domain section.
- Navigate to [ENVIRONMENT] → Authentication → Login method
- Make sure hosted login is toggled on.
- Add the following redirect URLs:
    - `{{ANDROID_PACKAGE_NAME}}://{{FRONTEGG_BASE_URL}}/android/oauth/callback`
    - `https://{{FRONTEGG_BASE_URL}}/oauth/account/redirect/android/{{ANDROID_PACKAGE_NAME}}`
    - `{{FRONTEGG_BASE_URL}}/oauth/authorize`

- Replace `ANDROID_PACKAGE_NAME` with your application identifier
- Replace `FRONTEGG_BASE_URL` with your Frontegg domain, i.e `app-xxxx.frontegg.com` or your custom domain.

> [!WARNING] 
>
> On every step, if you have a [custom domain](https://developers.frontegg.com/guides/env-settings/custom-domain), replace the `[frontegg-domain]` and `[your-custom-domain]` placeholders with your custom domain instead of the value from the settings page.

Configure Android AssetLinks for Magic Link authentication, password resets, login with passkeys, and IdP SSO login.

1. Get your SHA-256 certificate fingerprint:

``` bash
# For debug builds
./gradlew signingReport

# For release builds
keytool -list -v -keystore /PATH/file.jks -alias YourAlias -storepass *** -keypass ***
```

2. Obtain an environment token by following [these instructions](https://developers.frontegg.com/api/vendor-service)

Send a POST request to Frontegg API:

https://api.frontegg.com/vendors/resources/associated-domains/v1/android

```
{
    "packageName": "{{ANDROID_PACKAGE_NAME}}",
    "sha256CertFingerprints": ["{{KEYSTORE_CERT_FINGERPRINTS}}"]
}
```

To review the Asset Links configuration, you may send a `GET` request to the same endpoint:

```
https://api.frontegg.com/vendors/resources/associated-domains/v1/android
```

To update the configuration, first `DELETE` the existing configuration using its configuration ID, then create a new configuration via `POST`. For example:

```
https://api.frontegg.com/vendors/resources/associated-domains/v1/android/{{configurationId}} 

```




## Add Frontegg SDK to your project

1. Open your Android project.
2. Locate your app-level `build.gradle` file.
3. Add the following dependencies inside the `dependencies` block:

Groovy:

```groovy
dependencies {
    // Add the Frontegg Android Kotlin SDK
    implementation 'com.frontegg.sdk:android:LATEST_VERSION'

    // Add Frontegg observables dependency
    implementation 'io.reactivex.rxjava3:rxkotlin:3.0.1'
}
```

Kotlin:

```kotlin
dependencies {
    // Add the Frontegg Android Kotlin SDK
    implementation("com.frontegg.sdk:android:LATEST_VERSION")

    // Add Frontegg observables dependency
    implementation("io.reactivex.rxjava3:rxkotlin:3.0.1")
}
```
 
 ## Configure build config fields

To set up your Android application to communicate with Frontegg, you need to add the `buildConfigField` properties to the `android/app/build.gradle` file.

These properties will store your Frontegg hostname (without `https`) and the client ID from the previous step:

Groovy:

```groovy
def fronteggDomain = "{{FRONTEGG_DOMAIN}}"
def fronteggClientId = "{{FRONTEGG_CLIENT_ID}}"

android {
    defaultConfig {

        manifestPlaceholders = [
                "package_name"      : applicationId,
                "frontegg_domain"   : fronteggDomain,
                "frontegg_client_id": fronteggClientId
        ]

        buildConfigField "String", 'FRONTEGG_DOMAIN', "\"$fronteggDomain\""
        buildConfigField "String", 'FRONTEGG_CLIENT_ID', "\"$fronteggClientId\""
    }


}
```

Kotlin:

```kotlin
val fronteggDomain = "{{FRONTEGG_DOMAIN}}"
val fronteggClientId = "{{FRONTEGG_CLIENT_ID}}"

android {
    defaultConfig {

        manifestPlaceholders["package_name"] = applicationId.toString()
        manifestPlaceholders["frontegg_domain"] = fronteggDomain
        manifestPlaceholders["frontegg_client_id"] = fronteggClientId

        buildConfigField("String", "FRONTEGG_DOMAIN", "\"$fronteggDomain\"")
        buildConfigField("String", "FRONTEGG_CLIENT_ID", "\"$fronteggClientId\"")
    }

}
```

Add `buildConfig = true` if it does not exist inside the `android` section of your app-level `android/app/build.gradle`.

Groovy:

```groovy
android {
    buildFeatures {
        buildConfig = true
    }
}
```

Kotlin:

```kotlin
android {
    buildFeatures {
        buildConfig = true
    }
}
```



## Initialize FronteggApp

Create a custom `App` class that extends `android.app.Application` to initialize `FronteggApp`:

```kotlin
package com.frontegg.demo

import android.app.Application
import com.frontegg.android.FronteggApp

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        FronteggApp.init(
            BuildConfig.FRONTEGG_DOMAIN,
            BuildConfig.FRONTEGG_CLIENT_ID,
            this, // Application Context
        )
    }
}
```

Register the custom `App` in the app's manifest file.

**AndroidManifest.xml:**

```xml

<application android:name=".App">
    <!--  ... -->
</application>

```
