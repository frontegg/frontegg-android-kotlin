## v
## Summary

A customer pentest found the Frontegg Android SDK emits the OAuth authorization code in plaintext to logcat during the embedded WebView login flow (e.g. `code=<value>` visible in `Log.d` output). The customer's host app suppresses its own logs in production via Capacitor's `loggingBehavior: 'production'`, but the SDK was logging unconditionally — exposing the authorization code to anything with ADB access on a device that's been compromised or left in dev mode.

This PR redacts OAuth-sensitive query-parameter values from URLs before they reach `Log.d`.

## What was leaking

| File | Line | What was logged |
|---|---|---|
| `EmbeddedAuthActivity.kt` | 212 | full callback URL with `?code=...&state=...` |
| `EmbeddedAuthActivity.kt` | 242 | same, for social-login redirect path |
| `EmbeddedAuthActivity.kt` | 255 | same, for OAuth callback path |
| `EmbeddedAuthActivity.kt` | 355 | same, for the `onResume` retry path |
| `AuthorizeUrlGenerator.kt` | 135 | generated authorize URL with `code_challenge` + `nonce` |
| `FronteggWebClient.kt` | 466/468/473 | every URL the WebView loads (incl. OAuth callback redirects) |
| `AdminPortalActivity.kt` | 112 | admin portal URL (precautionary) |

Sentry breadcrumbs were already redacted via `SentryHelper` — only logcat was leaky.

## Approach

New `internal object LogUrlSanitizer` in `com.frontegg.android.utils`:
- Pure-Kotlin string ops (no `android.net.Uri` dep) → cheap, unit-testable on the JVM, safe to call during early SDK startup.
- Strips values for `code`, `state`, `code_verifier`, `code_challenge`, `nonce`, `access_token`, `refresh_token`, `id_token`, `device_token`, `mfa_token`, plus any key containing `token`, `secret`, `password`, `authorization`, `credential`, `signature`, `apikey`, `access_key`.
- No-op on URLs without a query → can be applied unconditionally without losing diagnostic value.
- Key set mirrors `SentryHelper.breadcrumbKeyIsSensitive` so logcat and Sentry redact the same set going forward.

Applied at every URL `Log.d` site listed above.

## Why not just gate on `BuildConfig.DEBUG`?

Two reasons:
1. The SDK module's `BuildConfig.DEBUG` is always `false` in published artifacts — gating on it would silence the logs entirely, including in customer apps' debug builds, where the SDK logs are genuinely useful for diagnosing integration issues.
2. Defense-in-depth: even a developer using a debug build of their own app shouldn't be able to accidentally screenshot / share a log line containing a real OAuth code.

A runtime `enableDebugLogging` flag is a reasonable follow-up but is out of scope here — this PR is the focused leak fix.

## Test plan

- [x] New unit test `LogUrlSanitizerTest` (11 cases): null/empty, no query, OAuth callback, PKCE, tokens, case-insensitive, fragment preservation, key-without-value, mixed safe + sensitive params
- [x] `./gradlew :android:testDebugUnitTest --tests "com.frontegg.android.utils.LogUrlSanitizerTest"` passes
- [x] `./gradlew :android:testDebugUnitTest --tests "com.frontegg.android.utils.*"` (including `AuthorizeUrlGeneratorTest`, `SentryHelperTest`) still passes
- [ ] Manual: capture logcat during embedded login and confirm the previously-leaking lines now show `code=[redacted]`

## Risk

- Sanitizer is purely additive at log sites — none of the actual URL handling, navigation, or token-exchange logic is touched.
- For URLs without a query string, sanitizer is a pass-through.
## Summary

Closes the decision-logic gap behind FR-24821. The mobile SDK was only checking whether a feature/permission *key* was present in the `/user-entitlements` response — but the response is a **catalog** (features + their linked plans, expiry, feature flags, and per-rule condition graphs), not a list of "what the user has." Web does the full evaluation; mobile didn't. Result: a feature like `sso` linked to a plan with `defaultTreatment: "false"` came back as `isEntitled = true` on mobile even though web (correctly) said the user wasn't entitled.

This PR ports [`@frontegg/entitlements-javascript-commons`](https://www.npmjs.com/package/@frontegg/entitlements-javascript-commons) (the canonical evaluator the React / JS / Next.js SDKs all run on) to Kotlin.

**Complementary to [#254](https://github.com/frontegg/frontegg-android-kotlin/pull/254)** — that PR handles cache invalidation on tenant switch and remains valid. This PR closes a different gap (the decision logic itself).

## Why

Yonatan's reproduction from FR-24821:

```json
{
  "features": {
    "sso": {"planIds": ["ID_1"], "expireTime": null}
  },
  "plans": {
    "ID_1": {"defaultTreatment": "false"}
  }
}
```

Pre-fix mobile saw `"sso"` in `features` → `isEntitled = true`. Web (correctly) follows `sso.planIds[0]` → `plans.ID_1.defaultTreatment` → `"false"` → not entitled.

## What landed

| Layer | Files |
|---|---|
| Models (TS shapes ported 1:1) | `entitlements/UserEntitlementsContext.kt` — `FeatureDetail`, `Plan`, `FeatureFlag`, `Rule`, `Condition`, `Treatment`, `ConditionLogic`, `Operation` |
| Operations matrix | `entitlements/operations/Operations.kt` — string (`in_list`/`starts_with`/`ends_with`/`contains`/`matches`), numeric (`equal`/`gt`/`gte`/`lt`/`lte`/`between`), boolean (`is`), date (`on`/`on_or_after`/`on_or_before`/`between`); sanitizer + handler pair per op, fails closed on type mismatch |
| Evaluators | `entitlements/ConditionEvaluator.kt` → `RuleEvaluator.kt` → `PlanEvaluator.kt` / `FeatureFlagEvaluator.kt` → `IsEntitledToFeature.kt` (direct + flag + plan-targeting chain) → `IsEntitledToPermission.kt` (wildcard match + linked-feature roll-up) |
| Attribute prep | `entitlements/AttributesPreparer.kt` — merges custom + JWT claims with the same `frontegg.` / `jwt.` prefix scheme web uses; a rule with `attribute = "frontegg.tenantId"` reads `jwt.tenantId` via the mapper |
| Permission matching | `entitlements/PermissionMatcher.kt` — anchored wildcard regex with metachar escaping (`fe.secure.*` matches `fe.secure.read.users`; `fe.secure` does NOT match `feXsecure`) |
| Parser | `entitlements/UserEntitlementsParser.kt` — lenient JSON → context; drops malformed sub-objects rather than failing the whole parse |
| Wiring | `Api.kt` (parse full context, keep legacy `featureKeys`/`permissionKeys` for backcompat), `EntitlementsService.kt` (`checkFeature`/`checkPermission` now route through the chain with an `Attributes` bag), `FronteggAuthService.kt` (decode JWT claims from current access token via new `JWTHelper.decodeClaims`, thread them + host-app `customAttributes` through `Attributes` — per Yonatan: attributes "should be in JWT") |

## Backwards compatibility

- Existing host-app code reading `auth.entitlements.state.featureKeys` / `permissionKeys` still works — those sets are still populated (now from the catalog rather than "what the user has"; the demo's count badge still renders).
- `Entitlement.justification` adds `BUNDLE_EXPIRED` to match web's `NotEntitledJustification` enum. The existing values (`NOT_AUTHENTICATED`, `ENTITLEMENTS_DISABLED`, `ENTITLEMENTS_NOT_LOADED`, `MISSING_FEATURE`, `MISSING_PERMISSION`) are unchanged.

## Tests

Five new test files. The FR-24821 reproduction lives in `IsEntitledToFeatureTest.fr_24821 sso with defaultTreatment false is not entitled` and `UserEntitlementsParserTest.FR_24821 minimal response`.

| Test | What it covers |
|---|---|
| `ConditionEvaluatorTest` | every operation kind + negate + malformed-payload + type-mismatch + null-attribute |
| `PlanAndFeatureFlagEvaluatorTest` | `defaultTreatment`, rule precedence, flag on/off |
| `IsEntitledToFeatureTest` | direct / flag / plan chain priorities, `BUNDLE_EXPIRED` aggregation, **FR-24821 repro** |
| `IsEntitledToPermissionTest` | wildcard matching, regex-meta escaping, linked-feature roll-up (granted permission on a not-entitled feature is denied) |
| `UserEntitlementsParserTest` | happy path (FR-24821 shape), `expireTime` nulls, unknown operations dropped, malformed sub-objects dropped without crashing the parse |

Existing `EntitlementsServiceTest` tests for "key present = entitled" updated to use the new `UserEntitlementsContext` path (those old tests literally encoded the bug).

## Verification

- [x] `./gradlew :android:testDebugUnitTest --tests "com.frontegg.android.entitlements.*"` — 43 new tests, 0 failures
- [x] `./gradlew :android:testDebugUnitTest` — full suite **548 tests / 0 failures**
- [ ] Manual repro in `:app` demo (Tenant A with SSO → switch to Tenant B without SSO → tap Load Entitlements → expect `Not entitled (MISSING_FEATURE)` for `sso`)

## Related

- [#254](https://github.com/frontegg/frontegg-android-kotlin/pull/254) — cache invalidation on tenant switch (complementary, still valid)
- iOS port forthcoming as a separate PR mirroring this structure

🤖 Generated with [Claude Code](https://claude.com/claude-code)
## Problem

Customers reported being forced to log in a second time when opening the embedded admin portal, even though the SDK already had a valid session. Reproduced on Android (and iOS — see companion PR).

## Root cause

`AdminPortalActivity`'s WebView shares the process-wide `CookieManager`, which contains:

- ✅ Cookies set by the SDK's embedded login WebView (password / embedded social)
- ❌ **Not** cookies from Chrome Custom Tabs (used for social / SAML / OIDC / browser SSO)

Android deliberately walls Chrome's per-app cookie jar off from the host app's WebView. Users on a browser flow had no `fe_refresh_*` cookie in CookieManager → portal rendered its own login form.

Additionally, `FronteggAuthService.logout()` did not clear any cookies — so a stale `fe_refresh_*` cookie from any prior flow (embedded login OR the bridge below) could silently resurrect the session next time the portal opened.

## Fix

Two changes:

### 1. Bridge on portal open (`AdminPortalActivity.kt`)

Before `webView.loadUrl("/oauth/portal")`, if the SDK is authenticated, write `fe_refresh_<appId-or-clientId>` into `CookieManager` scoped to the baseUrl host. Cookie-name format matches the Frontegg Next.js SDK (`frontegg-nextjs/packages/nextjs/src/utils/cookies/index.ts`) and the iOS SDK's `AdminPortalWebView.refreshCookieName` — all three platforms now agree on a single cookie format the auth server reads.

- `refreshCookieName(clientId, applicationId)` — prefers `appId` when present (multi-app workspaces), falls back to `clientId`. Dashes stripped.
- `buildRefreshCookieValue(...)` — returns the cookie header + scoping URL. Returns null when there is no refresh token (user not logged in) or baseUrl is malformed; the portal falls back to its own login.
- `flush()` is called after `setCookie` so the in-memory cookie is persisted before the WebView reads it.

### 2. Clear on logout (`FronteggAuthService.kt`)

New `clearRefreshCookiesForBaseUrl(baseUrl)`:
- Reads the cookie header for baseUrl
- Picks out every entry whose name starts with `fe_refresh`
- Expires each one by setting it with `Max-Age=0` scoped to the same URL
- Flushes

This is the Android equivalent of the iOS logout flow's existing regex-based cookie cleanup.

## Companion iOS PR

[frontegg/frontegg-ios-swift#267](https://github.com/frontegg/frontegg-ios-swift/pull/267) — same fix shape on iOS. iOS already had a regex-based logout cookie sweep matching `^fe_refresh`, so it didn't need a new logout path — just a regression test confirming the bridged cookie name matches the existing regex.

## Tests

- 12 new unit tests in `AdminPortalActivityTest` covering cookie name computation, nil/empty/malformed inputs, HTTPS vs HTTP, appId-vs-clientId precedence, and subpath-stripping in the scoping URL.
- 2 new unit tests in `FronteggAuthServiceTest` verifying that logout expires every `fe_refresh_*` cookie (and only those — unrelated cookies are untouched) and that the no-op path doesn't call `setCookie` or `flush`.

All existing tests still pass.

## Test plan

- [ ] CI: full unit-test suite passes
- [ ] Manual: log in via Google social (Chrome Custom Tabs) → open admin portal → confirm no second login prompt
- [ ] Manual: log in via embedded password → open admin portal → still works (regression check)
- [ ] Manual: open admin portal while logged out → portal's own login form appears (current behavior preserved)
- [ ] Manual: log in → open portal → close → logout → re-open portal → portal's login form (not the previously-bridged session)

## Out of scope (follow-ups)

- Custom `cookieDomain` support — current implementation scopes to baseUrl host.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

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











