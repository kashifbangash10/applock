package com.applockerapp.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.os.Build
import com.applockerapp.presentation.profile.AppLockProfile
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom

class LockSettingsManager(private val context: Context) {

    private val prefs: SharedPreferences
        get() = getPreferences(context)

    init {
        try {
            migrateToHashedStorage()
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "Error during storage migration", e)
        }
    }

    companion object {
        private const val PREFS_NAME = "applock_security_prefs"
        @Volatile
        private var cachedPrefs: SharedPreferences? = null

        fun getPreferences(context: Context): SharedPreferences {
            return cachedPrefs ?: synchronized(this) {
                cachedPrefs ?: createPreferences(context.applicationContext).also { cachedPrefs = it }
            }
        }

        private fun fallbackToPlainPrefs(context: Context): SharedPreferences {
            Log.w("LockSettingsManager", "Forcing fallback to plain SharedPreferences")
            return try {
                context.getSharedPreferences(PREFS_NAME + "_plain", Context.MODE_PRIVATE).also {
                    cachedPrefs = it
                }
            } catch (e: Exception) {
                Log.e("LockSettingsManager", "Critical error creating plain prefs", e)
                context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE).also {
                    cachedPrefs = it
                }
            }
        }

        private const val KEY_PATTERN = "lock_pattern_key"
        private const val KEY_LOCKED_PACKAGES = "locked_packages_key"
        private const val KEY_INTRUDER_ENABLED = "intruder_enabled_key"
        private const val KEY_INTRUDER_LIMIT = "intruder_limit_key"
        private const val KEY_LOCK_TYPE = "lock_type_key"
        private const val KEY_PIN = "lock_pin_key"
        private const val KEY_PASSWORD = "lock_password_key"
        private const val KEY_SECURITY_Q_INDEX = "security_q_index_key"
        private const val KEY_SECURITY_ANSWER = "security_answer_key"
        private const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection_enabled"
        private const val KEY_FAILED_LOCKOUT = "failed_attempts_lockout_enabled"
        private const val KEY_LOCKOUT_DEADLINE = "lockout_deadline"

        private fun createPreferences(context: Context): SharedPreferences {
            return try {
                Log.d("LockSettingsManager", "Attempting EncryptedSharedPreferences creation")
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                val result = EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                Log.d("LockSettingsManager", "EncryptedSharedPreferences created successfully")
                result
            } catch (e: Exception) {
                Log.e("LockSettingsManager", "First ESP creation failed: ${e.message}. Wiping and retrying.", e)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        context.deleteSharedPreferences(PREFS_NAME)
                    } else {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                    }
                    try {
                        val keyStore = KeyStore.getInstance("AndroidKeyStore")
                        keyStore.load(null)
                        if (keyStore.containsAlias("_androidx_security_master_key")) {
                            keyStore.deleteEntry("_androidx_security_master_key")
                            Log.d("LockSettingsManager", "Deleted stale keystore entry")
                        }
                    } catch (ksEx: Exception) {
                        Log.w("LockSettingsManager", "Could not clear keystore entry: ${ksEx.message}")
                    }
                    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                    val result = EncryptedSharedPreferences.create(
                        PREFS_NAME,
                        masterKeyAlias,
                        context,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    Log.d("LockSettingsManager", "EncryptedSharedPreferences created after reset")
                    result
                } catch (e2: Exception) {
                    fallbackToPlainPrefs(context)
                }
            }
        }
    }

    private fun safeGetString(key: String, defValue: String?): String? {
        return try {
            prefs.getString(key, defValue)
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "safeGetString exception on key: $key", e)
            fallbackToPlainPrefs(context)
            try { prefs.getString(key, defValue) } catch (e2: Exception) { defValue }
        }
    }

    private fun safePutString(key: String, value: String?) {
        try {
            prefs.edit().putString(key, value).apply()
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "safePutString exception on key: $key", e)
            fallbackToPlainPrefs(context)
            try { prefs.edit().putString(key, value).apply() } catch (e2: Exception) {}
        }
    }

    private fun safeGetInt(key: String, defValue: Int): Int {
        return try {
            prefs.getInt(key, defValue)
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "safeGetInt exception on key: $key", e)
            fallbackToPlainPrefs(context)
            try { prefs.getInt(key, defValue) } catch (e2: Exception) { defValue }
        }
    }

    private fun safePutInt(key: String, value: Int) {
        try {
            prefs.edit().putInt(key, value).apply()
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "safePutInt exception on key: $key", e)
            fallbackToPlainPrefs(context)
            try { prefs.edit().putInt(key, value).apply() } catch (e2: Exception) {}
        }
    }

    private fun safeGetBoolean(key: String, defValue: Boolean): Boolean {
        return try {
            prefs.getBoolean(key, defValue)
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "safeGetBoolean exception on key: $key", e)
            fallbackToPlainPrefs(context)
            try { prefs.getBoolean(key, defValue) } catch (e2: Exception) { defValue }
        }
    }

    private fun safePutBoolean(key: String, value: Boolean) {
        try {
            prefs.edit().putBoolean(key, value).apply()
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "safePutBoolean exception on key: $key", e)
            fallbackToPlainPrefs(context)
            try { prefs.edit().putBoolean(key, value).apply() } catch (e2: Exception) {}
        }
    }

    private fun safeGetLong(key: String, defValue: Long): Long {
        return try {
            prefs.getLong(key, defValue)
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "safeGetLong exception on key: $key", e)
            fallbackToPlainPrefs(context)
            try { prefs.getLong(key, defValue) } catch (e2: Exception) { defValue }
        }
    }

    private fun safePutLong(key: String, value: Long) {
        try {
            prefs.edit().putLong(key, value).apply()
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "safePutLong exception on key: $key", e)
            fallbackToPlainPrefs(context)
            try { prefs.edit().putLong(key, value).apply() } catch (e2: Exception) {}
        }
    }

    private fun safeGetStringSet(key: String, defValue: Set<String>?): Set<String>? {
        return try {
            prefs.getStringSet(key, defValue)
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "safeGetStringSet exception on key: $key", e)
            fallbackToPlainPrefs(context)
            try { prefs.getStringSet(key, defValue) } catch (e2: Exception) { defValue }
        }
    }

    private fun safePutStringSet(key: String, value: Set<String>?) {
        try {
            prefs.edit().putStringSet(key, value).apply()
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "safePutStringSet exception on key: $key", e)
            fallbackToPlainPrefs(context)
            try { prefs.edit().putStringSet(key, value).apply() } catch (e2: Exception) {}
        }
    }

    private fun getOrCreateSalt(): String {
        var salt = safeGetString("security_salt", null)
        if (salt == null) {
            salt = generateSalt()
            safePutString("security_salt", salt)
        }
        return salt
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return Base64.encodeToString(saltBytes, Base64.NO_WRAP)
    }

    private fun hashValue(value: String, salt: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val combined = value + salt
            val hashBytes = digest.digest(combined.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "Error hashing value: ${e.message}")
            value
        }
    }

    private fun migrateToHashedStorage() {
        val version = safeGetInt("security_version", 1)
        if (version < 2) {
            val salt = getOrCreateSalt()

            val plainPin = safeGetString(KEY_PIN, null)
            if (plainPin != null && plainPin.length != 44) {
                safePutString(KEY_PIN, hashValue(plainPin, salt))
            }

            val plainPassword = safeGetString(KEY_PASSWORD, null)
            if (plainPassword != null && plainPassword.length != 44) {
                safePutString(KEY_PASSWORD, hashValue(plainPassword, salt))
            }

            val plainPattern = safeGetString(KEY_PATTERN, null)
            if (plainPattern != null && !plainPattern.contains("==") && plainPattern.length < 44) {
                safePutString(KEY_PATTERN, hashValue(plainPattern, salt))
            }

            val plainAnswer = safeGetString(KEY_SECURITY_ANSWER, null)
            if (plainAnswer != null && plainAnswer.length != 44) {
                safePutString(KEY_SECURITY_ANSWER, hashValue(plainAnswer.trim().lowercase(), salt))
            }

            safePutInt("security_version", 2)
        }
    }

    fun getLockType(): String {
        return safeGetString(KEY_LOCK_TYPE, "pattern") ?: "pattern"
    }

    fun setLockType(type: String) {
        safePutString(KEY_LOCK_TYPE, type)
    }

    fun savePin(pin: String) {
        val salt = getOrCreateSalt()
        val hashed = hashValue(pin, salt)
        safePutString(KEY_PIN, hashed)
    }

    fun getPin(): String? {
        return safeGetString(KEY_PIN, null)
    }

    fun isPinSet(): Boolean {
        return !getPin().isNullOrEmpty()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = getPin() ?: return false
        return savedHash == hashValue(pin, getOrCreateSalt())
    }

    fun savePassword(password: String) {
        val salt = getOrCreateSalt()
        val hashed = hashValue(password, salt)
        safePutString(KEY_PASSWORD, hashed)
    }

    fun getPassword(): String? {
        return safeGetString(KEY_PASSWORD, null)
    }

    fun isPasswordSet(): Boolean {
        return !getPassword().isNullOrEmpty()
    }

    fun verifyPassword(password: String): Boolean {
        val savedHash = getPassword() ?: return false
        return savedHash == hashValue(password, getOrCreateSalt())
    }

    fun isAnyLockSet(): Boolean {
        return when (getLockType()) {
            "pin" -> isPinSet()
            "password" -> isPasswordSet()
            else -> isPatternSet()
        }
    }

    fun saveSecurityQuestion(index: Int, answer: String) {
        val salt = getOrCreateSalt()
        val hashed = hashValue(answer.trim().lowercase(), salt)
        safePutInt(KEY_SECURITY_Q_INDEX, index)
        safePutString(KEY_SECURITY_ANSWER, hashed)
    }

    fun saveSecurityQuestionWithHash(index: Int, hashedAnswer: String) {
        safePutInt(KEY_SECURITY_Q_INDEX, index)
        safePutString(KEY_SECURITY_ANSWER, hashedAnswer)
    }

    fun getSecurityQuestionIndex(): Int {
        return safeGetInt(KEY_SECURITY_Q_INDEX, -1)
    }

    fun getSecurityAnswer(): String? {
        return safeGetString(KEY_SECURITY_ANSWER, null)
    }

    fun isSecurityQuestionSet(): Boolean {
        return getSecurityQuestionIndex() != -1 && !getSecurityAnswer().isNullOrEmpty()
    }

    fun verifySecurityAnswer(answer: String): Boolean {
        val savedHash = getSecurityAnswer() ?: return false
        return savedHash == hashValue(answer.trim().lowercase(), getOrCreateSalt())
    }

    fun savePattern(pattern: List<Int>) {
        val patternStr = pattern.joinToString(",")
        val salt = getOrCreateSalt()
        val hashed = hashValue(patternStr, salt)
        safePutString(KEY_PATTERN, hashed)
    }

    fun getPattern(): List<Int>? {
        val patternStr = safeGetString(KEY_PATTERN, null) ?: return null
        if (patternStr.isEmpty() || patternStr.length == 44) return null
        return patternStr.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun isPatternSet(): Boolean {
        return !safeGetString(KEY_PATTERN, null).isNullOrEmpty()
    }

    fun verifyPattern(pattern: List<Int>): Boolean {
        val savedHash = safeGetString(KEY_PATTERN, null) ?: return false
        val patternStr = pattern.joinToString(",")
        return savedHash == hashValue(patternStr, getOrCreateSalt())
    }

    fun setAppLocked(packageName: String, isLocked: Boolean) {
        val currentLocked = getLockedPackages().toMutableSet()
        if (isLocked) {
            currentLocked.add(packageName)
        } else {
            currentLocked.remove(packageName)
        }
        safePutStringSet(KEY_LOCKED_PACKAGES, currentLocked)
    }

    fun isAppLocked(packageName: String): Boolean {
        return getLockedPackages().contains(packageName)
    }

    fun getLockedPackages(): Set<String> {
        return safeGetStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun isIntruderEnabled(): Boolean {
        return safeGetBoolean(KEY_INTRUDER_ENABLED, true)
    }

    fun setIntruderEnabled(enabled: Boolean) {
        safePutBoolean(KEY_INTRUDER_ENABLED, enabled)
    }

    fun getIntruderLimit(): Int {
        return safeGetInt(KEY_INTRUDER_LIMIT, 2)
    }

    fun setIntruderLimit(limit: Int) {
        safePutInt(KEY_INTRUDER_LIMIT, limit)
    }

    fun isMasterLockEnabled(): Boolean {
        return safeGetBoolean("master_lock_enabled", true)
    }

    fun setMasterLockEnabled(enabled: Boolean) {
        safePutBoolean("master_lock_enabled", enabled)
    }

    fun isScreenshotProtectionEnabled(): Boolean {
        return safeGetBoolean(KEY_SCREENSHOT_PROTECTION, true)
    }

    fun setScreenshotProtectionEnabled(enabled: Boolean) {
        safePutBoolean(KEY_SCREENSHOT_PROTECTION, enabled)
    }

    fun isFailedAttemptsLockoutEnabled(): Boolean {
        return safeGetBoolean(KEY_FAILED_LOCKOUT, true)
    }

    fun setFailedAttemptsLockoutEnabled(enabled: Boolean) {
        safePutBoolean(KEY_FAILED_LOCKOUT, enabled)
    }

    fun getLockoutDeadline(): Long {
        return safeGetLong(KEY_LOCKOUT_DEADLINE, 0L)
    }

    fun setLockoutDeadline(deadline: Long) {
        safePutLong(KEY_LOCKOUT_DEADLINE, deadline)
    }

    fun isOnboardingCompleted(): Boolean {
        return safeGetBoolean("onboarding_completed", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        safePutBoolean("onboarding_completed", completed)
    }

    fun getLanguageIndex(): Int {
        return safeGetInt("selected_language_index", 0)
    }

    fun setLanguageIndex(index: Int) {
        safePutInt("selected_language_index", index)
    }

    fun getProfiles(): List<AppLockProfile> {
        val jsonStr = safeGetString("applock_profiles", null)
        if (jsonStr.isNullOrEmpty()) {
            val defaults = listOf(
                AppLockProfile(
                    id = "guest",
                    name = "Guest",
                    description = "Guests are not allowed to access settings and emails",
                    packages = setOf("com.android.settings", "com.google.android.gm", "com.google.android.apps.photos", "com.android.vending"),
                    iconType = "guest"
                ),
                AppLockProfile(
                    id = "kids",
                    name = "Kids",
                    description = "Protect your social and gaming accounts",
                    packages = setOf("com.whatsapp", "com.google.android.youtube", "com.android.vending", "com.android.chrome", "com.snapchat.android", "com.facebook.katana"),
                    iconType = "kids"
                )
            )
            saveProfiles(defaults)
            return defaults
        }

        val list = mutableListOf<AppLockProfile>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val description = obj.getString("description")
                val iconType = obj.optString("iconType", "custom")
                val pkgsArray = obj.getJSONArray("packages")
                val packages = mutableSetOf<String>()
                for (j in 0 until pkgsArray.length()) {
                    packages.add(pkgsArray.getString(j))
                }
                val isActive = obj.optBoolean("isActive", false)
                list.add(AppLockProfile(id, name, description, packages, isActive, iconType))
            }
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "Error parsing profiles: ${e.message}")
        }
        return list
    }

    fun saveProfiles(profiles: List<AppLockProfile>) {
        try {
            val array = JSONArray()
            profiles.forEach { profile ->
                val obj = JSONObject()
                obj.put("id", profile.id)
                obj.put("name", profile.name)
                obj.put("description", profile.description)
                obj.put("iconType", profile.iconType)
                obj.put("isActive", profile.isActive)
                val pkgsArray = JSONArray()
                profile.packages.forEach { pkgsArray.put(it) }
                obj.put("packages", pkgsArray)
                array.put(obj)
            }
            safePutString("applock_profiles", array.toString())
        } catch (e: Exception) {
            Log.e("LockSettingsManager", "Error saving profiles: ${e.message}")
        }
    }

    fun activateProfile(profileId: String?) {
        val profiles = getProfiles().map { profile ->
            profile.copy(isActive = profile.id == profileId)
        }
        saveProfiles(profiles)

        if (profileId != null) {
            val activeProfile = profiles.find { it.id == profileId }
            if (activeProfile != null) {
                safePutStringSet(KEY_LOCKED_PACKAGES, activeProfile.packages)
            }
        } else {
            safePutStringSet(KEY_LOCKED_PACKAGES, emptySet())
        }
    }

    fun getActiveProfile(): AppLockProfile? {
        return getProfiles().find { it.isActive }
    }

    fun saveProfile(profile: AppLockProfile) {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        saveProfiles(current)
        if (profile.isActive) {
            safePutStringSet(KEY_LOCKED_PACKAGES, profile.packages)
        }
    }

    fun deleteProfile(profileId: String) {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profileId }
        if (index != -1) {
            val removed = current.removeAt(index)
            saveProfiles(current)
            if (removed.isActive) {
                activateProfile(null)
            }
        }
    }
}
