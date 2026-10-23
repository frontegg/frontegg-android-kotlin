## v1.3.44
## Summary
- Upgrade the docs site from docsify 4 to **5.0.0**. The 4.x search plugin writes the `s` query parameter into the search input's HTML without escaping it; 4.13.1 (the last 4.x release) is still affected, and 5.0.0 escapes the value.
- Pin every CDN asset to an exact version over https. The search plugin was previously loaded from an unversioned URL (`npm/docsify/lib/plugins/search.min.js`), so the site ran whatever version jsDelivr resolved.
- docsify 5 moved its files from `lib/` to `dist/` and split the theme into `core.min.css` + `addons/vue.min.css`; the `$docsify` config is unchanged.

Only `docs/index.html` changes. The GitHub Pages site redeploys from `docs/` on merge to `master`.

## Test plan
- [x] Served `docs/` locally: pages and sidebar render, no console errors
- [x] Typed search returns results
- [x] A crafted `?s=` value is rendered as inert text and no markup is injected
- [ ] After merge, re-check the live site with the same crafted `?s=` value

Note: opening a URL that already contains `?s=<term>` no longer pre-runs a search under docsify 5; typing in the search box works as before.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Jira: **FR-25956** (Android half of opt-in DPoP token binding).

## What changes

- `DPoPKeyProvider` / `AndroidKeyStoreDPoPKeyProvider`: a non-exportable P-256 key in the Android Keystore. An entry the Keystore reports as unrecoverable is replaced.
- `DPoPProofGenerator`: RFC 9449 proofs (`typ dpop+jwt`, ES256, public `jwk`, `jti`, `htm`, `htu`, `iat`, optional `ath` and `nonce`), the RFC 7638 JWK thumbprint, an in-memory key cache, and key rotation. An `InvalidKeyException` rotates the key that failed; a `ProviderException` only drops the cached handle.
- `DPoPEncoding`: base64url, SHA-256, and DER to JOSE signature conversion.
- `DPoPProvider`: process-wide enabled flag and generator.

Nothing calls this code yet. The interceptor and the `enableDPoP` flag come in part 2.

## Testing

- Unit tests: `DPoPEncodingTest` (9) and `DPoPProofGeneratorTest` (11), covering DER to JOSE, proof claims and signature verification, the RFC 7638 thumbprint vector, key caching, rotation, and signing failures.
- E2E coverage is in #301.

Part 1 of 3; split from #301 to keep PRs under 20 files. Next: https://github.com/frontegg/frontegg-android-kotlin/pull/307 (interceptor and opt-in flag), then #301 (E2E).

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_018X5mwfjstoxdFhCjqvEoo7

## Summary

Sometimes, on a cold start, embedded login opened `https://oauth/authorize` with an empty client ID and showed the "Check your internet connection" page until the app restarted. This was caught as a flaky test in the Capacitor Android E2E suite ([frontegg/frontegg-ionic-capacitor#118](https://github.com/frontegg/frontegg-ionic-capacitor/pull/118)), but the cause is in this SDK.

### Cause

1. `FronteggAuthService`'s constructor starts loading feature flags on a background thread.
2. `FeatureFlags.loadFlags` looked the auth service up through `Context.fronteggAuth`. That happens while `FronteggAppService` is still being constructed, before `FronteggApp.instance` is assigned.
3. When the background thread won, the lookup found no instance and ran the default initialization from BuildConfig. That overwrote the shared storage (`FronteggInnerStorage`) with the BuildConfig values and created a second auth service.

**Who's affected:**
- Apps that configure the SDK in code, as the React Native, Flutter and Capacitor plugins do, have no `FRONTEGG_DOMAIN` in BuildConfig. They ended up with base URL `https://` and an empty client ID.
- Apps configured through BuildConfig got a duplicate auth service with the same values.

**Evidence from the failing E2E run:**
- The plugin initialized once, against the mock server.
- "No tokens found" was then logged twice, from two threads, which means two auth services.
- Every login attempt went to `https://oauth/authorize?...client_id=&...`.
- The default initialization's own log line (tag `FronteggApp`) was filtered out of that CI log, which is why it didn't show up.

### Fix

`loadFlags` takes the auth service, and the constructor passes itself. Creating the SDK no longer reads the singleton it is creating. Nothing outside the SDK calls `loadFlags`; I checked the React Native, Flutter and Capacitor plugins.

## Test plan

- [x] New `FeatureFlagsTest` case: `loadFlags` loads flags from the auth service it's given. It failed to compile against the old `Context` signature.
- [x] `:android:testDebugUnitTest`: 734 tests, 0 failures
- [x] `:android:detektDebug`
- [ ] CI, including embedded E2E
- [ ] After release, bump the native SDK in the Capacitor plugin and confirm its Android mock suite is stable

🤖 Generated with [Claude Code](https://claude.com/claude-code)

## Summary
The "Push Workflow / Build And Test" job ran on `macos-15-xlarge`. It runs detekt, the unit tests and `:app:build`, and none of those need macOS. The pull request "Unit Tests" job already runs the same Gradle tasks on `ubuntu-latest`. Linux minutes cost a fraction of the larger macOS runner's, and this job runs on every push to every branch.

## Changes
- `.github/workflows/onPush.yml`: `runs-on: macos-15-xlarge` becomes `runs-on: ubuntu-latest`. No steps change.

## Testing
- The push of this branch runs the changed workflow on `ubuntu-latest`. Its result is on this PR.

---
_Generated by [Claude Code](https://claude.ai/code/session_018X5mwfjstoxdFhCjqvEoo7)_

## Summary
`RequestQueueTest > removeExpired removes old requests` failed on a CI run (764 tests, 1 failed, `RequestQueueTest.kt:139`). The test slept 100ms, enqueued a second request, then removed everything older than 50ms. On a loaded runner, more than 50ms can pass between the second enqueue and `removeExpired`, so both requests were removed and the size assertion failed.

## Changes
- `RequestQueue` takes its clock as a constructor parameter, defaulting to `System::currentTimeMillis`. The only production caller, `FronteggAuthService`, uses the default, so its behavior is unchanged.
- The test advances a fake clock instead of sleeping, so its result no longer depends on timing.

## Testing
- I ran the full `RequestQueueTest` class (13 tests) in a JVM harness with assertions enabled: all pass.
- CI has not run this branch yet. The `macos-15-xlarge` runners are currently not being assigned.

---
_Generated by [Claude Code](https://claude.ai/code/session_018X5mwfjstoxdFhCjqvEoo7)_

Jira: **FR-25936**.

**Stack:** Part 1 of 2; split from #299 to keep PRs under 20 files. Part 2 (E2E coverage and demo markers) is https://github.com/frontegg/frontegg-android-kotlin/pull/299, which is based on this branch. Merge this one first.

## What changes

- Every value on `FronteggAuth` (`isAuthenticated`, `user`, `accessToken`, ...) exposes `stateFlow: StateFlow<T>`. `kotlinx-coroutines-core` is now an `api` dependency so apps can compile against it.
- The SDK no longer uses RxJava internally. `FronteggState.bindDerivedState()` derives `showLoader` and binds the Sentry user once per process through a synchronous internal listener on the setter's thread. `EmbeddedAuthActivity` uses the same listener and clears it in `onPause`.
- `subscribe` and `observable` still work, including emitting a value set twice, and are deprecated. Install docs keep `rxkotlin` only as an optional line for that API.
- `User`, `UserRole` and `UserRolePermission` equality no longer reads unset `lateinit` fields, since `StateFlow` compares users on every set.
- `EntitlementsSwitchWindowTest` orders its repro with a latch instead of timing.
- Docs: `stateFlow` in the API reference, an "Observe authentication state" section in usage, and optional `rxkotlin` in install steps.

## Testing

- `ObservableValueTest` (state flow, deprecated subscribe, listeners, a value set twice), `UserTest` (partial users compare without crashing), `FronteggStateTest` (derived `showLoader`, Sentry binding happens once).
- CI on push: detekt, unit tests and the `:app` build. The demo apps on this branch are unchanged from `master` and still compile against the deprecated `subscribe` API.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_018X5mwfjstoxdFhCjqvEoo7

Jira: **FR-25955** (Android half).

**Stack:** Part 1 of 3; split from https://github.com/frontegg/frontegg-android-kotlin/pull/302 to keep PRs under 20 files. Targets master now that FR-25936 (https://github.com/frontegg/frontegg-android-kotlin/pull/308 and https://github.com/frontegg/frontegg-android-kotlin/pull/299) is merged. Next: part 2, https://github.com/frontegg/frontegg-android-kotlin/pull/310 (the `:compose` module), then part 3, https://github.com/frontegg/frontegg-android-kotlin/pull/302 (demo screen and E2E).

## What changes

- `fronteggAuth.sessions` (`SessionsService`) lists and revokes the signed-in user's active sessions.
- `Api.getUserSessions()` / `Api.revokeUserSession(id)` call `GET` and `DELETE` on `identity/resources/users/sessions/v1/me`. This device's session is the one the server marks `current`, or else the one whose id is the access token's `sid` claim. A `404` on `DELETE` counts as already revoked.
- `revokeOtherSessions()` revokes every other session one by one, and revokes nothing if it cannot tell which session is this device's.
- New `UserSession` model and an API reference entry in `docs/api.md`.

## Backend contract to confirm

- `GET /me` and `DELETE /me/{id}` under `identity/resources/users/sessions/v1` are the endpoints the admin portal uses.

## Testing

- `ApiSessionsTest`: parsing (array and `items` envelope, malformed entries skipped), the current session from `sid`, encoded revoke path, `404` treated as revoked, revoking other sessions one by one, refusing when none is current.
- CI: detekt, unit tests, the `:app` build and the embedded E2E suite.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_018X5mwfjstoxdFhCjqvEoo7

## Summary

The embedded E2E sources on master don't compile, so every `embedded-e2e-suite` run against master fails before any test starts:

```
EmbeddedE2ETests.kt:635:2 Expecting '}'
EmbeddedE2ETests.kt:578:9 Unresolved reference: waitForDescText
EmbeddedE2ETests.kt:624:5 Modifier 'private' is not applicable to 'local function'
```

When https://github.com/frontegg/frontegg-android-kotlin/pull/299 (FR-25936, the Kotlin Flow E2E coverage) was rebased onto master, the two closing braces of `waitForMeCallWithTokenVersion` were dropped. The StateFlow tests added after it ended up nested inside that function. It reached master through https://github.com/frontegg/frontegg-android-kotlin/pull/299 and https://github.com/frontegg/frontegg-android-kotlin/pull/308. The unit tests and push builds don't compile `androidTest`, so they stayed green.

## Changes

- `embedded/src/androidTest/java/com/frontegg/demo/e2e/EmbeddedE2ETests.kt`: restore the two closing braces after `Thread.sleep(500)` in `waitForMeCallWithTokenVersion`. The helper now matches its version before the rebase.

## Testing

- The file's braces balance again.
- The embedded E2E suite on this PR is the check that it compiles and runs.
- It currently blocks the E2E checks on https://github.com/frontegg/frontegg-android-kotlin/pull/304, https://github.com/frontegg/frontegg-android-kotlin/pull/311 and https://github.com/frontegg/frontegg-android-kotlin/pull/310.

---
_Generated by [Claude Code](https://claude.ai/code/session_018X5mwfjstoxdFhCjqvEoo7)_

Jira: **FR-25957** (Android half). Targets `master`, which now has DPoP (FR-25956, merged in https://github.com/frontegg/frontegg-android-kotlin/pull/306). Nothing installs this code yet; https://github.com/frontegg/frontegg-android-kotlin/pull/312 wires it in.

## What changes

- New `com.frontegg.android.integrity` package:
  - `DeviceIntegrityAttestor` keeps one prepared Play Integrity Standard API provider and runs one prepare at a time in the background.
    - A token request waits at most 2 seconds for a prepare already in flight, then at most 3 seconds for the token.
    - Every failure returns `null`, so sign-in goes ahead unattested. That includes a prepare error, an invalid provider, a timeout, and a `LinkageError` when the Play library is missing.
  - `DeviceIntegrityInterceptor` adds `frontegg-device-integrity-token` to the authorization-code exchange, social post-login and passkey post-login on the Frontegg host. The token is bound to the base64url SHA-256 of the request body. Refresh and silent authorize are not attested.
  - `DeviceIntegrity` is the process-wide configuration, off unless a positive Cloud project number is set.
- `FronteggEndpoints.PASSKEY_POST_LOGIN_PATH`.
- The Play Integrity and `play-services-tasks` dependencies in `android/build.gradle`.

## Testing

- `DeviceIntegrityAttestorTest`, `DeviceIntegrityInterceptorTest` and `DeviceIntegrityTest` cover:
  - endpoint matching, with refresh excluded
  - a shared background prepare and the in-flight wait bound
  - fail-open on every error path
  - the integrity header surviving a DPoP nonce retry
- CI on push: detekt, unit tests, `:app` build.

Part 1 of 3; split from https://github.com/frontegg/frontegg-android-kotlin/pull/303 to keep PRs under 20 files.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_018X5mwfjstoxdFhCjqvEoo7

Jira: **FR-25955** (Android half).

**Stack:** Part 2 of 3; split from https://github.com/frontegg/frontegg-android-kotlin/pull/302 to keep PRs under 20 files. Targets master now that part 1, https://github.com/frontegg/frontegg-android-kotlin/pull/309 (sessions API), is merged. Next: part 3, https://github.com/frontegg/frontegg-android-kotlin/pull/302 (demo screen and E2E).

## What changes

- New module and artifact `com.frontegg.sdk:android-compose`, versioned with the core SDK, included in `settings.gradle`.
- `rememberFronteggAuth()` and `collectAsState()` for every auth value.
- `FronteggTenantSwitcher`: the user's tenants, the active one marked, progress while switching, failure shown.
- `FronteggSecurityCenter`: account, MFA status, add a passkey (a dismissed prompt is not an error), active sessions (revoke one, sign out the others), sign out. The session list is cleared and reloaded whenever the signed-in user changes, and a new load replaces one still running.
- Both have stateless overloads for custom wiring and follow `MaterialTheme`.
- CI (`onPush.yml`, `onPullRequestUpdated.yaml`) runs `:compose:testDebugUnitTest` and `:compose:assembleDebug` next to the core checks.
- Docs: a Jetpack Compose section in `docs/advanced.md` and a pointer from `docs/usage.md`.

## Testing

- `TenantSwitcherStateTest` and `SecurityCenterStateTest` (a replaced load, clearing on user change, revoke flows).
- CI builds and unit-tests the new module.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_018X5mwfjstoxdFhCjqvEoo7

Closes https://github.com/frontegg/frontegg-android-kotlin/issues/269.

## What changes

- New overload: `fronteggAuth.directLoginAction(activity, type, data, additionalQueryParams, callback)`. It matches the iOS SDK's `additionalQueryParams`.
- The embedded login box sends these params in `login_direct_action`, merged over the default `prompt=consent`. Passing `prompt` replaces it; any other key is added next to it.
- iOS sends its `additionalQueryParams` the same way, in the `login_direct_action` payload.
- Existing calls are unchanged:
  - `FronteggAuth.directLoginAction(activity, type, data, callback)` and `EmbeddedAuthActivity.directLoginAction(activity, type, data, callback)` keep their signatures. Both delegate to the new overloads with no extra params.
  - Calls with three arguments, a trailing lambda, a named `callback`, or a positional callback all compile and behave as before.
- `EmbeddedAuthActivity` carries the params in the intent as a `Bundle`. `consumeIntent` builds the payload through a small `directLoginActionJson` helper, which replaces the duplicated map and JSON code.
- `ephemeralSession` is documented as iOS-only, because Android always runs this flow in the embedded WebView. It is not added to the signature.
- `docs/api.md` documents the new overload.

## Testing

- `EmbeddedAuthActivityTest` (Robolectric):
  - With no caller params, the payload keeps only `prompt=consent`.
  - Caller params are added, and `prompt` is replaced.
- `FronteggAuthServiceTest`:
  - The old overload reaches `EmbeddedAuthActivity` with an empty map.
  - The new overload passes its params through.
- Overload resolution was checked by compiling every call style used in this repo against the new signatures.

## Follow-up

The React Native, Flutter and Ionic bridges can pass their `additionalQueryParams` through once this is released.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_018X5mwfjstoxdFhCjqvEoo7

## v1.3.43

Bug fixes:

- Fixed embedded step-up never completing after MFA. The login box went on to the application's App URL instead of returning to the app, so no stepped-up token was issued and step-up never reported success. Step-up now finishes through the hosted login and returns a stepped-up token. No app or configuration changes are needed.
- Fixed embedded step-up sometimes signing the user out before the MFA challenge appeared, because the step-up page could start before the login box had loaded the user's session. The step-up page now opens only once the session is loaded. No app or configuration changes are needed.

## v1.3.42

Features:

- Added a host-supplied footer below the embedded login box, for example a sign-up link or the reCAPTCHA attribution Google requires when the badge is hidden. Set `FronteggInnerStorage.loginBoxFooter` to rows of text and link segments; it defaults to `null`, and setting `null` clears it. The login box renders the footer on its sign-in and password screens only. Links must be absolute `http(s)` URLs or a scheme the app itself declares a browsable `VIEW` filter for; any other link, and app links that look like OAuth callbacks, are shown as plain text. A tapped `http(s)` footer link opens in the browser and leaves the login box open; a footer app link is handed to the app and closes the login box. Embedded mode only, gated on `LoginBoxCustomization.isSupported()`. Requires the hosted login box update that renders host footers (FR-27245); against an older login box the footer is ignored. Based on a contribution by @airowe. ([#291](https://github.com/frontegg/frontegg-android-kotlin/pull/291))

## v1.3.41

Features:

- Added runtime theme and copy overrides for the embedded login box. `FronteggInnerStorage.loginBoxThemeOptions` and `.loginBoxLocalizations` take the same shapes as `themeV2` and `localizations` in the environment's login-box configuration, and are deep-merged over it — keys the override does not mention keep whatever the environment defines. This covers appearance that is only known at runtime, such as a white-labeled app resolving each brand's logo and colours from its own backend, which per-environment configuration cannot express. Both default to `null`, and setting `null` clears an override, so apps that do not set them are unaffected. Embedded mode only. `LoginBoxCustomization.isSupported()` reports whether the device's WebView provider can apply overrides; on one that cannot, the login box renders the environment's own branding. Requires a hosted login box that applies host-supplied overrides. ([#286](https://github.com/frontegg/frontegg-android-kotlin/pull/286))

Bug fixes:

- Fixed the SDK retrying forever when a refresh token was rejected. A 401 from the token endpoint means the refresh token itself is no longer valid, so no retry can succeed, but it was treated as a temporary failure: the session stayed marked as signed in, and with offline mode enabled each failed attempt queued another, with no limit and no backoff — one captured case reached around 1,780 failed refreshes in 14 minutes with the rate still climbing. A rejected refresh token now ends the session immediately, which also cancels the refresh timer and drains any queued retries. Temporary failures such as network errors keep their existing retry behaviour. No app or configuration changes are needed. 

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
