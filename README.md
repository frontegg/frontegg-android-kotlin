
![Frontegg_Android_SDK (Kotlin)](./logo.png)

Frontegg is a web platform where SaaS companies can set up their fully managed, scalable and brand aware - SaaS features
and integrate them into their SaaS portals in up to 5 lines of code.

## Table of Contents

- [Project Requirements](#project-requirements)
    - [Supported Languages](#supported-languages)
- [Getting Started](#getting-started)
    - [Prepare Frontegg workspace](#prepare-frontegg-workspace)
    - [Add frontegg package to the project](#add-frontegg-package-to-the-project)
    - [Setup build variables](#setup-build-variables)
    - [Initialize FronteggApp](#initialize-fronteggapp)
    - [Add custom loading screen](#Add-custom-loading-screen)
    - [Config Android AssetLinks](#config-android-assetlinks)

## Project Requirements

### Supported Languages

**Android SDK:** The minimum supported version is 26.

## Getting Started

### Prepare Frontegg workspace

Navigate to [Frontegg Portal Settings](https://portal.frontegg.com/development/settings), If you don't have application
follow integration steps after signing up.
Copy FronteggDomain to future steps from [Frontegg Portal Domain](https://portal.frontegg.com/development/settings/domains)

### Add frontegg package to the project

- Open you project
- Find your app's build.gradle file
- Add the following to your dependencies section: 
```groovy
    dependencies {
      // Add the Frontegg Android Kotlin SDK
      implementation 'com.frontegg:android:1.+'
    }
```

### Setup build variables 

To setup your Android application to communicate with Frontegg, you have to use `manifestPlaceholders` property in your build.gradle
file, this property will store hostname of from Frontegg Portal:

```groovy

def fronteggDomain = "DOMAIN_HOST_FROM_PREVIOUS_STEPS"
def fronteggClientId = "CLIENT_ID_FROM_PREVIOUS_STEP"

android {
    defaultConfig {
        manifestPlaceholders = [
                "frontegg_domain" : fronteggDomain,
                frontegg_client_id: fronteggClientId
        ]

        buildConfigField "String", 'FRONTEGG_DOMAIN', "\"$fronteggDomain\""
        buildConfigField "String", 'FRONTEGG_CLIENT_ID', "\"$fronteggClientId\""
    }
    /* Use buildTypes for release / debug configurations */
    // ...
}
```

### Initialize FronteggApp

Create a custom `App` class that extends `android.app.Application` to initialize `FronteggApp`

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
            this // Application Context
        )
    }
}
```

### Add Frontegg Activity

Create `FronteggActivity` class that extends `com.frontegg.android.AbstractFronteggActivity` to handle authenticated redirects

**FronteggAcivity.kt:**

```kotlin
package com.frontegg.demo

import android.content.Intent
import com.frontegg.android.AbstractFronteggActivity

class FronteggActivity: AbstractFronteggActivity() {

    /**
     * This function will be called everytime the FronteggActivity
     * successfully authenticated and should redirect the user to 
     * authenticated screens
     * 
     * NOTE: Replace the `MainActivity::class.java` with your Main Activity class  
     */
    override fun navigateToAuthenticated() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
```

Register the `FronteggActivity` in the app's manifest file

**AndroidManifest.xml:**
```xml
<activity
    android:name="[APP_PACKAGE_NAME].FronteggActivity"
    android:exported="true"
    android:label="@string/app_name"
    android:theme="@style/Theme.MyApplication.NoActionBar">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    <intent-filter  android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="http" />
        <data android:scheme="https" />
        <data android:host="${frontegg_domain}" />
    </intent-filter>
</activity>
```

### Add Frontegg Logout Activity 


Create `FronteggLogoutActivity` class that extends `com.frontegg.android.AbstractFronteggLogoutActivity` to handle logout redirects

**FronteggLogoutAcivity.kt:**

```kotlin
package com.frontegg.demo

import android.content.Intent
import com.frontegg.android.AbstractFronteggLogoutActivity

class FronteggLogoutActivity: AbstractFronteggLogoutActivity() {
    /**
     * This function will be called everytime the FronteggLogoutActivity
     * successfully logged out and should redirect the user to
     * your the previous created `FronteggActivity`
     *
     * NOTE: Replace the `FronteggActivity::class.java` with your `FronteggActivity` class
     */
    override fun navigateToFronteggLogin() {
        val intent = Intent(this, FronteggActivity::class.java)
        startActivity(intent)
        finish()
    }
}
```

Register the `FronteggLogoutActivity` in the app's manifest file

**AndroidManifest.xml:**
```xml
<activity
    android:name=".FronteggLogoutActivity"
    android:theme="@style/Theme.MyApplication.NoActionBar"/>
```

### Config Android AssetLinks 
Configuring your Android `AssetLinks` is required for Magic Link authentication / Reset Password / Activate Account.

In order to add your `AssetLinks` to your Frontegg application, you will need to update in each of your integrated Frontegg Environments the `AssetLinks` that you would like to use with that Environment. Send a POST request to `https://api.frontegg.com/vendors/resources/associated-domains/v1/android` with the following payload:
```
{
    "packageName": "YOUR_APPLICATION_PACKAGE_NAME",
    "sha256CertFingerprints": ["YOUR_KEYSTORE_CERT_FINGERPRINTS"]
}
```
In order to use our API’s, follow [this guide](‘https://docs.frontegg.com/reference/getting-started-with-your-api’) to generate a vendor token.
