## v
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
