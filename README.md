
![Frontegg_Android_SDK (Kotlin)](./logo.png)

Frontegg is a web platform where SaaS companies can set up their fully managed, scalable and brand aware - SaaS features
and integrate them into their SaaS portals in up to 5 lines of code.

## Project Requirements

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


## Getting Started

  - [Prepare Frontegg workspace](#prepare-frontegg-workspace)
  - [Setup Hosted Login](#setup-hosted-login)
  - [Add frontegg package to the project](#add-frontegg-package-to-the-project)
  - [Setup build variables](#setup-build-variables)
  - [Initialize FronteggApp](#initialize-fronteggapp)
  - [Add custom loading screen](#add-custom-loading-screen)
  - [Config Android AssetLinks](#config-android-assetlinks)

### Prepare Frontegg workspace

Navigate to [Frontegg Portal Settings](https://portal.frontegg.com/development/settings), If you don't have application
follow integration steps after signing up.
Copy FronteggDomain to future steps from [Frontegg Portal Domain](https://portal.frontegg.com/development/settings/domains)

### Setup Hosted Login

- Navigate to [Login Method Settings](https://portal.frontegg.com/development/authentication/hosted)
- Toggle Hosted login method
- Add `{{LOGIN_URL}}/mobile/callback`

### Add frontegg package to the project

- Open you project
- Find your app's build.gradle file
- Add the following to your dependencies section: 
```groovy
    dependencies {
      // Add the Frontegg Android Kotlin SDK
      implementation 'com.frontegg.sdk:android:1.+'
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
            this, // Application Context
            null
        )
    }
}
```

Register the custom `App` in the app's manifest file

**AndroidManifest.xml:**
```xml

<application
        android:name=".App"
        <!--  ... -->
        >
    <!--  ... -->
</application>

```
android:name=".App"

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
    android:name=".FronteggActivity"
    android:exported="true"
    android:label="@string/app_name"
    android:theme="@style/Theme.Design.NoActionBar">
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

**FronteggLogoutActivity.kt:**

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
    android:theme="@style/Theme.Design.NoActionBar"/>
```

### Add custom loading screen
In order to customize Frontegg loading screen:

- Create new layout file contains your loader screen design:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    tools:ignore="UseCompoundDrawables">

    <ImageView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:contentDescription="@string/logo"
        android:src="@mipmap/ic_launcher_round" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:gravity="center"
        android:text="@string/app_name"
        android:textSize="20sp" />

</LinearLayout>
```

- Add `R.layout.loader` FronteggApp.init as the 4th argument:
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
              this,
              R.layout.loader // <<-- here
          )
      }
  }
```

### Permissions

Add `INTERNET` permission to the app's manifest file.

```xml
<uses-permission android:name="android.permission.INTERNET" />
```


### Config Android AssetLinks 
Configuring your Android `AssetLinks` is required for Magic Link authentication / Reset Password / Activate Account / login with IdPs.

To add your `AssetLinks` to your Frontegg application, you will need to update in each of your integrated Frontegg Environments the `AssetLinks` that you would like to use with that Environment. Send a POST request to `https://api.frontegg.com/vendors/resources/associated-domains/v1/android` with the following payload:
```
{
    "packageName": "YOUR_APPLICATION_PACKAGE_NAME",
    "sha256CertFingerprints": ["YOUR_KEYSTORE_CERT_FINGERPRINTS"]
}
```

Each Android app has multiple certificate fingerprint, to get your `DEBUG` sha256CertFingerprint you have to run the following command:

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

In order to use our API’s, follow [this guide](https://docs.frontegg.com/reference/getting-started-with-your-api) to generate a vendor token.
