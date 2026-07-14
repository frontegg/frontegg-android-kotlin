## v
## Context — FR-25496

[FR-25496](https://frontegg.atlassian.net/browse/FR-25496) reports: *"Android build fails on AGP 8.x / JDK 17+ environments, due to hardcoded JVM target 11 in SDK."*

**The published SDK module is not the culprit.** `android/build.gradle` already targets `1.8` (`sourceCompatibility/targetCompatibility VERSION_1_8`, `kotlinOptions.jvmTarget '1.8'`) — it was lowered from 11 → 1.8 back in Jan 2023 (commit `e7aa5c8`). Java-8 bytecode runs fine on JDK 17+, so the consumer-facing AAR is not what fails.

The **only** place in the repo still pinned to JVM target 11 was the internal `detekt-rules` static-analysis module:

```groovy
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
compileKotlin {
    kotlinOptions { jvmTarget = "11" }
}
```

This is build tooling — it is **not** part of the published `com.frontegg.sdk:android` artifact, so it does not affect a customer's app build. But it was an inconsistent outlier (every other module, and the detekt task itself at `android/build.gradle:95`, uses `1.8`), and it's the literal "hardcoded JVM target 11" the ticket points at.

## Change

Lower `detekt-rules` to JVM target `1.8`, matching every sibling module.

The `detekt-api:1.23.8` dependency is compiled to **Java 8 bytecode (class major version 52)** — verified directly — so nothing required target 11; Kotlin will not hit an inline-bytecode mismatch. The value was gratuitous.

## Verification

```
JAVA_HOME=<openjdk@17> ./gradlew :detekt-rules:compileKotlin
BUILD SUCCESSFUL
```

Compiles cleanly on **JDK 17** (the environment in the ticket) and emits Java 8 bytecode (major version 52).

## Scope note

This is a tooling-consistency cleanup. It does **not** change the published SDK, which already targets 1.8. If customers are still seeing real AGP 8.x / JDK 17 build failures, the root cause is almost certainly elsewhere (e.g. a JVM-target *consistency* mismatch in the consumer's own project, or an AGP/Gradle/Kotlin/`compileSdk` constraint) and should be reproduced with the exact Gradle error before any further change.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

[FR-25496]: https://frontegg.atlassian.net/browse/FR-25496?atlOrigin=eyJpIjoiNWRkNTljNzYxNjVmNDY3MDlhMDU5Y2ZhYzA5YTRkZjUiLCJwIjoiZ2l0aHViLWNvbS1KU1cifQ
## What & why

Closes **FR-19725** — _[Mobile SDK] Replace startActivityForResult with the new Activity Result API_.

While implementing this I found the ticket's framing doesn't fit the codebase: the SDK launches its auth activities with `startActivityForResult`, but **the result is never consumed** — there is no `onActivityResult` override anywhere in the SDK. Auth outcomes are delivered through the static `onAuthFinishedCallback` field instead, so the request-code / result-code contract is **dead code**.

Given that, adopting `registerForActivityResult` would add ceremony *and* force a **breaking API change** on integrators (a launcher must be registered before the host `Activity` reaches `STARTED`, but the SDK is handed a running activity mid-lifecycle through static methods) — all to arrive at the exact same behavior. So this PR removes the deprecated API instead of ritually replacing it.

## Changes

- Replace all `startActivityForResult(intent, OAUTH_LOGIN_REQUEST)` launches with `startActivity(intent)`:
  - `AuthenticationActivity` — `authenticate`, `authenticateWithMultiFactor`, `authenticateWithStepUp`
  - `EmbeddedAuthActivity` — `authenticate`, `directLoginAction`, `authenticateWithMultiFactor`, `authenticateWithStepUp`
  - `FronteggNativeBridge` — Chrome Custom Tabs launch (custom tabs return via deep-link redirect, never an activity result)
- Remove the now-unused `OAUTH_LOGIN_REQUEST` request-code constants and the now-unused `EmbeddedAuthActivity` import in the bridge.

## Behavior impact

**None.** `startActivity` is equivalent to `startActivityForResult(intent, -1)`, and the `onAuthFinishedCallback` delivery path is untouched. The `setResult(...)` calls remain (harmless no-ops now, not deprecated) to keep the diff focused. Public API surface (`FronteggAuth.login` / `directLoginAction` / MFA / step-up) is unchanged.

## Note for reviewer

`OAUTH_LOGIN_REQUEST` was a `public const val` in the activity companion objects. It's an internal request code tied to the removed pattern with no cross-repo consumers, so it's removed here. If we'd rather preserve strict source compat, say the word and I'll keep the constants as `@Deprecated` no-ops.

## Testing

- `./gradlew :android:compileReleaseKotlin :android:compileDebugUnitTestKotlin` -> BUILD SUCCESSFUL
- Manual smoke test recommended across the four launch paths: hosted login, embedded login, MFA, and step-up.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
## Problem

Even with the native token bridge and the hosted-login box's step-up render fix ([admin-box#2865](https://github.com/frontegg/admin-box/pull/2865), deployed in 7.118.0), a native step-up still renders a **blank page instead of the MFA challenge**.

**Root cause:** the box renders its step-up page (`StepUpPage`) only when the WebView is *at* the step-up route (`/oauth/account/step-up`) with the native token bridge present. A native step-up authorize URL (`acr_values` + `max_age`) instead bootstraps the box on its **prelogin** path and never navigates to that route on its own — it is silently token-refreshed and the box renders blank. Nothing routes the WebView onward to `/oauth/account/step-up`.

This is the Android counterpart of [frontegg-ios-swift#278](https://github.com/frontegg/frontegg-ios-swift/pull/278).

## Fix

New `StepUpWebDriver` (`embedded/StepUpWebDriver.kt`): while presenting a step-up flow, inject a **document-start** script that, before the box reads the document:
- seeds the box's step-up `localStorage` contract (`SHOULD_STEP_UP`, `FRONTEGG_OAUTH_STEP_UP_MAX_AGE`),
- rewrites the URL to the box's step-up route (`<basename>/account/step-up`) so the box renders `StepUpPage`, and
- points the box's after-auth redirect (`FRONTEGG_AFTER_AUTH_REDIRECT_URL`) back at the original authorize URL.

On completion the box performs a full navigation to that authorize URL — now with an elevated session — yielding a stepped-up `code` that the existing `FronteggWebClient` OAuth callback already captures. No new native token-capture path is required.

Wiring (`EmbeddedAuthActivity.consumeIntent`): the driver is installed right after the step-up `AUTHORIZE_URI` is read, **before any `loadUrl`**, and gated on `isStepUpAuthorization && url.contains("acr_values")` so normal login is untouched.

Notes:
- Uses `WebViewCompat.addDocumentStartJavaScript` behind the `DOCUMENT_START_SCRIPT` feature gate — the same mechanism (and legacy-fallback shape) as the Admin Portal bridge in `AdminPortalActivity`. The Android equivalent of iOS's `WKUserScript` at `.atDocumentStart`.
- The authorize URL is embedded as a JSON-quoted JS string literal, so a crafted URL cannot break out of the script (unit-tested).
- **No `max_age` change needed** here — Android already emits integer seconds (`Duration.inWholeSeconds`); the `60.0` float bug was iOS-specific.
- A minimal `[step-up] routed…` line is surfaced to logcat via a `WebMessageListener` (same pattern as the passkey listener), since the box JS console is invisible in release.

## Verification

- ✅ `:android:compileDebugKotlin` → **BUILD SUCCESSFUL**.
- ✅ `:android:testDebugUnitTest` → new **`StepUpWebDriverTest`** (2/2 — contract keys + URL-escaping), and existing **`StepUpAuthenticatorTest`** (7/7) and **`AuthorizeUrlGeneratorTest`** (16/16) still green.
- ✅ `:android:detekt` → **BUILD SUCCESSFUL**.
- ⏳ On-device E2E (blank page → MFA challenge) still to run on an emulator; the iOS counterpart (#278) is verified on-device against the deployed box, and this mirrors that logic.

**Requires** the box-side render fix ([admin-box#2865](https://github.com/frontegg/admin-box/pull/2865), deployed in 7.118.0): the driver routes the WebView to the step-up route; #2865 is what renders `StepUpPage` there. Both halves are needed.

Relates to **FR-24939**.
## Problem

Retail Success reported that on their Dev environment (5-minute token TTL), the Android SDK stops refreshing — after ~4 minutes the token silently expires and the user is logged out. The logcat shows the refresh timer firing once, logging `refreshIdempotent: token already refreshed by concurrent call, skipping`, then nothing — **no new timer is ever scheduled.**

## Root cause

`refreshIdempotent`'s "already refreshed by concurrent call" guard used **remaining TTL** as a proxy for "someone else refreshed":

```kotlin
val offset = decoded.exp.calculateTimerOffset()
if (offset > 0) { /* skip */ }   // offset > 0  ⟺  > 20s of TTL left
```

But the refresh timer is scheduled at **80% of TTL**, so it fires with ~20% of the TTL still on the clock. For any token with **TTL > 100s**, that remaining 20% is still above the 20-second refresh floor, so `offset > 0` and the timer mistakes the very token it was scheduled for as a concurrent refresh. It skips the network call **and never reschedules** — the next timer is only armed after a real refresh inside `sendRefreshTokenInternal`. The refresh loop dies.

Only tokens with TTL ≤ 100s ever reached the branch that actually refreshes. Introduced with the `refreshIdempotent` mutex refactor (offline-mode session recovery, FR-24189).

### Logcat, to the millisecond
| Evidence | Value |
|---|---|
| JWT `exp − iat` | 300s (5-min Dev TTL) |
| `Start Timer task` | 238566 ms ≈ 0.8 × remaining |
| `Job started` | ~4 min later, ~59s TTL still left → `offset` ≈ 47s > 0 → guard trips |
| `already refreshed… skipping` → *(no further `Start Timer`)* | refresh loop dead |

## Fix

Dedup on **token identity**, not TTL. Snapshot the access token before contending for `refreshMutex`; skip only if the live token actually *changed* while we waited on the lock (a genuine concurrent refresh). Standard double-checked locking:

```kotlin
val tokenAtRequest = accessToken.value               // before the mutex
refreshMutex.withLock {
    if (!force && wasRefreshedConcurrently(tokenAtRequest, accessToken.value)) { /* skip */ }
    ...
}
```

- A burst of callers all snapshot `T0`; the winner refreshes to `T1`; the rest see `T1 ≠ T0` and correctly skip. Concurrent-refresh de-duplication is preserved.
- `force = true` (tenant switch) still bypasses the guard entirely — unchanged.
- The resume/lifecycle path (`refreshTokenWhenNeeded`) already gated on `offset <= 0` and never relied on the guard as a throttle, so there's no over-refresh.

## Tests

- **Regression test** — drives `refreshIdempotent` end-to-end with a 300s TTL and asserts `api.refreshToken` fires **and** the timer is rescheduled. Verified it fails on the old code (`Api.refreshToken(...) was not called` while `refreshIdempotent` still returns `true` — the silent-failure signature) and passes on the fix.
- **Unit tests** for `wasRefreshedConcurrently`: unchanged token → refresh (the bug), swapped token → skip (dedup preserved), null baseline → never skip.

Full Android unit suite: **601 tests, 0 failures.**

🤖 Generated with [Claude Code](https://claude.com/claude-code)
## Problem

In `docs/getting-started.md`, the **Configure build config fields** section shows `manifestPlaceholders` at the *top* of `defaultConfig`, with `applicationId` never declared in the snippet:

```groovy
android {
    defaultConfig {
        manifestPlaceholders = [
                "package_name"      : applicationId,   // ← reads applicationId
                "frontegg_domain"   : fronteggDomain,
                "frontegg_client_id": fronteggClientId
        ]
        ...
    }
}
```

Gradle evaluates `defaultConfig { }` top-to-bottom. When a customer pastes this block above their existing `applicationId` line, `applicationId` resolves to `null`, the `package_name` placeholder is empty, and the AAR manifest merge fails. The Kotlin variant (`manifestPlaceholders["package_name"] = applicationId.toString()`) has the same ordering problem.

## Fix

Declare `applicationId` first in both the Groovy and Kotlin snippets, with a comment explaining that it must precede `manifestPlaceholders` because the `package_name` placeholder reads it.

This matches the SDK's own working example apps, which all declare `applicationId` before `manifestPlaceholders`:
- `app/build.gradle:34`
- `embedded/build.gradle:34`
- `applicationId/build.gradle:32`
- `multi-region/build.gradle:32`

Docs-only change. Mirrors the equivalent fix in frontegg-react-native PR #79.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
## Problem

Step-up authentication (and any embedded re-auth that already has a session) renders a **blank page / forces a second login** in embedded mode — the same bug class the Admin Portal had before its native-token-bridge fix.

**Root cause:** the embedded login box (`FronteggWebView` → `/oauth/authorize`) injects `window.FronteggNativeBridgeFunctions` **without `getTokens`** (`FronteggWebClient.onPageFinished`), and `FronteggNativeBridge` had no `getTokens` handler. With no native-token path, the login box (`@frontegg/redux-store` ≥ 7.113.0) falls back to the cookie token-refresh — which 401s inside the WebView → blank box. The `getTokens` bridge previously lived **only** in `AdminPortalActivity`, so step-up never benefited.

## Fix

Mirror the Admin Portal bridge into the embedded login WebView:
- `FronteggWebClient.kt` — advertise `"getTokens": true` in the injected capabilities; add `currentUrlMainSafe()` + `evaluateJavascriptOnMain()` helpers (the bridge has no direct WebView reference).
- `FronteggNativeBridge.kt` — `@JavascriptInterface getTokens(callbackId)` → trusted-origin check → `refreshTokenAndWait()` → resolve `{accessToken, refreshToken}` into the redux-store's `window.FronteggNativeBridgeCallbacks` registry (same protocol as `AdminPortalActivity`).

Notes:
- **Fresh login is unaffected** — with no session, `getTokens` rejects `no_tokens` and the box falls through to the normal login flow.
- **Step-up still challenges** — `acr_values`/`max_age` in the authorize URL drive the MFA prompt; `getTokens` only fixes the bootstrap.
- The **hosted (Custom Tab)** path can't host a native bridge; step-up that must reuse the session should run in embedded mode.

## Verification
- ✅ `./gradlew :android:compileDebugKotlin` → **BUILD SUCCESSFUL**.
- ⏳ End-to-end (react-native step-up with no blank page) needs a release + bumping the android SDK pin in [frontegg-react-native#73](https://github.com/frontegg/frontegg-react-native/pull/73).

Relates to **FR-24939**. iOS equivalent: [frontegg-ios-swift#275](https://github.com/frontegg/frontegg-ios-swift/pull/275).

🤖 Generated with [Claude Code](https://claude.com/claude-code)

## v
- Admin Portal hosted mode support

## v
- Bridge the SDK refresh token into the embedded admin portal WebView so users on browser-based login flows (social / SAML / OIDC) are no longer prompted to log in a second time; clear `fe_refresh_*` cookies on logout 
- Port the full entitlement decision logic from the web SDK — evaluate the `/user-entitlements` catalog (linked plans, feature flags, and per-rule condition graphs) instead of only checking whether a feature/permission key is present
- Redact OAuth authorization code, PKCE values, and tokens from URL log lines before they reach logcat 

## v
## Summary

Redact OAuth-sensitive query parameters from URLs before they reach `Log.d`.

A customer pentest report flagged that the SDK emits the OAuth authorization code (and PKCE `code_verifier` / `code_challenge`, `nonce`, `state`) in plaintext to logcat during the login flow. The leak occurred in the embedded WebView path — host apps that suppress their own logs in production (e.g. Capacitor's `loggingBehavior: 'production'`) had no way to silence the SDK's output, so the code remained visible to anything with ADB access.

- New `LogUrlSanitizer` utility strips sensitive query-parameter values (`code`, `state`, `code_verifier`, `code_challenge`, `nonce`, `access_token`, `refresh_token`, `id_token`, bearer / authorization tokens, etc.) and replaces them with `[redacted]` before logging.
- Applied at every `Log.d` call site that prints a URL: `EmbeddedAuthActivity`, `AuthorizeUrlGenerator`, `FronteggWebClient.shouldInterceptRequest`, `AdminPortalActivity`.
- Key set kept in sync with `SentryHelper` so logcat and Sentry redact the same parameters.

## v
- Minor enhance switch tenants logic
- route `switchTenant` through `updateStateWithCredentials` and fires `loadEntitlements(forceRefresh = true)` on the new tenant's token

## v
Sentry's automatic network breadcrumbs have been disabled

## v
## Summary

- Threads a `force` flag through `refreshIdempotent` so callers that need a real refresh — even when the current access token has time on its TTL — can bypass the v1.3.23 skip-if-not-expired guard.
- `switchTenant` now passes `force = true`. Frontegg access tokens are tenant-bound (tenantId is a JWT claim), so a tenant switch must always re-mint tokens.
- Adds `switchTenant forces a token refresh even when the existing access token is still valid by TTL` to `FronteggAuthServiceTest` as the regression reproduction.

## v
## Summary

Adds **Admin Portal BETA version** to the SDK. Opens `${baseUrl}/oauth/portal?appId=<applicationId>` in a `WebView` that shares the process-wide `CookieManager` with the SDK's login `WebView` so authenticated users don't see a second login.

- New public surface: `AdminPortalActivity.open(activity)` from anywhere in the host app
- Demo app: "Open Admin Portal" button on the home screen

## Implementation details
**`?appId=` is required.** Without it, the portal renders "Application not found" after login when the SDK was configured with an application context

## v
## Summary

- trust server tenant on fresh login instead of stale cache
- gate AndroidDebugConfigurationChecker on host app debuggable flag

## v
## Summary

- A failed network-quality probe during `initializeSubscriptions()` was being treated as an auth failure when `enableOfflineMode` is disabled (the default), calling `clearCredentials()` and wiping a perfectly valid session.


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











