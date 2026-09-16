## v1.3.41

Features:

- Added runtime theme and copy overrides for the embedded login box. `FronteggInnerStorage.loginBoxThemeOptions` and `.loginBoxLocalizations` take the same shapes as `themeV2` and `localizations` in the environment's login-box configuration, and are deep-merged over it — keys the override does not mention keep whatever the environment defines. This covers appearance that is only known at runtime, such as a white-labeled app resolving each brand's logo and colours from its own backend, which per-environment configuration cannot express. Both default to `null`, and setting `null` clears an override, so apps that do not set them are unaffected. Embedded mode only. `LoginBoxCustomization.isSupported()` reports whether the device's WebView provider can apply overrides; on one that cannot, the login box renders the environment's own branding. Requires a hosted login box that applies host-supplied overrides. ([#286](https://github.com/frontegg/frontegg-android-kotlin/pull/286))

Bug fixes:

- Fixed the SDK retrying forever when a refresh token was rejected. A 401 from the token endpoint means the refresh token itself is no longer valid, so no retry can succeed, but it was treated as a temporary failure: the session stayed marked as signed in, and with offline mode enabled each failed attempt queued another, with no limit and no backoff — one captured case reached around 1,780 failed refreshes in 14 minutes with the rate still climbing. A rejected refresh token now ends the session immediately, which also cancels the refresh timer and drains any queued retries. Temporary failures such as network errors keep their existing retry behaviour. No app or configuration changes are needed. 

<!-- CURSOR_SUMMARY -->
---

> [!NOTE]
> **Low Risk**
> This PR only changes version, changelog content, and changelog automation; no SDK runtime code is modified in the diff.
> 
> **Overview**
> **Release v1.3.41** bumps the published SDK version in `android/build.gradle` and prepends `CHANGELOG.md` with the v1.3.41 notes (runtime embedded login-box theme/localization overrides via `FronteggInnerStorage`, and ending the session immediately when the token endpoint returns **401** on refresh instead of retrying indefinitely).
> 
> The release PR workflow now **strips `<!-- CURSOR_SUMMARY -->` blocks** from the GitHub PR body before that text is written into `CHANGELOG.md`, so review-bot summaries are not copied into the changelog. `CHANGELOG.old.md` only loses trailing blank lines.
> 
> <sup>Reviewed by [Cursor Bugbot](https://cursor.com/bugbot) for commit bb53a8bddcfd8e7e8341a9fdb97d5c9c8ee9bbb4. Bugbot is set up for automated code reviews on this repo. Configure [here](https://www.cursor.com/dashboard/bugbot).</sup>
<!-- /CURSOR_SUMMARY -->
Four faults in the release automation. Three surfaced in the #286 merge; the fourth is visible right now on #288.

## 1. Publishing ran on every non-release merge

`onPullRequestMerged` set an alpha version and then ran:

```
./gradlew publishToSonatype closeSonatypeStagingRepository releaseSonatypeStagingRepository
```

So merging **any** feature PR pushed an artifact to Maven Central — irreversible, and not something a feature merge should do. That job's purpose is to open the release PR; `onReleaseMerged` already publishes when the release PR merges, which is the only place it should happen.

Removed the publish, and the `Set Alpha Version` step that existed solely to feed it. The E2E trigger is unaffected — it passes `incremented-patch-version`, not the alpha.

## 2. The generated changelog fed itself

`onReleasePullRequestUpdated` copies the release PR body verbatim into `CHANGELOG.md`. Review bots append their summary to that same body. So the summary was written into the changelog, that commit was reviewed, a fresh summary appended, and the job ran again on the edit.

Four `chore(release): Updated CHANGELOG.md` commits landed on `release/next` this way, the last containing a review note that describes the previous review note. `CHANGELOG.md` on that branch currently carries a `CURSOR_SUMMARY` block in the middle of the v1.3.41 release notes.

Now strips the delimited block before writing. Nothing else about the body is filtered.

## 3. The release-notes heading was always empty

The fallback built:

```yaml
DESCRIPTION="## v${{ steps.incremented-version.outputs.result }}"
```

`steps.incremented-version` is the **Set Alpha Version** step, which runs *later* in the job, so it is empty here; and the action outputs `version`, not `result`. The heading therefore rendered as a bare `## v`. Now reads `incremented-patch-version.outputs.version`, as the commit message, PR title and E2E trigger already do.

## 4. Explicit permissions — and a correction on why #286's merge failed

The merge of #286 failed pushing `release/next`:

```
remote: Permission to frontegg/frontegg-android-kotlin.git denied to github-actions[bot].
fatal: ... The requested URL returned error: 403
```

**An earlier revision of this PR attributed that to a tightened org-wide default. That was wrong**, and worth stating plainly because it changes what this PR does and does not fix:

| PR | Branch lives in | `onPullRequestMerged` |
|---|---|---|
| #287 | `frontegg/…` (internal) | succeeded, 02 Sep |
| #286 | `airowe/…` (**fork**) | **403**, 16 Sep |
| #288 | `frontegg/…` (internal) | `onReleasePullRequestUpdated` commits fine, with no `permissions:` block at all |

`GITHUB_TOKEN` is read-only for `pull_request` events originating from a fork. That is a platform cap, and **`permissions:` cannot raise it** — so the blocks added here do *not* fix the #286 case, and I am not claiming they do.

They are still worth having: they declare least privilege explicitly and make the jobs robust if the org default is ever tightened. Each gets only what it needs — `contents` + `pull-requests` for the release PR, `contents` + `issues` for the tag, release and PR comment.

**The fork case needs a separate decision**, not made here: either trigger release-PR creation from a `push` to `master` rather than `pull_request: closed`, or give that job an App token. Worth its own ticket.

## What this means for releasing #288

#288 is on `release/next`, an internal branch, so `onReleaseMerged` gets a writable token and should tag and publish without this PR. **This PR is not a prerequisite for that release** — contrary to what the earlier revision said. It is a prerequisite for the changelog not re-corrupting itself, and for future feature merges not publishing to Maven Central.

Note that fixing #2 and #3 does not repair what is already on `release/next`; that file needs cleaning by hand once this lands, or the next body edit will regenerate it from the body as-is.

## Verification

| | permissions | publishes | strips review block |
|---|---|---|---|
| `onPullRequestMerged.yaml` | `contents`, `pull-requests` | no | — |
| `onReleaseMerged.yaml` | `contents`, `issues` | **yes** | — |
| `onReleasePullRequestUpdated.yaml` | (unchanged) | no | **yes** |

All three parse. No dangling `incremented-version` references remain. The strip was checked against a sample body carrying the block.

These are workflow-permission and release-path changes; they want a look from whoever owns CI.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01ALKJ6thT3mssEYmEsmR5r2

<!-- CURSOR_SUMMARY -->
---

> [!NOTE]
> **Medium Risk**
> Changes when artifacts hit Maven Central and how release notes/changelogs are generated; misconfiguration could block releases or skip publishing until release PR merge.
> 
> **Overview**
> **Release CI is tightened so Maven publish and changelog generation only run at the right times.**
> 
> `onPullRequestMerged` no longer bumps an alpha Gradle version or runs `publishToSonatype` / staging release on every feature merge—those steps are removed so only release-PR creation, changelog prep, and E2E trigger remain. The fallback release-PR description heading now uses `incremented-patch-version.outputs.version` instead of a later, wrong step output (which produced bare `## v`).
> 
> `onReleasePullRequestUpdated` and `onReleaseMerged` strip the `<!-- CURSOR_SUMMARY -->` … `<!-- /CURSOR_SUMMARY -->` block from the PR body before copying it into `CHANGELOG.md` or GitHub release notes, breaking the edit→changelog→re-review feedback loop.
> 
> Both merge workflows declare explicit `permissions` (`contents` + `pull-requests` for release PR creation; `contents` + `issues` for tag, release, and comment on release merge).
> 
> <sup>Reviewed by [Cursor Bugbot](https://cursor.com/bugbot) for commit 933dc4c0523b97b5aef2330e43049487b0c8592e. Bugbot is set up for automated code reviews on this repo. Configure [here](https://www.cursor.com/dashboard/bugbot).</sup>
<!-- /CURSOR_SUMMARY -->

## v1.3.40

Bug fixes:

- Hardened the authentication screens against deep links from untrusted sources. An incoming link is now checked against the Frontegg domain the app is configured for before anything is opened, and the account links (password reset, invitation, unlock and the rest) are recognised by their address path rather than by matching text anywhere in the link. Apps pick this up automatically; no app or configuration changes are needed. (FR-26895 — [#285](https://github.com/frontegg/frontegg-android-kotlin/pull/285))

## v1.3.40

Bug fixes:

- Hardened the authentication screens against deep links from untrusted sources. An incoming link is now checked against the Frontegg domain the app is configured for before anything is opened, and the account links (password reset, invitation, unlock and the rest) are recognised by their address path rather than by matching text anywhere in the link. Apps pick this up automatically; no app or configuration changes are needed. (FR-26895 — [#285](https://github.com/frontegg/frontegg-android-kotlin/pull/285))

## v1.3.39

Bug fixes:

- Fixed sign-in failing to return to the app for environments whose Frontegg base URL includes a path — for example `https://api.example.com/fe-auth`, where a shared domain routes a prefix through to Frontegg. The OAuth callback was built without that path, so it matched neither the asset links published for the app nor the redirect URI registered for it, and the user was left in the browser on a page the shared domain does not serve after already authenticating. The callback now carries the path, and the previous form keeps working so sessions issued before upgrading are unaffected. Environments whose base URL has no path are unchanged. (FR-26743 — [#282](https://github.com/frontegg/frontegg-android-kotlin/pull/282))

  Note for apps on such an environment: the SDK now sends the right callback, but the App Links intent filter is still declared with `android:host` and a root path, which cannot express the prefix. Until that is addressed those apps also need an intent filter matching their own prefixed callback. Tracked in FR-26743.

## v1.3.38

Bug fixes:

- Fixed the app crashing when the device could not reach the network while the SDK was loading entitlements. A routine DNS failure — most often while the app was in the background — was left unhandled and terminated the host app instead of being treated as a failed load. No app or configuration changes are needed

## v1.3.37

Bug fixes:

- Fixed the account-unlock link in a lockout email opening a web browser instead of the app, which left the user unable to complete the unlock and get back to signing in. Apps pick this up automatically; no app or configuration changes are needed. (FR-26330 — [#276](https://github.com/frontegg/frontegg-android-kotlin/pull/276))

## v1.3.36

Bug fixes:

- Fixed a crash that could occur when refreshing tokens or updating credentials from the app's main thread.
- Fixed embedded login getting permanently stuck after the app was killed and reopened mid-login.
- Hardened the embedded login webview: native login actions are now restricted to trusted content, and sensitive response data is no longer written to device logs.
- Strengthened login security with a longer, standards-compliant PKCE code verifier.

## v1.3.35

- Fixed: embedded step-up renders the MFA challenge instead of a blank page — the embedded login WebView now exposes the native `getTokens` token bridge (the same protocol the Admin Portal uses), and a new step-up web driver routes the hosted login box to its step-up page and completes with an elevated (stepped-up) token via the existing OAuth callback. Requires hosted login box ≥ 7.118.0. (FR-24939 — [#262](https://github.com/frontegg/frontegg-android-kotlin/pull/262), [#268](https://github.com/frontegg/frontegg-android-kotlin/pull/268))
- Fixed: token refresh no longer stalls on short-TTL environments — the concurrent-refresh guard in `refreshIdempotent` now keys on token identity instead of remaining TTL, so the refresh timer is always rescheduled. ([#267](https://github.com/frontegg/frontegg-android-kotlin/pull/267))
- Changed: removed the deprecated `startActivityForResult` usage — auth activities now launch with `startActivity`; auth results were already delivered via callbacks, so there is no integrator-facing change. (FR-19725 — [#266](https://github.com/frontegg/frontegg-android-kotlin/pull/266))
- Changed: the internal `detekt-rules` tooling module now targets JVM 1.8, matching all other modules (tooling-only; the published SDK already targeted 1.8). (FR-25496 — [#264](https://github.com/frontegg/frontegg-android-kotlin/pull/264))
- Added: embedded step-up E2E coverage (`testEmbeddedStepUpMfaChallenge`) exercising the full `acr_values` → step-up page → elevated-token flow against the mock auth server. ([#268](https://github.com/frontegg/frontegg-android-kotlin/pull/268))
- Docs: getting-started now declares `applicationId` before `manifestPlaceholders`, avoiding unresolved-placeholder build errors. ([#263](https://github.com/frontegg/frontegg-android-kotlin/pull/263))

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
