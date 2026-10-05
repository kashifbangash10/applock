package com.applockerapp.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.applockerapp.R
import com.applockerapp.databinding.ActivityMainBinding
import com.applockerapp.presentation.customview.PatternLockView
import com.applockerapp.presentation.lock.BiometricAuthActivity
import com.applockerapp.presentation.lock.SecurityQuestionResetActivity
import com.applockerapp.presentation.service.AppLockService
import com.applockerapp.presentation.themes.ThemeConfig
import com.applockerapp.utils.IntruderSelfieHelper
import com.applockerapp.utils.LockSettingsManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity - Single Activity host.
 * Protects AppLocker app itself with mandatory lock authentication on launch/resume.
 * Starts AppLockService only if both required permissions are already granted.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var lockSettingsManager: LockSettingsManager? = null

    private var isAppAuthenticated = false
    private var isBiometricPromptShowing = false
    private var selfLockBiometricPromptDismissed = false
    private var wrongAttempts = 0
    private var lockoutTimer: CountDownTimer? = null
    private var enteredPin = ""

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            try {
                lockSettingsManager = LockSettingsManager(this)
            } catch (e: Exception) {
                android.util.Log.e("AppLockDebug", "Failed to create LockSettingsManager in MainActivity", e)
            }

            // TODO: Screenshot protection disabled temporarily — re-enable later
            // try {
            //     if (lockSettingsManager?.isScreenshotProtectionEnabled() == true) {
            //         window.setFlags(
            //             android.view.WindowManager.LayoutParams.FLAG_SECURE,
            //             android.view.WindowManager.LayoutParams.FLAG_SECURE
            //         )
            //     }
            // } catch (e: Exception) {
            //     android.util.Log.e("AppLockDebug", "Error applying screenshot protection", e)
            // }

            setupNavigation()
            requestRequiredPermissions()
        } catch (e: Exception) {
            android.util.Log.e("AppLockDebug", "Critical error in MainActivity.onCreate", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val overlay = hasOverlayPermission()
            val usage = hasUsagePermission()
            android.util.Log.d("AppLockDebug", "MainActivity onResume: overlayGranted=$overlay, usageGranted=$usage")

            if (overlay && usage) {
                startAppLockService()
            }

            // Check if AppLocker self-lock authentication is required
            val lsm = lockSettingsManager
            if (lsm != null && lsm.isAnyLockSet() && !isAppAuthenticated) {
                showSelfLockOverlay()
            } else {
                hideSelfLockOverlay()
            }
        } catch (e: Exception) {
            android.util.Log.e("AppLockDebug", "Error in MainActivity.onResume", e)
        }
    }

    override fun onStop() {
        super.onStop()
        // Reset authentication state when app is sent to background
        isAppAuthenticated = false
        selfLockBiometricPromptDismissed = false
    }

    private fun showSelfLockOverlay() {
        val overlayView = binding.appLockAuthOverlay.root
        overlayView.visibility = View.VISIBLE
        overlayView.findViewById<PatternLockView>(R.id.overlayPatternView)?.clearPattern()

        val themeId = try {
            getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
                .getInt("selected_theme_id", 1)
        } catch (e: Exception) { 1 }
        try { applyThemeToOverlay(overlayView, themeId) } catch (e: Exception) {
            android.util.Log.e("AppLockDebug", "applyThemeToOverlay failed", e)
        }

        // Setup AppLocker header info
        val imgIcon = overlayView.findViewById<ImageView>(R.id.imgOverlayAppIcon)
        val tvAppName = overlayView.findViewById<TextView>(R.id.tvOverlayAppName)
        val tvTitle = overlayView.findViewById<TextView>(R.id.tvOverlayLockedTitle)
        val btnExit = overlayView.findViewById<ImageView>(R.id.btnOverlayExit)
        val statusText = overlayView.findViewById<TextView>(R.id.tvOverlayAppStatus)
        val cardFingerprint = overlayView.findViewById<View>(R.id.cardFingerprint)
        val tvForgotPassword = overlayView.findViewById<TextView>(R.id.tvOverlayForgotPassword)

        tvTitle?.text = "AppLocker Protected 🔒"
        tvAppName?.text = getString(R.string.app_name)
        imgIcon?.setImageResource(R.mipmap.ic_launcher)

        btnExit?.setOnClickListener {
            finishAffinity() // Exit AppLocker
        }

        // Fingerprint button setup
        val prefs = getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
        val isFingerprintEnabled = prefs.getBoolean("fingerprint_unlock_enabled", false)
        if (isFingerprintEnabled && isBiometricHardwareAvailable()) {
            cardFingerprint?.visibility = View.VISIBLE
            cardFingerprint?.setOnClickListener {
                selfLockBiometricPromptDismissed = false
                triggerBiometricPrompt()
            }
        } else {
            cardFingerprint.visibility = View.GONE
        }

        val lockType = lockSettingsManager?.getLockType() ?: "pattern"
        wrongAttempts = 0

        val patternView = overlayView.findViewById<PatternLockView>(R.id.overlayPatternView)
        val includePin = overlayView.findViewById<View>(R.id.includeOverlayPinInput)
        val includePassword = overlayView.findViewById<View>(R.id.includeOverlayPasswordInput)

        when (lockType) {
            "pin" -> {
                patternView.visibility = View.GONE
                includePin.visibility = View.VISIBLE
                includePassword.visibility = View.GONE
                statusText.text = "Enter your PIN code to access AppLocker"
                setupOverlayPinKeypad(overlayView, statusText)
            }
            "password" -> {
                patternView.visibility = View.GONE
                includePin.visibility = View.GONE
                includePassword.visibility = View.VISIBLE
                statusText.text = "Enter your password to access AppLocker"
                setupOverlayPasswordForm(overlayView, statusText)
            }
            else -> { // pattern
                patternView.visibility = View.VISIBLE
                includePin.visibility = View.GONE
                includePassword.visibility = View.GONE
                statusText.text = "Draw your unlock pattern to access AppLocker"
                setupOverlayPatternListener(overlayView, statusText)
            }
        }

        tvForgotPassword.setOnClickListener {
            if (lockSettingsManager?.isSecurityQuestionSet() == true) {
                val intent = Intent(this, SecurityQuestionResetActivity::class.java).apply {
                    putExtra("target_package", packageName)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "No Security Question set!", Toast.LENGTH_LONG).show()
            }
        }

        // Check active lockout state
        val deadline = lockSettingsManager?.getLockoutDeadline() ?: 0L
        val currentTime = System.currentTimeMillis()
        if (lockSettingsManager?.isFailedAttemptsLockoutEnabled() == true && currentTime < deadline) {
            startLockoutCountdown(deadline - currentTime)
        } else {
            enablePasscodeInputs(true)
        }

        // Trigger biometrics if enabled, not already showing, and not previously dismissed this session
        if (isFingerprintEnabled && isBiometricHardwareAvailable() && !isBiometricPromptShowing && !selfLockBiometricPromptDismissed) {
            isBiometricPromptShowing = true
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isAppAuthenticated && binding.appLockAuthOverlay.root.visibility == View.VISIBLE && !selfLockBiometricPromptDismissed) {
                    triggerBiometricPrompt()
                } else {
                    isBiometricPromptShowing = false
                }
            }, 250)
        }
    }

    private fun hideSelfLockOverlay() {
        lockoutTimer?.cancel()
        lockoutTimer = null
        isBiometricPromptShowing = false
        selfLockBiometricPromptDismissed = false
        binding.appLockAuthOverlay.root.findViewById<PatternLockView>(R.id.overlayPatternView)?.clearPattern()
        binding.appLockAuthOverlay.root.visibility = View.GONE
    }

    private fun handleUnlockSuccess() {
        wrongAttempts = 0
        isAppAuthenticated = true
        hideSelfLockOverlay()
        Toast.makeText(this, "Welcome to AppLocker 🔓", Toast.LENGTH_SHORT).show()
    }

    private fun handleWrongAttempt(statusText: TextView, message: String) {
        wrongAttempts++
        statusText.text = "$message (Attempt #$wrongAttempts)"
        statusText.setTextColor(0xFFFF5252.toInt())

        if (lockSettingsManager?.isFailedAttemptsLockoutEnabled() == true && wrongAttempts >= 5) {
            val durationMs = 30000L
            val deadline = System.currentTimeMillis() + durationMs
            lockSettingsManager?.setLockoutDeadline(deadline)
            startLockoutCountdown(durationMs)
            return
        }

        if (lockSettingsManager?.isIntruderEnabled() == true &&
            wrongAttempts >= (lockSettingsManager?.getIntruderLimit() ?: 2)
        ) {
            IntruderSelfieHelper.captureSelfie(this, this, packageName, wrongAttempts) { file ->
                Handler(Looper.getMainLooper()).post {
                    if (file != null) {
                        showSelfLockIntruderBanner()
                    }
                }
            }
        }
    }

    private fun showSelfLockIntruderBanner() {
        val container = binding.appLockAuthOverlay.root.findViewById<View>(R.id.intruderBannerContainer) ?: return
        container.visibility = View.VISIBLE
        container.alpha = 0f
        container.translationY = 40f
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .withEndAction {
                Handler(Looper.getMainLooper()).postDelayed({
                    container.animate()
                        .alpha(0f)
                        .translationY(40f)
                        .setDuration(300)
                        .withEndAction {
                            container.visibility = View.GONE
                        }
                        .start()
                }, 3000L)
            }
            .start()
    }

    private fun setupOverlayPatternListener(view: View, statusText: TextView) {
        val patternView = view.findViewById<PatternLockView>(R.id.overlayPatternView)
        patternView.setOnPatternListener(object : PatternLockView.OnPatternListener {
            override fun onPatternComplete(pattern: List<Int>) {
                if (lockSettingsManager?.verifyPattern(pattern) == true) {
                    patternView.clearPattern()
                    handleUnlockSuccess()
                } else {
                    patternView.setPatternError()
                    handleWrongAttempt(statusText, "Wrong pattern!")
                    Handler(Looper.getMainLooper()).postDelayed({
                        patternView.clearPattern()
                        statusText.text = "Draw your unlock pattern to access AppLocker"
                        resetStatusTextColor(statusText)
                    }, 1000)
                }
            }
        })
    }

    private fun setupOverlayPinKeypad(view: View, statusText: TextView) {
        val pinLayout = view.findViewById<View>(R.id.includeOverlayPinInput)
        enteredPin = ""
        updateOverlayPinDots(pinLayout)

        val keys = listOf(
            R.id.btnKey0 to "0", R.id.btnKey1 to "1", R.id.btnKey2 to "2",
            R.id.btnKey3 to "3", R.id.btnKey4 to "4", R.id.btnKey5 to "5",
            R.id.btnKey6 to "6", R.id.btnKey7 to "7", R.id.btnKey8 to "8",
            R.id.btnKey9 to "9"
        )

        keys.forEach { (id, digit) ->
            pinLayout.findViewById<MaterialButton>(id)?.setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin += digit
                    updateOverlayPinDots(pinLayout)
                }
            }
        }

        pinLayout.findViewById<MaterialButton>(R.id.btnKeyDelete)?.setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.substring(0, enteredPin.length - 1)
                updateOverlayPinDots(pinLayout)
            }
        }

        pinLayout.findViewById<MaterialButton>(R.id.btnKeyConfirm)?.setOnClickListener {
            if (lockSettingsManager?.verifyPin(enteredPin) == true) {
                handleUnlockSuccess()
            } else {
                handleWrongAttempt(statusText, "Wrong PIN code!")
                Handler(Looper.getMainLooper()).postDelayed({
                    enteredPin = ""
                    updateOverlayPinDots(pinLayout)
                    statusText.text = "Enter your PIN code to access AppLocker"
                    resetStatusTextColor(statusText)
                }, 1000)
            }
        }
    }

    private fun updateOverlayPinDots(pinLayout: View) {
        val dotIds = listOf(R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4)
        dotIds.forEachIndexed { index, id ->
            val dotView = pinLayout.findViewById<View>(id)
            if (index < enteredPin.length) {
                dotView?.setBackgroundResource(R.drawable.bg_pin_dot_filled)
            } else {
                dotView?.setBackgroundResource(R.drawable.bg_pin_dot_empty)
            }
        }
    }

    private fun setupOverlayPasswordForm(view: View, statusText: TextView) {
        val passLayout = view.findViewById<View>(R.id.includeOverlayPasswordInput)
        val btnConfirm = passLayout.findViewById<MaterialButton>(R.id.btnPasswordConfirm)
        val etPassword = passLayout.findViewById<TextInputEditText>(R.id.etPasswordInput)

        etPassword.setText("")
        btnConfirm.setOnClickListener {
            val input = etPassword.text.toString().trim()
            if (lockSettingsManager?.verifyPassword(input) == true) {
                handleUnlockSuccess()
            } else {
                handleWrongAttempt(statusText, "Wrong password!")
                Handler(Looper.getMainLooper()).postDelayed({
                    etPassword.setText("")
                    statusText.text = "Enter your password to access AppLocker"
                    resetStatusTextColor(statusText)
                }, 1000)
            }
        }
    }

    private fun isBiometricHardwareAvailable(): Boolean {
        val biometricManager = androidx.biometric.BiometricManager.from(this)
        val result = biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return result == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun triggerBiometricPrompt() {
        val biometricPrompt = androidx.biometric.BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isBiometricPromptShowing = false
                    handleUnlockSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isBiometricPromptShowing = false
                    selfLockBiometricPromptDismissed = true
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )

        val lockType = lockSettingsManager?.getLockType() ?: "pattern"
        val negativeBtnText = when (lockType) {
            "pin" -> "Use PIN"
            "password" -> "Use Password"
            else -> "Use Pattern"
        }

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Unlock")
            .setSubtitle("Touch sensor to unlock AppLocker")
            .setNegativeButtonText(negativeBtnText)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            android.util.Log.e("AppLockDebug", "Failed to show biometric prompt in MainActivity", e)
            isBiometricPromptShowing = false
        }
    }

    private fun startLockoutCountdown(durationMs: Long) {
        enablePasscodeInputs(false)
        val statusText = binding.appLockAuthOverlay.root.findViewById<TextView>(R.id.tvOverlayAppStatus) ?: return

        lockoutTimer?.cancel()
        lockoutTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                statusText.text = "Too many wrong attempts. Try again in $seconds seconds."
                statusText.setTextColor(0xFFFF5252.toInt())
            }

            override fun onFinish() {
                statusText.text = when (lockSettingsManager?.getLockType()) {
                    "pin" -> "Enter your PIN code to access AppLocker"
                    "password" -> "Enter your password to access AppLocker"
                    else -> "Draw your unlock pattern to access AppLocker"
                }
                resetStatusTextColor(statusText)
                enablePasscodeInputs(true)
                wrongAttempts = 0
                lockSettingsManager?.setLockoutDeadline(0L)
            }
        }.start()
    }

    private fun enablePasscodeInputs(enabled: Boolean) {
        val view = binding.appLockAuthOverlay.root
        view.findViewById<PatternLockView>(R.id.overlayPatternView)?.isEnabled = enabled

        val pinLayout = view.findViewById<View>(R.id.includeOverlayPinInput)
        if (pinLayout != null) {
            val keyIds = listOf(
                R.id.btnKey0, R.id.btnKey1, R.id.btnKey2, R.id.btnKey3,
                R.id.btnKey4, R.id.btnKey5, R.id.btnKey6, R.id.btnKey7,
                R.id.btnKey8, R.id.btnKey9, R.id.btnKeyDelete, R.id.btnKeyConfirm
            )
            keyIds.forEach { id -> pinLayout.findViewById<View>(id)?.isEnabled = enabled }
        }

        val passLayout = view.findViewById<View>(R.id.includeOverlayPasswordInput)
        if (passLayout != null) {
            passLayout.findViewById<View>(R.id.etPasswordInput)?.isEnabled = enabled
            passLayout.findViewById<View>(R.id.btnPasswordConfirm)?.isEnabled = enabled
        }
    }

    private fun resetStatusTextColor(statusText: TextView) {
        val prefs = getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
        val themeId = prefs.getInt("selected_theme_id", 1)
        val textColor = when (themeId) {
            1 -> 0xFF5C6479.toInt()
            2 -> 0xFFB0BEC5.toInt()
            3 -> 0xFF90A4AE.toInt()
            4 -> 0xFFA5D6A7.toInt()
            else -> 0xFF5C6479.toInt()
        }
        statusText.setTextColor(textColor)
    }

    private fun applyThemeToOverlay(view: View, themeId: Int) {
        val rootLayout = view.findViewById<View>(R.id.rootOverlayLayout) ?: view
        val patternView = view.findViewById<PatternLockView>(R.id.overlayPatternView)
        val tvTitle = view.findViewById<TextView>(R.id.tvOverlayLockedTitle)
        val tvAppName = view.findViewById<TextView>(R.id.tvOverlayAppName)
        val tvStatus = view.findViewById<TextView>(R.id.tvOverlayAppStatus)
        val btnExit = view.findViewById<ImageView>(R.id.btnOverlayExit)

        val theme = ThemeConfig.getById(themeId)
        rootLayout.setBackgroundResource(theme.drawableRes)

        patternView?.setPatternColors(theme.normalColor, theme.selectedColor, theme.lineColor)
        patternView?.setPatternEmojis(theme.normalEmoji, theme.selectedEmoji)
        tvTitle?.setTextColor(theme.primaryTextColor)
        tvAppName?.setTextColor(theme.primaryTextColor)
        tvStatus?.setTextColor(theme.secondaryTextColor)
        btnExit?.setColorFilter(theme.primaryTextColor)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
    }

    private fun startAppLockService() {
        android.util.Log.d("AppLockDebug", "MainActivity: Attempting to start AppLockService")
        try {
            val serviceIntent = Intent(this, AppLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            android.util.Log.d("AppLockDebug", "MainActivity: startForegroundService called successfully")
        } catch (e: Exception) {
            android.util.Log.e("AppLockDebug", "MainActivity: Failed to start service", e)
        }
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}

