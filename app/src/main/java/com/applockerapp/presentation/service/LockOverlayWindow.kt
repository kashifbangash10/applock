package com.applockerapp.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleOwner
import com.applockerapp.R
import com.applockerapp.presentation.MainActivity
import com.applockerapp.presentation.customview.PatternLockView
import com.applockerapp.presentation.lock.BiometricAuthActivity
import com.applockerapp.presentation.lock.SecurityQuestionResetActivity
import com.applockerapp.presentation.themes.ThemeConfig
import com.applockerapp.utils.IntruderSelfieHelper
import com.applockerapp.utils.LockSettingsManager
import com.google.android.material.button.MaterialButton

class LockOverlayWindow(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onUnlockSuccess: (String) -> Unit
) {
    companion object {
        private const val TAG = "LockOverlayWindow"
        private const val NOTIFICATION_ID_ALERT_PREFIX = 91000
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val lockSettingsManager = LockSettingsManager(context)
    private var overlayView: View? = null
    private var currentPackage: String? = null
    private var wrongAttempts = 0

    private var lockoutTimer: android.os.CountDownTimer? = null
    private var isBiometricPromptShowing = false
    private var isBiometricPromptDismissed = false

    fun show(packageName: String) {
        // If already showing for this package, do nothing
        if (overlayView != null && currentPackage == packageName) return

        // If showing for different package, update it
        if (overlayView != null) {
            currentPackage = packageName
            wrongAttempts = 0
            isBiometricPromptDismissed = false
            updateAppDetails()

            // Check lockout state for new package window
            val view = overlayView!!
            val patternView = view.findViewById<PatternLockView>(R.id.overlayPatternView)
            patternView?.clearPattern()
            val statusText = view.findViewById<TextView>(R.id.tvOverlayAppStatus)
            val deadline = lockSettingsManager.getLockoutDeadline()
            val currentTime = System.currentTimeMillis()
            if (lockSettingsManager.isFailedAttemptsLockoutEnabled() && currentTime < deadline) {
                startLockoutCountdown(deadline - currentTime)
            } else {
                enablePasscodeInputs(true)
                resetStatusTextColor(statusText)
                val lockType = lockSettingsManager.getLockType()
                statusText.text = when (lockType) {
                    "pin" -> "Enter your PIN code to access"
                    "password" -> "Enter your password to access"
                    else -> "Draw your unlock pattern to access"
                }
            }
            return
        }

        currentPackage = packageName
        wrongAttempts = 0
        isBiometricPromptShowing = false
        isBiometricPromptDismissed = false

        // Use ContextThemeWrapper so theme attributes resolve correctly
        val themedContext = ContextThemeWrapper(context, R.style.Theme_AppLockerApp)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.layout_lock_overlay, null)

        val isSecure = lockSettingsManager.isScreenshotProtectionEnabled()
        var windowFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        // TODO: Screenshot protection disabled temporarily — re-enable later
        // if (isSecure) {
        //     windowFlags = windowFlags or WindowManager.LayoutParams.FLAG_SECURE
        // }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }

        // Read and apply active theme
        val prefs = context.getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
        val themeId = prefs.getInt("selected_theme_id", 1)
        applyThemeToOverlay(view, themeId)

        setupOverlayListeners(view)

        try {
            windowManager.addView(view, params)
            // IMPORTANT: assign overlayView BEFORE calling updateAppDetails()
            // so that the function can find the view and set the real app icon.
            overlayView = view
            updateAppDetails()
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
            Log.d(TAG, "Overlay shown for: $packageName")

            // Force PatternLockView to initialize dots immediately
            val patternView = view.findViewById<PatternLockView>(R.id.overlayPatternView)
            patternView?.post {
                patternView.clearPattern()
                patternView.invalidate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view: ${e.message}")
            overlayView = null
            return
        }

        // Auto trigger biometric scan if enabled
        checkAndTriggerBiometrics()
    }

    private fun checkAndTriggerBiometrics() {
        if (isBiometricPromptShowing || isBiometricPromptDismissed) return
        val prefs = context.getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
        val isFingerprintEnabled = prefs.getBoolean("fingerprint_unlock_enabled", false)
        if (isFingerprintEnabled && isBiometricHardwareAvailable()) {
            isBiometricPromptShowing = true
            Handler(Looper.getMainLooper()).postDelayed({
                if (isShowing() && !isBiometricPromptDismissed) {
                    triggerBiometricPrompt()
                } else {
                    isBiometricPromptShowing = false
                }
            }, 250)
        }
    }

    private fun isBiometricHardwareAvailable(): Boolean {
        val biometricManager = androidx.biometric.BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return result == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun triggerBiometricPrompt() {
        BiometricAuthActivity.onAuthSuccess = {
            val pkg = currentPackage ?: ""
            Log.d(TAG, "Biometric unlock success: $pkg")
            isBiometricPromptShowing = false
            onUnlockSuccess(pkg)
            hide()
        }
        BiometricAuthActivity.onAuthCancelled = {
            Log.d(TAG, "Biometric auth cancelled by user")
            isBiometricPromptShowing = false
            isBiometricPromptDismissed = true
        }
        val intent = Intent(context, BiometricAuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BiometricAuthActivity", e)
            isBiometricPromptShowing = false
        }
    }

    fun hide() {
        lockoutTimer?.cancel()
        lockoutTimer = null
        isBiometricPromptShowing = false
        overlayView?.let { view ->
            try {
                view.findViewById<PatternLockView>(R.id.overlayPatternView)?.clearPattern()
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                } else {
                    windowManager.removeView(view)
                }
                Log.d(TAG, "Overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay: ${e.message}")
            }
            overlayView = null
            currentPackage = null
        }
    }

    fun isShowing(): Boolean = overlayView != null

    private var enteredOverlayPin = ""

    private fun setupOverlayListeners(view: View) {
        val patternView = view.findViewById<PatternLockView>(R.id.overlayPatternView)
        val btnExit = view.findViewById<ImageView>(R.id.btnOverlayExit)
        val statusText = view.findViewById<TextView>(R.id.tvOverlayAppStatus)
        val cardFingerprint = view.findViewById<View>(R.id.cardFingerprint)
        val tvForgotPassword = view.findViewById<TextView>(R.id.tvOverlayForgotPassword)

        val includePin = view.findViewById<View>(R.id.includeOverlayPinInput)
        val includePassword = view.findViewById<View>(R.id.includeOverlayPasswordInput)

        // Exit button → go to home screen
        btnExit.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            hide()
        }

        // Setup Fingerprint button listener if available
        val prefs = context.getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
        val isFingerprintEnabled = prefs.getBoolean("fingerprint_unlock_enabled", false)
        if (isFingerprintEnabled && isBiometricHardwareAvailable()) {
            cardFingerprint.visibility = View.VISIBLE
            cardFingerprint.setOnClickListener {
                triggerBiometricPrompt()
            }
        } else {
            cardFingerprint.visibility = View.GONE
        }

        val lockType = lockSettingsManager.getLockType()
        wrongAttempts = 0

        when (lockType) {
            "pin" -> {
                patternView.visibility = View.GONE
                includePin.visibility = View.VISIBLE
                includePassword.visibility = View.GONE
                statusText.text = "Enter your PIN code to access"
                setupOverlayPinKeypad(view, statusText)
            }
            "password" -> {
                patternView.visibility = View.GONE
                includePin.visibility = View.GONE
                includePassword.visibility = View.VISIBLE
                statusText.text = "Enter your password to access"
                setupOverlayPasswordForm(view, statusText)
            }
            else -> { // pattern
                patternView.visibility = View.VISIBLE
                includePin.visibility = View.GONE
                includePassword.visibility = View.GONE
                statusText.text = "Draw your unlock pattern to access"
                setupOverlayPatternListener(view, statusText)
            }
        }

        // Forgot Password listener
        tvForgotPassword.setOnClickListener {
            if (lockSettingsManager.isSecurityQuestionSet()) {
                val intent = Intent(context, SecurityQuestionResetActivity::class.java)
                intent.putExtra("target_package", currentPackage)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "No Security Question set! Please clear app data.", Toast.LENGTH_LONG).show()
            }
        }

        // Apply lockout check initially
        val deadline = lockSettingsManager.getLockoutDeadline()
        val currentTime = System.currentTimeMillis()
        if (lockSettingsManager.isFailedAttemptsLockoutEnabled() && currentTime < deadline) {
            startLockoutCountdown(deadline - currentTime)
        } else {
            enablePasscodeInputs(true)
        }
    }

    private fun handleUnlockSuccess() {
        wrongAttempts = 0
        val pkg = currentPackage ?: ""
        Log.d(TAG, "Unlock success for: $pkg")
        onUnlockSuccess(pkg)
        hide()
    }

    private fun handleWrongAttempt(statusText: TextView, message: String) {
        wrongAttempts++
        statusText.text = "$message (Attempt #$wrongAttempts)"
        statusText.setTextColor(0xFFFF5252.toInt())
        
        val pkg = currentPackage ?: ""
        sendWrongAttemptNotification(pkg, wrongAttempts)

        if (lockSettingsManager.isFailedAttemptsLockoutEnabled() && wrongAttempts >= 5) {
            val durationMs = 30000L // 30 seconds
            val deadline = System.currentTimeMillis() + durationMs
            lockSettingsManager.setLockoutDeadline(deadline)
            startLockoutCountdown(durationMs)
            return
        }

        if (lockSettingsManager.isIntruderEnabled() &&
            wrongAttempts >= lockSettingsManager.getIntruderLimit()
        ) {
            Log.d(TAG, "Capturing intruder selfie (attempt #$wrongAttempts)")
            IntruderSelfieHelper.captureSelfie(context, lifecycleOwner, pkg, wrongAttempts) { file ->
                Handler(Looper.getMainLooper()).post {
                    if (file != null) {
                        showIntruderBanner()
                    }
                }
            }
        }
    }

    private fun showIntruderBanner() {
        val container = overlayView?.findViewById<View>(R.id.intruderBannerContainer) ?: return
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

    private fun resetStatusTextColor(statusText: TextView) {
        val prefs = context.getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
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

    private fun setupOverlayPinKeypad(view: View, statusText: TextView) {
        val pinLayout = view.findViewById<View>(R.id.includeOverlayPinInput)
        enteredOverlayPin = ""
        updateOverlayPinDots(pinLayout)

        val keys = listOf(
            R.id.btnKey0 to "0", R.id.btnKey1 to "1", R.id.btnKey2 to "2",
            R.id.btnKey3 to "3", R.id.btnKey4 to "4", R.id.btnKey5 to "5",
            R.id.btnKey6 to "6", R.id.btnKey7 to "7", R.id.btnKey8 to "8",
            R.id.btnKey9 to "9"
        )

        keys.forEach { (id, digit) ->
            pinLayout.findViewById<MaterialButton>(id)?.setOnClickListener {
                if (enteredOverlayPin.length < 4) {
                    enteredOverlayPin += digit
                    updateOverlayPinDots(pinLayout)
                }
            }
        }

        pinLayout.findViewById<MaterialButton>(R.id.btnKeyDelete)?.setOnClickListener {
            if (enteredOverlayPin.isNotEmpty()) {
                enteredOverlayPin = enteredOverlayPin.substring(0, enteredOverlayPin.length - 1)
                updateOverlayPinDots(pinLayout)
            }
        }

        pinLayout.findViewById<MaterialButton>(R.id.btnKeyConfirm)?.setOnClickListener {
            if (lockSettingsManager.verifyPin(enteredOverlayPin)) {
                handleUnlockSuccess()
            } else {
                handleWrongAttempt(statusText, "Wrong PIN code!")
                Handler(Looper.getMainLooper()).postDelayed({
                    enteredOverlayPin = ""
                    updateOverlayPinDots(pinLayout)
                    statusText.text = "Enter your PIN code to access"
                    resetStatusTextColor(statusText)
                }, 1000)
            }
        }
    }

    private fun updateOverlayPinDots(pinLayout: View) {
        val dotIds = listOf(
            R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4
        )
        dotIds.forEachIndexed { index, id ->
            val dotView = pinLayout.findViewById<View>(id)
            if (index < enteredOverlayPin.length) {
                dotView?.setBackgroundResource(R.drawable.bg_pin_dot_filled)
            } else {
                dotView?.setBackgroundResource(R.drawable.bg_pin_dot_empty)
            }
        }
    }

    private fun setupOverlayPasswordForm(view: View, statusText: TextView) {
        val passLayout = view.findViewById<View>(R.id.includeOverlayPasswordInput)
        val btnConfirm = passLayout.findViewById<MaterialButton>(R.id.btnPasswordConfirm)
        val etPassword = passLayout.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPasswordInput)

        etPassword.setText("")

        btnConfirm.setOnClickListener {
            val input = etPassword.text.toString().trim()
            if (lockSettingsManager.verifyPassword(input)) {
                handleUnlockSuccess()
            } else {
                handleWrongAttempt(statusText, "Wrong password!")
                Handler(Looper.getMainLooper()).postDelayed({
                    etPassword.setText("")
                    statusText.text = "Enter your password to access"
                    resetStatusTextColor(statusText)
                }, 1000)
            }
        }
    }

    private fun setupOverlayPatternListener(view: View, statusText: TextView) {
        val patternView = view.findViewById<PatternLockView>(R.id.overlayPatternView)
        patternView.setOnPatternListener(object : PatternLockView.OnPatternListener {
            override fun onPatternComplete(pattern: List<Int>) {
                if (lockSettingsManager.verifyPattern(pattern)) {
                    patternView.clearPattern()
                    handleUnlockSuccess()
                } else {
                    patternView.setPatternError()
                    handleWrongAttempt(statusText, "Wrong pattern!")
                    Handler(Looper.getMainLooper()).postDelayed({
                        patternView.clearPattern()
                        statusText.text = "Draw your unlock pattern to access"
                        resetStatusTextColor(statusText)
                    }, 1000)
                }
            }
        })
    }

    private fun sendWrongAttemptNotification(pkg: String, attemptCount: Int) {
        val prefs = context.getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
        val alertsEnabled = prefs.getBoolean("pref_wrong_attempt_notification", true)
        if (!alertsEnabled) return

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg
        }

        val channelId = "applocker_security_alerts"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Failed unlock attempts warnings"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Failed Unlock Attempt! ⚠️")
            .setContentText("Wrong attempt on $appName (Attempt #$attemptCount)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ALERT_PREFIX + pkg.hashCode(), notification)
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

    private fun updateAppDetails() {
        val view = overlayView ?: return
        val imgIcon = view.findViewById<ImageView>(R.id.imgOverlayAppIcon)
        val tvAppName = view.findViewById<TextView>(R.id.tvOverlayAppName)
        val pkg = currentPackage ?: return

        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            tvAppName.text = pm.getApplicationLabel(appInfo).toString()
            imgIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
        } catch (e: Exception) {
            tvAppName.text = pkg
            imgIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }
    }

    private fun startLockoutCountdown(durationMs: Long) {
        enablePasscodeInputs(false)
        val statusText = overlayView?.findViewById<TextView>(R.id.tvOverlayAppStatus) ?: return

        lockoutTimer?.cancel()
        lockoutTimer = object : android.os.CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                statusText.text = "Too many wrong attempts. Try again in $seconds seconds."
                statusText.setTextColor(0xFFFF5252.toInt())
            }

            override fun onFinish() {
                statusText.text = when (lockSettingsManager.getLockType()) {
                    "pin" -> "Enter your PIN code to access"
                    "password" -> "Enter your password to access"
                    else -> "Draw your unlock pattern to access"
                }
                resetStatusTextColor(statusText)
                enablePasscodeInputs(true)
                wrongAttempts = 0
                lockSettingsManager.setLockoutDeadline(0L)
            }
        }.start()
    }

    private fun enablePasscodeInputs(enabled: Boolean) {
        val view = overlayView ?: return

        // Disable/enable Pattern View
        val patternView = view.findViewById<PatternLockView>(R.id.overlayPatternView)
        patternView?.isEnabled = enabled

        // Disable/enable PIN Input layout buttons
        val pinLayout = view.findViewById<View>(R.id.includeOverlayPinInput)
        if (pinLayout != null) {
            val keyIds = listOf(
                R.id.btnKey0, R.id.btnKey1, R.id.btnKey2, R.id.btnKey3,
                R.id.btnKey4, R.id.btnKey5, R.id.btnKey6, R.id.btnKey7,
                R.id.btnKey8, R.id.btnKey9, R.id.btnKeyDelete, R.id.btnKeyConfirm
            )
            keyIds.forEach { id ->
                pinLayout.findViewById<View>(id)?.isEnabled = enabled
            }
        }

        // Disable/enable Password inputs
        val passLayout = view.findViewById<View>(R.id.includeOverlayPasswordInput)
        if (passLayout != null) {
            passLayout.findViewById<View>(R.id.etPasswordInput)?.isEnabled = enabled
            passLayout.findViewById<View>(R.id.btnPasswordConfirm)?.isEnabled = enabled
        }
    }
}
