# Migration Guide (1.2.* → 1.3.*)

This guide helps you migrate from Frontegg Android SDK version 1.2.* to 1.3.*. The new version introduces context-based lazy initialization, replacing the old static singleton pattern. The new approach provides automatic configuration discovery and better modularity.

## Quick Start for New Users

### 1. Add BuildConfig Constants

Add these constants to your app's `build.gradle`:

```gradle
android {
    defaultConfig {
        // Required
        buildConfigField "String", "FRONTEGG_DOMAIN", "\"your-domain.frontegg.com\""
        buildConfigField "String", "FRONTEGG_CLIENT_ID", "\"your-client-id\""
        
        // Optional
        buildConfigField "String", "FRONTEGG_APPLICATION_ID", "\"your-application-id\""
        buildConfigField "boolean", "FRONTEGG_USE_ASSETS_LINKS", "false"
        buildConfigField "boolean", "FRONTEGG_USE_CHROME_CUSTOM_TABS", "true"
        buildConfigField "String", "FRONTEGG_DEEP_LINK_SCHEME", "\"your-scheme\""
        buildConfigField "boolean", "FRONTEGG_USE_DISK_CACHE_WEBVIEW", "false"
        buildConfigField "String", "FRONTEGG_MAIN_ACTIVITY_CLASS", "\"com.your.package.MainActivity\""
    }
}
```

### 2. Use the SDK

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Access authentication via context
        val auth = this.fronteggAuth
        
        // Login
        auth.login(this) { result ->
            when (result) {
                is Success -> {
                    // Handle successful login
                    startActivity(Intent(this, HomeActivity::class.java))
                }
                is Error -> {
                    // Handle error
                    Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
```

## Migration for Existing Users (1.2.* → 1.3.*)

### Step 1: Update BuildConfig

Add the required constants to your `build.gradle`:

```gradle
android {
    defaultConfig {
        // Required - replace with your actual values
        buildConfigField "String", "FRONTEGG_DOMAIN", "\"your-domain.frontegg.com\""
        buildConfigField "String", "FRONTEGG_CLIENT_ID", "\"your-client-id\""
        
        // Optional - add as needed
        buildConfigField "String", "FRONTEGG_APPLICATION_ID", "\"your-application-id\""
        buildConfigField "boolean", "FRONTEGG_USE_ASSETS_LINKS", "false"
        buildConfigField "boolean", "FRONTEGG_USE_CHROME_CUSTOM_TABS", "true"
        buildConfigField "String", "FRONTEGG_DEEP_LINK_SCHEME", "\"your-scheme\""
        buildConfigField "boolean", "FRONTEGG_USE_DISK_CACHE_WEBVIEW", "false"
        buildConfigField "String", "FRONTEGG_MAIN_ACTIVITY_CLASS", "\"com.your.package.MainActivity\""
    }
}
```

### Step 2: Update Code References

#### Authentication Access

```kotlin
// OLD CODE
val auth = FronteggAuth.instance
val isLoading = FronteggAuthService.instance.isLoading.value
val isEmbedded = FronteggAuth.instance.isEmbeddedMode

// NEW CODE
val auth = context.fronteggAuth
val isLoading = FronteggState.isLoading.value
val isEmbedded = context.fronteggAuth.isEmbeddedMode
```

### Step 3: Remove Manual Initialization

If you were manually initializing the SDK, you can now remove that code:

```kotlin
// OLD CODE - Remove this
FronteggApp.init(
    fronteggDomain = "your-domain.frontegg.com",
    clientId = "your-client-id",
    context = this,
    applicationId = "your-application-id"
)

// NEW CODE - No manual initialization needed!
// The SDK automatically initializes when you first access it:
val auth = context.fronteggAuth
```

## Multi-Region Support

If you're using multi-region functionality, the `initWithRegions` method is still supported:

```kotlin
// This still works - no changes needed
val regions = listOf(
    RegionConfig(
        key = "us",
        baseUrl = "https://us.frontegg.com",
        clientId = "us-client-id",
        applicationId = "us-app-id"
    ),
    RegionConfig(
        key = "eu", 
        baseUrl = "https://eu.frontegg.com",
        clientId = "eu-client-id",
        applicationId = "eu-app-id"
    )
)

FronteggApp.initWithRegions(regions, context)
```

## BuildConfig Configuration Reference

### Required Constants

| Constant | Type | Description | Example |
|----------|------|-------------|---------|
| `FRONTEGG_DOMAIN` | String | Your Frontegg domain | `"your-domain.frontegg.com"` |
| `FRONTEGG_CLIENT_ID` | String | Your Frontegg client ID | `"your-client-id"` |

### Optional Constants

| Constant | Type | Default | Description | Example |
|----------|------|---------|-------------|---------|
| `FRONTEGG_APPLICATION_ID` | String | `null` | Your Frontegg application ID | `"your-app-id"` |
| `FRONTEGG_USE_ASSETS_LINKS` | boolean | `true` | Enable asset links | `false` |
| `FRONTEGG_USE_CHROME_CUSTOM_TABS` | boolean | `true` | Enable Chrome Custom Tabs | `true` |
| `FRONTEGG_DEEP_LINK_SCHEME` | String | `null` | Custom deep link scheme | `"myapp"` |
| `FRONTEGG_USE_DISK_CACHE_WEBVIEW` | boolean | `false` | Enable WebView disk cache | `false` |
| `FRONTEGG_MAIN_ACTIVITY_CLASS` | String | `null` | Main activity class name | `"com.example.MainActivity"` |

### Build Variants Example

```gradle
android {
    buildTypes {
        debug {
            buildConfigField "String", "FRONTEGG_DOMAIN", "\"dev.frontegg.com\""
            buildConfigField "String", "FRONTEGG_CLIENT_ID", "\"dev-client-id\""
        }
        release {
            buildConfigField "String", "FRONTEGG_DOMAIN", "\"prod.frontegg.com\""
            buildConfigField "String", "FRONTEGG_CLIENT_ID", "\"prod-client-id\""
        }
    }
    
    flavorDimensions "environment"
    productFlavors {
        staging {
            dimension "environment"
            buildConfigField "String", "FRONTEGG_DOMAIN", "\"staging.frontegg.com\""
            buildConfigField "String", "FRONTEGG_CLIENT_ID", "\"staging-client-id\""
        }
        production {
            dimension "environment"
            buildConfigField "String", "FRONTEGG_DOMAIN", "\"prod.frontegg.com\""
            buildConfigField "String", "FRONTEGG_CLIENT_ID", "\"prod-client-id\""
        }
    }
}
```

## Troubleshooting

### Common Issues

#### 1. BuildConfig Not Found
```
Error: Field 'FRONTEGG_DOMAIN' not found in BuildConfig
```

**Solution:** Ensure you've added the buildConfigField to your `build.gradle` and rebuilt the project.

#### 2. Configuration Not Loading
```
Error: Failed to retrieve BuildConfig class for package
```

**Solution:** The SDK automatically searches parent packages. If your `BuildConfig` is in a different package, it will find it automatically.

#### 3. Missing Required Constants
```
Error: baseUrl is empty
```

**Solution:** Ensure `FRONTEGG_DOMAIN` and `FRONTEGG_CLIENT_ID` are set in your `build.gradle`.

### Debug Configuration

To debug configuration loading, check the logs:

```kotlin
// The SDK logs configuration discovery
Log.d("FronteggApp", "Initializing Frontegg SDK with constants: {...}")
```

## Complete Example

### build.gradle
```gradle
android {
    defaultConfig {
        applicationId "com.example.myapp"
        
        // Required
        buildConfigField "String", "FRONTEGG_DOMAIN", "\"myapp.frontegg.com\""
        buildConfigField "String", "FRONTEGG_CLIENT_ID", "\"my-client-id\""
        
        // Optional
        buildConfigField "String", "FRONTEGG_APPLICATION_ID", "\"my-app-id\""
        buildConfigField "boolean", "FRONTEGG_USE_CHROME_CUSTOM_TABS", "true"
        buildConfigField "String", "FRONTEGG_DEEP_LINK_SCHEME", "\"myapp\""
        buildConfigField "String", "FRONTEGG_MAIN_ACTIVITY_CLASS", "\"com.example.myapp.MainActivity\""
    }
}
```

### MainActivity.kt
```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Access authentication
        val auth = this.fronteggAuth
        
        // Check authentication status
        if (auth.isAuthenticated.value) {
            // User is logged in
            startActivity(Intent(this, HomeActivity::class.java))
        } else {
            // User needs to login
            findViewById<Button>(R.id.loginButton).setOnClickListener {
                auth.login(this) { result ->
                    when (result) {
                        is Success -> {
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        }
                        is Error -> {
                            Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
```

## Migration Checklist (1.2.* → 1.3.*)

- [ ] Add required BuildConfig constants to `build.gradle`
- [ ] Update authentication access from `FronteggAuth.instance` to `context.fronteggAuth`
- [ ] Remove manual `FronteggApp.init()` calls (if any)
- [ ] Test authentication flow
- [ ] Test multi-region functionality (if applicable)
- [ ] Verify configuration loading in logs

---

**Need Help?** If you encounter any issues during migration from 1.2.* to 1.3.*, please check the troubleshooting section or contact our support team. 