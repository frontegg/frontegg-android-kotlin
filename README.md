![Frontegg_Android_SDK (Kotlin)](./logo.png)

Frontegg is a web platform where SaaS companies can set up their fully managed, scalable and brand aware - SaaS features
and integrate them into their SaaS portals in up to 5 lines of code.

## Table of Contents

- [Get Started](#get-started)
    - [Project Requirements](#project-requirements)
    - [Prepare Frontegg workspace](#prepare-frontegg-workspace)
    - [Setup Hosted Login](#setup-hosted-login)
    - [Add Frontegg package to the project](#add-frontegg-package-to-the-project)
    - [Set minimum sdk version](#set-minimum-sdk-version)
    - [Configure build config fields](#configure-build-config-fields)
    - [Config Android AssetLinks](#config-android-assetlinks)
- [Usage](#usage)
    - [Initialize FronteggApp](#initialize-fronteggapp)
    - [Login with Frontegg](#login-with-frontegg)
    - [Logout user](#logout)
    - [Switch Tenant](#switch-tenant)

## Get Started

### Project Requirements

- Android SDK 26+
  Set defaultConfig's minSDK to 26+ in build.gradle:
  ```groovy
  android {
      defaultConfig {
          minSdk 26
      }
  }
  ```
- Java 8+
  Set target java 8 byte code for Android and Kotlin plugins respectively build.gradle:
  ```groovy
  android {
      compileOptions {
          sourceCompatibility JavaVersion.VERSION_1_8
          targetCompatibility JavaVersion.VERSION_1_8
      }
  
      kotlinOptions {
          jvmTarget = '1.8'
      }
  }
  ```

### Prepare Frontegg workspace

Navigate to [Frontegg Portal Settings](https://portal.frontegg.com/development/settings), If you don't have application
follow integration steps after signing up.
Copy FronteggDomain to future steps
from [Frontegg Portal Domain](https://portal.frontegg.com/development/settings/domains)

### Setup Hosted Login

- Navigate to [Login Method Settings](https://portal.frontegg.com/development/authentication/hosted)
- Toggle Hosted login method
- Add `{{ANDROID_PACKAGE_NAME}}://{{FRONTEGG_BASE_URL}}/ios/oauth/callback`
- Replace `ANDROID_PACKAGE_NAME` with your application identifier
- Replace `FRONTEGG_BASE_URL` with your Frontegg base url

### Add Frontegg package to the project

- Open you project
- Find your app's build.gradle file
- Add the following to your dependencies section:

```groovy
    dependencies {
      // Add the Frontegg Android Kotlin SDK
      implementation 'com.frontegg.sdk:android:1.+'
    }
```

### Set minimum sdk version

To set up your Android minimum sdk version, open root gradle file at`android/build.gradle`,
and add/edit the `minSdkVersion` under `buildscript.ext`:

```groovy
buildscript {
    ext {
        minSdkVersion = 26
        // ...
    }
}
```

### Configure build config fields

To set up your Android application on to communicate with Frontegg, you have to add `buildConfigField` property the
gradle `android/app/build.gradle`.
This property will store frontegg hostname (without https) and client id from previous step:

```groovy

def fronteggDomain = "DOMAIN_HOST.com"
def fronteggClientId = "CLIENT_ID"

android {
    defaultConfig {
        
        manifestPlaceholders = [
                package_name : applicationId,
                frontegg_domain : fronteggDomain,
                frontegg_client_id: fronteggClientId
        ]

        buildConfigField "String", 'FRONTEGG_DOMAIN', "\"$fronteggDomain\""
        buildConfigField "String", 'FRONTEGG_CLIENT_ID', "\"$fronteggClientId\""
    }
    
    
}
```

Add bundleConfig=true if not exists inside the android section inside the app gradle `android/app/build.gradle`

```groovy
android {
  buildFeatures {
    buildConfig = true
  }
}
```

### Permissions

Add `INTERNET` permission to the app's manifest file.

```xml

<uses-permission android:name="android.permission.INTERNET"/>
```

### Config Android AssetLinks

Configuring your Android `AssetLinks` is required for Magic Link authentication / Reset Password / Activate Account /
login with IdPs.

To add your `AssetLinks` to your Frontegg application, you will need to update in each of your integrated Frontegg
Environments the `AssetLinks` that you would like to use with that Environment. Send a POST request
to `https://api.frontegg.com/vendors/resources/associated-domains/v1/android` with the following payload:

```
{
    "packageName": "YOUR_APPLICATION_PACKAGE_NAME",
    "sha256CertFingerprints": ["YOUR_KEYSTORE_CERT_FINGERPRINTS"]
}
```

Each Android app has multiple certificate fingerprint, to get your `DEBUG` sha256CertFingerprint you have to run the
following command:

For Debug mode, run the following command and copy the `SHA-256` value

```bash
./gradlew signingReport

###################
#  Example Output:
###################

#  Variant: debugAndroidTest
#  Config: debug
#  Store: /Users/davidfrontegg/.android/debug.keystore
#  Alias: AndroidDebugKey
#  MD5: 25:F5:99:23:FC:12:CA:10:8C:43:F4:02:7D:AD:DC:B6
#  SHA1: FC:3C:88:D6:BF:4E:62:2E:F0:24:1D:DB:D7:15:36:D6:3E:14:84:50
#  SHA-256: D9:6B:4A:FD:62:45:81:65:98:4D:5C:8C:A0:68:7B:7B:A5:31:BD:2B:9B:48:D9:CF:20:AE:56:FD:90:C1:C5:EE
#  Valid until: Tuesday, 18 June 2052

```

For Release mode, Extract the SHA256 using keytool from your `Release` keystore file:

```bash
keytool -list -v -keystore /PATH/file.jks -alias YourAlias -storepass *** -keypass ***
```

In order to use our API’s, follow [this guide](https://docs.frontegg.com/reference/getting-started-with-your-api) to
generate a vendor token.

## Usage

### Initialize FronteggApp

Create a custom `App` class that extends `android.app.Application` to initialize `FronteggApp`
In order to force FronteggApp to use Trusted Web Activity "TWA" for login, pass `true` to forth parameter.

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
            false // use Trusted Web Activtiy "TWA" for login (optional)
        )
    }
}
```

Register the custom `App` in the app's manifest file

**AndroidManifest.xml:**

```xml

<application
        android:name=".App">
    <!--  ... -->
</application>

```

android:name=".App"

## Login with Frontegg

In order to login with Frontegg, you have to call `FronteggAuth.instance.login` method with `activtity` context.
Login method will open Frontegg hosted login page, and will return user data after successful login.

```kotlin

import com.frontegg.android.FronteggAuth

class FirstFragment : Fragment() {
  // ...
  
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {  
    
    binding.loginButton.setOnClickListener {
        FronteggAuth.instance.login(requireActivity())
    }
  }
  // ...
}

```

## Logout user

In order to logout user, you have to call `FronteggAuth.instance.logout` method.
Logout method will clear all user data from the device.

```kotlin

import com.frontegg.android.FronteggAuth

class FirstFragment : Fragment() {
  // ...
  
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {  
    
    binding.logoutButton.setOnClickListener {
        FronteggAuth.instance.logout()
    }
  }
  // ...
}

```

## Switch Tenant

In order to switch tenant, you have to call `FronteggAuth.instance.switchTenant` method with `activtity` context.

```kotlin

import com.frontegg.android.FronteggAuth

class FirstFragment : Fragment() {
  // ...
  
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {  
    
    val tenantIds = FronteggAuth.instance.user.value?.tenantIds ?: listOf()
    
    /**
      *  pick one from `tenantIds` list:
      */
    val tenantToSwitchTo = tenantIds[0] 
    
    binding.switchTenant.setOnClickListener {
        FronteggAuth.instance.switchTenant(tenantToSwitchTo)
    }
  }
  // ...
}

```
