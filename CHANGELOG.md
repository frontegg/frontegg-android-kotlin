## v
## Summary

- A failed network-quality probe during `initializeSubscriptions()` was being treated as an auth failure when `enableOfflineMode` is disabled (the default), calling `clearCredentials()` and wiping a perfectly valid session.
- Most visible on FCM-driven cold starts after long backgrounds: the radio is still resuming from doze, the 5-second HEAD probe to `/<base>/test` returns a stale negative, and the user lands on the login screen even though the refresh token is still valid.
- Apply the same pattern as [86a5793](https://github.com/frontegg/frontegg-android-kotlin/commit/86a5793) (transient 5xx during refresh): keep cached tokens, mark the user authenticated, let the next `/me` or `/oauth/token` call surface a real 401 if the user was actually logged out server-side. A 5s HEAD probe is a hint, not auth state.

## Reproduction

Confirmed by a unit test that drives `initializeSubscriptions()` directly with persisted tokens + forced probe failure + `enableOfflineMode = false`. Before the fix, the test failed at:

```
java.lang.AssertionError: Access token should remain in memory
after a transient probe failure, was null
```

Proving `clearCredentials()` had wiped the in-memory session. After the fix the test passes.

## Test plan

- [x] New unit test `FronteggAuthServiceTest.initializeSubscriptions preserves session when network probe transiently fails on cold start` — passes with the fix, fails on `master`
- [x] Full `:android:testDebugUnitTest` suite green
- [x] Full `FronteggAuthServiceTest`, `FronteggAuthServiceExtendedTest`, `FronteggAppServiceTest` suites green
- [ ] New E2E test `EmbeddedE2ETests.testAuthenticatedColdStartWithExpiredAccessTokenAndTransientProbeFailurePreservesSession` — runs via the existing emulator CI matrix
- [ ] Existing E2E tests covering `enableOfflineMode = false` paths (`testColdLaunchWithOfflineModeDisabledReachesLoginQuickly`, `testOfflineModeDisabledPreservesSessionDuringConnectionLossAndRecovers`, `testPasswordLoginWorksWithOfflineModeDisabled`) still pass

## Behaviour matrix

| Scenario | `enableOfflineMode` | Before | After |
|---|---|---|---|
| Cold start, no tokens, probe fails | any | login screen | login screen (unchanged) |
| Cold start, valid tokens, probe fails | `true` | offline mode + authed | offline mode + authed (unchanged) |
| Cold start, valid tokens, probe fails | `false` | **`clearCredentials()` → login screen** | authed; next `/me` decides |
| `/oauth/token` returns 5xx during refresh | any | authed (since 86a5793) | authed (unchanged) |
| `/oauth/token` returns 401 (real server logout) | any | login screen | login screen (unchanged) |


- added full support for the FRONTEGG_DISABLE_AUTO_REFRESH flag.
When enabled, automatic token refresh is disabled in all cases, including offline mode, initialization, and other refresh mechanisms.

## v
### Added
- Added offline mode config flags:
  - `FRONTEGG_ENABLE_OFFLINE_MODE` (default `false`)
  - `FRONTEGG_NETWORK_MONITORING_INTERVAL_SECONDS` (default `10`)
- Exposed `isOfflineMode` on `FronteggAuth` for UI state handling.

### Changed
- Offline recovery/reconnect flows are now gated by `FRONTEGG_ENABLE_OFFLINE_MODE`.
- Offline monitoring intervals now use `FRONTEGG_NETWORK_MONITORING_INTERVAL_SECONDS` instead of hardcoded delays.
- Added cached offline user persistence and restore behavior for offline startup.

## v1.3.24
## Fixed
- Fixed session recovery after offline mode in background.

## v1.3.23
**Fixed**
- Fixed token refresh getting permanently stuck after background/foreground lifecycle transitions

**Removed**
- Removed unused refreshRetryCount, maxRetries, and baseRetryDelayMs fields from FronteggAuthService

## v1.3.22
### Fixed
- **Unexpected logout after JWT expiry / opening app from notification:** Concurrent refresh calls (e.g. app foreground + `RefreshTokenJobService` or `RefreshTokenAlarmReceiver`) could both use the same refresh token; one request succeeded and one returned 401, and the error path could clear credentials. Refresh is now **single-flight** inside `sendRefreshToken()` so only one refresh runs at a time across all callers.

### Added
- **`FronteggAuth.refreshTokenAndWait()`** (`suspend`): Waits until token refresh completes (or fails). Use when the caller must have an updated `accessToken` immediately after refresh. `refreshTokenIfNeeded()` still returns immediately without waiting

## v1.3.21
**Entitlements support**
Adds support for Frontegg Entitlements so Android apps can load and check user features and permissions.
- Load entitlements from the Frontegg API and cache them locally.
- Check feature and permission access with `getFeatureEntitlements`, `getPermissionEntitlements`, and `getEntitlements`.
- Entitlements load automatically on login and refresh; cache is cleared on logout.
- Enable via `FRONTEGG_ENTITLEMENTS_ENABLED` in BuildConfig (e.g. buildConfigField "boolean", `FRONTEGG_ENTITLEMENTS_ENABLED`, "true" in your app’s build.gradle).

**Docs & demos**
- Entitlements section in README.
- Entitlements UI in demo apps (app and embedded).

## v1.3.20
- Fixed race condition in hosted login callback while using chrome custom tabs flag`FRONTEGG_USE_CHROME_CUSTOM_TABS = true`

## v1.3.19
Changed baseUrl and clientId for demo and test projects.
Fixed: first login attempt returns user to the login form(direct login)
- SocialLoginUrlGenerator: Use applicationId ?: clientId for the OAuth client_id (match AuthorizeUrlGenerator and iOS).
- Blank app id: Treat empty/blank applicationId as unset via .takeIf { it.isNotBlank() } in AuthorizeUrlGenerator, SocialLoginUrlGenerator, AppIdHeaderHelper, and Api so client_id falls back to clientId.
- WebView headers: Add frontegg-requested-application-id on all login WebView loads via new AppIdHeaderHelper in EmbeddedAuthActivity, FronteggWebClient, and FronteggAuthService.

## v1.3.17
Added new section to advanced documentation regarding login-per-session feature.
Fixed: Autofill Password Managers kills activity
Fixed: screen rotation resets flow.

- updated demo apps with more examples
- schedule alarm permission request
- tests coverage 
Removed local sentry flag.
Added feature-flag for sentry support.
- added login per account

## v1.3.16
- **Sentry**: Sentry is controlled only by the remote feature flag `mobile-enable-logging`; local option `FRONTEGG_ENABLE_SENTRY_LOGGING` has been removed.
- **Offline Support**: Configurable `FRONTEGG_SENTRY_MAX_QUEUE_SIZE` (default: 30) for event queuing during offline periods (maps to Sentry `maxCacheItems`)
- **Comprehensive Breadcrumbs**: Automatic breadcrumbs for HTTP requests, OAuth callbacks, and token refresh attempts (URL query is intentionally omitted)
- **Trace ID Correlation**: `frontegg-trace-id` headers from API responses are logged as Sentry breadcrumbs and also saved locally to `frontegg-trace-ids.log`
- **Safe Initialization**: Sentry is initialized by the SDK only when the feature flag is enabled (prevents startup crashes when DSN is not configured by the host app)
- **Fixed problem with authenticated user after logout and restart the app. 
- **Fixed problems with switchTenants.

## v1.3.14
- doze mode support

## v1.3.13
Added tenant per session support

## v1.3.12
Fixed bug with directLogin in embedded mode.

## v1.3.11
Fixed error when reset password method redirects sometimes to blank screen.

## v1.3.10
Access Token retrieval examples update.

## v1.3.9
Disabled session alive-time for offline mode.
Improvement for autorefresh token.

## v1.3.8
Added ability to disable token refresing in SDK - side with "FRONTEGG_DISABLE_AUTO_REFRESH" gradle parameter.
A service has been added to monitor internet connection quality. If the connection is weak (for example, Edge), when the token expires, the refresh token will be added to a queue and executed once the connection is restored.
Offline mode support has been improved.

## v1.3.7
Fixes for FR-22063

## v1.3.6
Added new redirecting flow for social logins.

## v1.3.5
Fixed long-text exception when receiving timeout exception
Fixed logout issue after 30m inactive state

## v1.3.4
Fixed bug with unauthorized exceptions for weak network connections
Added auto reconnect when connection was established.

## v1.3.3
Add auto-reconnect when exiting offline mode

## v1.3.2

- fixed crashes in offline mode

## v1.3.1
- Added all `BuilldConfig` files to `consumer-rules.pro`

## v1.3.0

# 🔄 Implement Context-Based Lazy Initialization for Frontegg SDK

## 📋 Summary

This PR refactors the Frontegg Android SDK to use context-based lazy initialization with automatic configuration discovery from `BuildConfig`. The changes eliminate the need for manual SDK initialization while maintaining full backward compatibility.

## 🎯 Key Changes

### **FronteggApp.kt - Lazy Initialization Pattern**

**New Context Extensions:**
```kotlin
// Automatic initialization from BuildConfig
val app = context.fronteggApp
val auth = context.fronteggAuth
```

**Removed Static Singleton Pattern:**
- ❌ `FronteggApp.getInstance()` 
- ❌ `FronteggAuth.instance`
- ✅ `context.fronteggApp`
- ✅ `context.fronteggAuth`

### **Utils.kt - Dynamic Configuration Loading**

**New `Context.fronteggConstants` Extension:**
- Automatically reads configuration from `BuildConfig` using reflection
- Recursively searches package hierarchy for correct `BuildConfig` class
- Provides type-safe access with fallback defaults

**Configuration Parameters:**
- `FRONTEGG_DOMAIN` → `baseUrl`
- `FRONTEGG_CLIENT_ID` → `clientId`
- `FRONTEGG_APPLICATION_ID` → `applicationId`
- `FRONTEGG_USE_ASSETS_LINKS` → `useAssetsLinks`
- `FRONTEGG_USE_CHROME_CUSTOM_TABS` → `useChromeCustomTabs`
- `FRONTEGG_DEEP_LINK_SCHEME` → `deepLinkScheme`
- `FRONTEGG_USE_DISK_CACHE_WEBVIEW` → `useDiskCacheWebview`
- `FRONTEGG_MAIN_ACTIVITY_CLASS` → `mainActivityClass`

### **Authentication Flow Updates**

**Service Access Changes:**
```kotlin
// Old
FronteggAuthService.instance.isLoading.value = true
FronteggAuth.instance.isEmbeddedMode

// New  
FronteggState.isLoading.value = true
context.fronteggAuth.isEmbeddedMode
```

**AuthorizeUrlGenerator Context Parameter:**
```kotlin
// Old
AuthorizeUrlGenerator().generate(loginHint)

// New
AuthorizeUrlGenerator(context).generate(loginHint)
```

## 🚀 **Benefits**

### **Developer Experience**
- ✅ **Zero Configuration**: SDK auto-discovers settings from `BuildConfig`
- ✅ **Type Safety**: Compile-time validation of configuration parameters
- ✅ **Graceful Fallbacks**: Sensible defaults when configuration is missing

### **Architecture Improvements**
- ✅ **Context-Aware**: Each context maintains its own configuration scope
- ✅ **Lazy Loading**: SDK initializes only when first accessed
- ✅ **Multi-Module Support**: Works with complex package hierarchies
- ✅ **Backward Compatibility**: Existing APIs continue to work

## 🔄 **Migration**

### **For Existing Users**
```kotlin
// Old
val auth = FronteggAuth.instance

// New
val auth = context.fronteggAuth
```

### **For New Users**
```kotlin
// Just add BuildConfig constants and access via context
val auth = context.fronteggAuth
auth.login(this) { result ->
    // Handle authentication result
}
```

## 🧪 **Testing**

- ✅ Backward compatibility maintained
- ✅ Multi-region support preserved (`initWithRegions`)
- ✅ Configuration discovery tested
- ✅ Error handling with fallbacks
- ✅ Performance optimization through lazy loading

## ⚠️ **Breaking Changes**

**None** - Full backward compatibility maintained while introducing new convenient APIs.

---

This refactor significantly improves the SDK's developer experience while maintaining all existing functionality. The new context-based approach provides automatic configuration discovery and better modularity.

## v1.2.48
- added `http` support in Manifest
- Added support for http and updated docs for multi-region

## v1.2.47
- Migrate publish process to central Sonatype
- Fix sonatype repository url

## v1.2.46
- Added `isInitialized` function for `FronteggApp`

## v1.2.45

- Reduce number of full page load when loading login page

## v1.2.44
- fixed url handling for oauth
- Fix e2e trigger ref #151
Fix e2e trigger script
- exposed function `updateCredentials` that sets the access token and refresh token for the current session

## v
- Updated example projects UI
- added redirect to auth page if auth request failed due to connectivity problems
- fixed url lazy evaluation

## v1.2.42

### 🌟 New Features

* **Web Resource Caching for WebView**
  Added support for persistent caching of **JavaScript**, **font**, and **CSS** files loaded via WebView. This reduces redundant network requests and improves page load performance for embedded login and other hosted assets.

  **How to enable:**
  Set `useDiskCacheWebview = true` when initializing the SDK:

  ```kotlin
  FronteggApp.init(
      fronteggDomain = "your-domain.frontegg.com",
      clientId = "your-client-id",
      context = applicationContext,
      useDiskCacheWebview = true
  )
  ```

  For multi-region apps:

  ```kotlin
  FronteggApp.initWithRegions(
      regions = yourRegionList,
      context = applicationContext,
      useDiskCacheWebview = true
  )
  ```

### 🐞 Bug Fixes

* **Login Direct Action**
  Fixed an issue where the `loginDirectAction` command was not executing properly in certain scenarios, especially when triggered after cold app launches or delayed SDK initialization.

## v1.2.41
## 🚀 New Features & Enhancements

- **Restoration SDK Integration**  
  Added restoration support across activities to ensure seamless session recovery when returning to the app.

- **Flutter Plugin Initialization Fix**  
  Ensured that the Flutter plugin initializes correctly when a Frontegg deep link is opened before launching the app, preventing `app_not_initialized` crashes.

- **Unified Native Loader Support**  
  Introduced a unified native loader mechanism to better support hybrid platform integrations.

- **Coroutine Improvements**  
  Replaced `Handlers` and `GlobalScope` usage with structured coroutine scopes for safer and more maintainable async operations.

- **README Updates**  
  Improved documentation for better developer onboarding and usage clarity.

---

## 🐛 Bug Fixes

- **Channel Subscription Timing**  
  Ensured the channel subscription starts only after Frontegg is fully initialized to avoid race conditions.

- **Crash Fix for Background Token Refresh**  
  Fixed a crash caused by an unhandled exception during background token refresh.

- **API Safety Enhancements**  
  Added try-catch handling for `api.me()` and `api.exchangeToken()` to improve resilience against unexpected failures.

- **Publishing Script Fix**  
  Corrected issues in the publishing script to ensure consistent release workflows.

---

## ✅ QA & Tooling

- **Detekt Integration**  
  Added [Detekt](https://github.com/detekt/detekt) to QA and linting pipelines for Kotlin code quality enforcement.
Add trigger to e2e test on pull request

## v
- Improved `FronteggInnerStorage`

## v1.2.39
- Added `step-up` instruction.
- Updated docs.
- Support deep linking for redirect in Embedded Login WebView

## v
- FIxed `RefreshTokenJobService`.

## v1.2.37
- fixed `onPullRequestMerged` workflow
- ✅ Legal links like /terms-of-use/ or /privacy-policy/ or .pdf will open in the system browser
- ✅ All other URLs will continue to behave as they currently do
- ✅ No SDK behavior broken, no unwanted redirects

## v
- Fix step-up

## v1.2.35
- fixed onReleaseMerged prepare release step according to https://github.com/actions/github-script?tab=readme-ov-file#v5

## v1.2.34
- Added automation of generation `CHANGELOG.md`
- added `DefaultLoader` to `EmbeddedAuthActivity` and customization mechanism











