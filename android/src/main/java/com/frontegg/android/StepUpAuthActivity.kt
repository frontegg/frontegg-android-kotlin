package com.frontegg.android

/**
 * Always-enabled embedded WebView activity used exclusively for step-up.
 *
 * Step-up must reuse the existing native session via the getTokens bridge — like the
 * Admin Portal — regardless of the app's login mode. [EmbeddedAuthActivity] is disabled
 * (`android:enabled="false"` via `tools:replace`) in hosted-login apps, so it can't be
 * launched there. This subclass has its own always-enabled manifest entry (no
 * intent-filter, and not toggled by the app because it has a different name), so step-up
 * can always present the embedded bridge WebView while inheriting all of
 * [EmbeddedAuthActivity]'s WebView and OAuth-completion machinery.
 */
class StepUpAuthActivity : EmbeddedAuthActivity()
