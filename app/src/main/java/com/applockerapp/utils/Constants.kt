package com.applockerapp.utils

/**
 * App-wide constants.
 * Centralized place for all magic strings, keys, and values.
 */
object Constants {
    // App Info
    const val APP_NAME = "AppLocker"
    const val APP_VERSION = "1.0"

    // SharedPreferences keys
    const val PREF_NAME = "applocker_prefs"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val KEY_USER_TOKEN = "user_token"
    const val KEY_ONBOARDING_DONE = "onboarding_done"
    const val KEY_APP_THEME = "app_theme"

    // Navigation
    const val SPLASH_DELAY_MS = 3200L   // Enough time for ice crystal animation

    // API (to be filled when backend is ready)
    // const val BASE_URL = "https://api.yourapp.com/"
}
