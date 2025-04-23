# Project setup

This section guides you through the essential steps to configure the Frontegg SDK in your Android project.

## Set the minimum SDK version

To ensure your app targets Android SDK 26+, update your project's configuration:

1. Open your root-level `android/build.gradle` file.
2. Set the `minSdk` version.

Groovy:

```groovy
buildscript {
    ext {
        minSdk = 26
        // ...
    }
}
```

Kotlin:

```kotlin
android {
    defaultConfig {
        minSdk = 26
        // ...
    }
}
```

## Set up Android SDK and language compatibility

Groovy:

```groovy
android {
    defaultConfig {
        minSdk 26
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = '1.8'
    }
}
```

Kotlin:

```kotlin
android {
    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}
```

## Enable Android AssetLinks

To enable Android features like Magic Link authentication, password reset, account activation, and login with identity providers, follow the steps below.

**NOTE**: Make youre you have a [vendor token to access Frontegg APIs](https://docs.frontegg.com/reference/getting-started-with-your-api). 

1. Send a `POST` request to the following Frontegg endpoint:

   ```
   https://api.frontegg.com/vendors/resources/associated-domains/v1/android
   ```
2. Use the following payload:

  ```
   {
  "packageName": "{{ANDROID_PACKAGE_NAME}}",
  "sha256CertFingerprints": ["{{KEYSTORE_CERT_FINGERPRINTS}}"]
   }
   ```

3. Get your `sha256CertFingerprints`. Each Android app has multiple certificate fingerprints. You must extract at least the one for `DEBUG` and optionally for `RELEASE`.

**For Debug mode:**

1. Open a terminal in your project root.
2. Run the following command:

```
./gradlew signingReport
```
3. Look for the section with:

```
Variant: debug
Config: debug
```
4. Copy the SHA-256 value from the output. Make sure the `Variant` and `Config` both equal `debug`.
     

Example output:
     
``` sh
./gradlew signingReport

###################
#  Example Output:
###################

#  Variant: debug
#  Config: debug
#  Store: /Users/davidfrontegg/.android/debug.keystore
#  Alias: AndroidDebugKey
#  MD5: 25:F5:99:23:FC:12:CA:10:8C:43:F4:02:7D:AD:DC:B6
#  SHA1: FC:3C:88:D6:BF:4E:62:2E:F0:24:1D:DB:D7:15:36:D6:3E:14:84:50
#  SHA-256: D9:6B:4A:FD:62:45:81:65:98:4D:5C:8C:A0:68:7B:7B:A5:31:BD:2B:9B:48:D9:CF:20:AE:56:FD:90:C1:C5:EE
#  Valid until: Tuesday, 18 June 2052
```

**For Release mode:**

1. Run the following command (customize the path and credentials):
        
```
keytool -list -v -keystore /PATH/file.jks -alias YourAlias -storepass *** -keypass ***
```

2. Copy the `SHA-256` value from the output.

## Setup for  `Gradle8+`

Enable `buildconfig` feature:

1. Add the below line to your `gradle.properties`:
```properties
android.defaults.buildfeatures.buildconfig=true
```

1. Add the below lines to your `app/build.gradle`:

Groovy:

```gradle
android {
    ...
    buildFeatures {
        buildConfig = true
    }
    ...
}
```

Kotlin:

```kotlin
android {
    ...
    buildFeatures {
        buildConfig = true
    }
    ...
}
```

##  `Proguard` setup (Optional)

If `minifyEnabled` and `shrinkResources` is `true` follow the instruction below.

Modify `proguard-rules.pro`:

```
# Gson relies on generic type information stored in class files when working with fields. 
# ProGuard removes this information by default, so we need to retain it.
-keepattributes Signature

# according to https://stackoverflow.com/a/76224937
# This is also required for R8 in compatibility mode, as several optimizations 
# (such as class merging and argument removal) may remove the generic signature.
# For more information, see:
# https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#troubleshooting-gson-gson
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Retain GSON @Expose annotation attributes
-keepattributes AnnotationDefault,RuntimeVisibleAnnotations
-keep class com.google.gson.reflect.TypeToken { <fields>; }
-keepclassmembers class **$TypeAdapterFactory { <fields>; }

# Keep Frontegg classes
-keep class com.frontegg.android.utils.JWT { *; }
-keep class com.frontegg.android.models.** { *; }

# Retain Tink classes used for shared preferences encryption
-keep class com.google.crypto.tink.** { *; }
```

## Passkeys authentication

Passkeys provide a seamless, passwordless login experience using WebAuthn and platform-level biometric authentication. The following setup is required for both webview and direct API implementations:

**Prerequisites**

Use Android SDK 26+.


1. Open `android/build.gradle`.
2. Add the following Gradle dependencies under dependencies:

   ```groovy
      dependencies {
       implementation 'androidx.browser:browser:1.8.0'
       implementation 'com.frontegg.sdk:android:1.2.30'
   }
   ```

3. Inside the `android` block, add the following to set Java 8 compatibility:

   ```groovy
    android {
     compileOptions {
         sourceCompatibility JavaVersion.VERSION_1_8
         targetCompatibility JavaVersion.VERSION_1_8
     }
   }
   ```
