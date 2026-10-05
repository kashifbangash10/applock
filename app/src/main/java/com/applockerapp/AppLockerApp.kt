package com.applockerapp

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class - Entry point for Dagger Hilt DI.
 * @HiltAndroidApp triggers Hilt's code generation and sets up
 * the application-level dependency container.
 */
@HiltAndroidApp
class AppLockerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Read theme settings and apply at startup
        val prefs = getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("is_dark_mode", true)
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}
