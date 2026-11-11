package com.frontegg.android

/** How the embedded admin portal lays itself out. */
enum class AdminPortalLayout {
    /** Renders the desktop layout at 1024px, scaled to fit, with the sidebar always visible. */
    DESKTOP,

    /** Uses the device's real width, so on phones the sidebar collapses into a menu button. */
    RESPONSIVE,
}
