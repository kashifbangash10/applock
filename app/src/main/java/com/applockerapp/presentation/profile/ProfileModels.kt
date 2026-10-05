package com.applockerapp.presentation.profile

data class AppLockProfile(
    val id: String,
    val name: String,
    val description: String,
    val packages: Set<String>,
    val isActive: Boolean = false,
    val iconType: String = "custom"
)
