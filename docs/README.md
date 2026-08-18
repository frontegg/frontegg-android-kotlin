<p align="center">
  <img src="https://raw.githubusercontent.com/frontegg/frontegg-android-kotlin/master/images/frontegg-kotlin.png" alt="Frontegg Android SDK" width="640" />
</p>

<h1 align="center">Frontegg Android SDK</h1>

<p align="center">
  <strong>Authentication and user management for your Android app — in a few lines of Kotlin.</strong>
</p>

<p align="center">
  <a href="https://github.com/frontegg/frontegg-android-kotlin/releases"><img src="https://img.shields.io/github/v/release/frontegg/frontegg-android-kotlin?label=release&color=6c47ff" alt="Latest release" /></a>
  <a href="https://central.sonatype.com/artifact/com.frontegg.sdk/android"><img src="https://img.shields.io/maven-central/v/com.frontegg.sdk/android?label=maven%20central&color=blue" alt="Maven Central" /></a>
  <img src="https://img.shields.io/badge/API-26%2B-lightgrey" alt="Android API 26+" />
  <img src="https://img.shields.io/badge/Kotlin-ready-7f52ff" alt="Kotlin" />
  <a href="https://github.com/frontegg/frontegg-android-kotlin/blob/master/LICENSE"><img src="https://img.shields.io/github/license/frontegg/frontegg-android-kotlin?color=blue" alt="Licence" /></a>
</p>

---

[Frontegg](https://frontegg.com/) is a self-served user management platform for modern SaaS
applications. Drop this SDK in and your app gets a production login screen, a live session, and a
user object — without you writing an auth flow or touching a token.

| | |
| --- | --- |
| **Hosted or embedded login** | Frontegg's login box in a browser tab, or your own UI on top of the API |
| **Every method your tenants need** | Email, social, SSO, magic link, passkeys, MFA and step-up |
| **Sessions that stay alive** | Tokens refresh in the background; offline mode keeps users working without a connection |
| **Built for multi-tenant SaaS** | Multi-tenancy, RBAC, entitlements, multi-region and multi-app support |

---

## Install

Add the dependency to your app's `build.gradle`:

```groovy
dependencies {
    implementation 'com.frontegg.sdk:android:1.3.38'
    implementation 'io.reactivex.rxjava3:rxkotlin:3.0.1'
}
```

> Requires **Android API 26+**. The [releases page](https://github.com/frontegg/frontegg-android-kotlin/releases) has the current version.

## Quick start

**1 · Allow the redirect URLs.** In the Frontegg Portal, under **[ENVIRONMENT] → Authentication →
Login method**, turn hosted login on and add:

```
{{ANDROID_PACKAGE_NAME}}://{{FRONTEGG_BASE_URL}}/android/oauth/callback
https://{{FRONTEGG_BASE_URL}}/oauth/account/redirect/android/{{ANDROID_PACKAGE_NAME}}
{{FRONTEGG_BASE_URL}}/oauth/authorize
```

**2 · Point the app at your environment** in `app/build.gradle`. Your domain and client ID are in
the Portal under **[ENVIRONMENT] → Keys & domains**.

```groovy
def fronteggDomain = "{{FRONTEGG_DOMAIN}}"
def fronteggClientId = "{{FRONTEGG_CLIENT_ID}}"

android {
    defaultConfig {
        applicationId "com.example.myapp"

        manifestPlaceholders = [
                "package_name"      : applicationId,
                "frontegg_domain"   : fronteggDomain,
                "frontegg_client_id": fronteggClientId
        ]

        buildConfigField "String", 'FRONTEGG_DOMAIN', "\"$fronteggDomain\""
        buildConfigField "String", 'FRONTEGG_CLIENT_ID', "\"$fronteggClientId\""
    }
}
```

`applicationId` must come before `manifestPlaceholders` — `defaultConfig` is evaluated top to
bottom, and the `package_name` placeholder reads it.

**3 · Use it.** No manual initialisation; the SDK initialises lazily from context.

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val auth = this.fronteggAuth

        if (auth.isAuthenticated.value) {
            startActivity(Intent(this, HomeActivity::class.java))
        } else {
            findViewById<Button>(R.id.loginButton).setOnClickListener {
                auth.login(this)
            }
        }
    }
}
```

That is a working login. Magic links, passkeys and IdP-initiated SSO also need Android App Links
configured — the [Get Started guide](https://android-kotlin-guide.frontegg.com/#/getting-started)
walks through the `assetlinks.json` setup.

## Documentation

| Guide | What it covers |
| --- | --- |
| [Get Started](https://android-kotlin-guide.frontegg.com/#/getting-started) | Integration end to end, including App Links |
| [Setup](https://android-kotlin-guide.frontegg.com/#/setup) | Detailed configuration |
| [API Reference](https://android-kotlin-guide.frontegg.com/#/api) | Every method the SDK exposes |
| [Usage Examples](https://android-kotlin-guide.frontegg.com/#/usage) | Common implementation patterns |
| [Advanced Topics](https://android-kotlin-guide.frontegg.com/#/advanced) | Multi-region, multi-app, entitlements, logging |
| [Migration Guide](https://android-kotlin-guide.frontegg.com/#/migration-guide) | Moving between major versions |

Full platform documentation lives at [developers.frontegg.com](https://developers.frontegg.com).

## Example apps

Four runnable projects, each a complete integration:

[Hosted](https://github.com/frontegg/frontegg-android-kotlin/tree/master/app) ·
[Embedded](https://github.com/frontegg/frontegg-android-kotlin/tree/master/embedded) ·
[Application-Id](https://github.com/frontegg/frontegg-android-kotlin/tree/master/applicationId) ·
[Multi-Region](https://github.com/frontegg/frontegg-android-kotlin/tree/master/multi-region)

## Support

No Frontegg account yet? [Sign up free](https://portal.us.frontegg.com/signup).

Questions, or something broken? Reach the team at
[support.frontegg.com](https://support.frontegg.com/frontegg/directories) or
[open an issue](https://github.com/frontegg/frontegg-android-kotlin/issues).

Licensed under the [LICENSE](https://github.com/frontegg/frontegg-android-kotlin/blob/master/LICENSE) in this repository.
