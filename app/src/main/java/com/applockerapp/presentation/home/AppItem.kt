package com.applockerapp.presentation.home

/**
 * Data model for applications inside the AppLocker.
 */
data class AppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    var isLocked: Boolean,
    val iconDrawableId: Int
)
