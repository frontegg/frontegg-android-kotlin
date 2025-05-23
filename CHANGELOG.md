## v
- Add support for caching web js font css files
- Fix login direct action command

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
