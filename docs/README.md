# Frontegg Android SDK
![Frontegg_Android_SDK (Kotlin)](/images/frontegg-kotlin.png)

Welcome to the official **Frontegg Android SDK** — your all-in-one solution for
integrating authentication and user management into your Android mobile
app. [Frontegg](https://frontegg.com/) is a self-served user management platform, built for modern
SaaS applications. Easily implement authentication, SSO, RBAC, multi-tenancy, and more — all from a
single SDK.

## 📚 Documentation

This repository includes:

- A [Get Started](https://android-kotlin-guide.frontegg.com/#/getting-started) guide for quick integration
- A [Setup Guide](https://android-kotlin-guide.frontegg.com/#/setup) with detailed setup instructions
- An [API Reference](https://android-kotlin-guide.frontegg.com/#/api) for detailed SDK functionality
- [Usage Examples](https://android-kotlin-guide.frontegg.com/#/usage) with common implementation patterns
- [Advanced Topics](https://android-kotlin-guide.frontegg.com/#/advanced) for complex integration scenarios
- [Migration Gide](https://android-kotlin-guide.frontegg.com/#/migration-guide) for check migration instructions
- A [Hosted](https://github.com/frontegg/frontegg-android-kotlin/tree/master/app), [Embedded](https://github.com/frontegg/frontegg-android-kotlin/tree/master/embedded), [Application-Id](https://github.com/frontegg/frontegg-android-kotlin/tree/master/applicationId), and [Multi-Region](https://github.com/frontegg/frontegg-android-kotlin/tree/master/multi-region) example projects to help you get started quickly

## Entitlements

The SDK can load and check user entitlements (features and permissions) from the Frontegg Entitlements API. Enable entitlements by adding `FRONTEGG_ENTITLEMENTS_ENABLED` to your app’s BuildConfig (e.g. `buildConfigField "boolean", 'FRONTEGG_ENTITLEMENTS_ENABLED', "true"` in `build.gradle`), then:

1. Entitlements are fetched automatically on login. You can also call `fronteggAuth.loadEntitlements(forceRefresh, completion)`: by default (`forceRefresh = false`) the SDK uses cached entitlements when available (no network call). Pass `forceRefresh = true` to always fetch from the API (`GET .../frontegg/entitlements/api/v2/user-entitlements`).
2. Use the cached state for local checks: `getFeatureEntitlements(featureKey)`, `getPermissionEntitlements(permissionKey)`, `getEntitlements(options)` with `EntitledToOptions.FeatureKey(key)` or `EntitledToOptions.PermissionKey(key)`.
3. All checks after load use in-memory state only. Cache is cleared on logout. Access raw state via `fronteggAuth.entitlements.state` (`EntitlementState`: `featureKeys`, `permissionKeys`).

For full documentation, visit the Frontegg Developer Portal:  
🔗 [https://developers.frontegg.com](https://developers.frontegg.com)

---

## 🧑‍💻 Getting Started with Frontegg

Don't have a Frontegg account yet?  
Sign up here → [https://portal.us.frontegg.com/signup](https://portal.us.frontegg.com/signup)

---

## 💬 Support

Need help? Our team is here for you:  
[https://support.frontegg.com/frontegg/directories](https://support.frontegg.com/frontegg/directories)
