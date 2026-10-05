package com.applockerapp.presentation.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.applockerapp.R
import com.applockerapp.presentation.customview.PatternLockView
import com.applockerapp.presentation.lock.BiometricAuthActivity
import com.applockerapp.presentation.service.AppLockService
import com.applockerapp.utils.LockSettingsManager

class WidgetAuthActivity : AppCompatActivity() {

    private lateinit var lockSettingsManager: LockSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockSettingsManager = LockSettingsManager(this)
        // TODO: Screenshot protection disabled temporarily — re-enable later
        // if (lockSettingsManager.isScreenshotProtectionEnabled()) {
        //     window.setFlags(
        //         android.view.WindowManager.LayoutParams.FLAG_SECURE,
        //         android.view.WindowManager.LayoutParams.FLAG_SECURE
        //     )
        // }

        val isEnabled = lockSettingsManager.isMasterLockEnabled()

        if (!isEnabled) {
            // Turning ON protection: no verification needed
            lockSettingsManager.setMasterLockEnabled(true)
            
            // Launch/resume background monitoring
            val serviceIntent = Intent(this, AppLockService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            AppLockWidgetProvider.triggerUpdate(this)
            Toast.makeText(this, "AppLocker Protection Active! 🔒", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Turning OFF protection: needs verification!
        setContentView(R.layout.activity_widget_auth)
        setupViews()

        // Auto trigger biometric prompt if configured & supported
        val prefs = getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
        val isFingerprintEnabled = prefs.getBoolean("fingerprint_unlock_enabled", false)
        if (isFingerprintEnabled && isBiometricHardwareAvailable()) {
            Handler(Looper.getMainLooper()).postDelayed({
                triggerBiometricPrompt()
            }, 250)
        }
    }

    private fun setupViews() {
        val patternView = findViewById<PatternLockView>(R.id.widgetAuthPatternView)
        val tvSubtitle = findViewById<TextView>(R.id.tvWidgetAuthSubtitle)
        val btnCancel = findViewById<View>(R.id.btnCancelWidgetAuth)

        btnCancel.setOnClickListener {
            finish()
        }

        patternView.setOnPatternListener(object : PatternLockView.OnPatternListener {
            override fun onPatternComplete(pattern: List<Int>) {
                if (lockSettingsManager.verifyPattern(pattern)) {
                    patternView.clearPattern()
                    onAuthSuccess()
                } else {
                    patternView.setPatternError()
                    tvSubtitle.text = "Incorrect pattern! Try again."
                    tvSubtitle.setTextColor(0xFFFF5252.toInt())
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        patternView.clearPattern()
                        tvSubtitle.text = "Draw pattern to pause protection"
                        tvSubtitle.setTextColor(0xFF8894B4.toInt()) // text_secondary
                    }, 1000)
                }
            }
        })
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
        BiometricAuthActivity.onAuthSuccess = {
            onAuthSuccess()
        }
        val intent = Intent(this, BiometricAuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        startActivity(intent)
    }

    private fun onAuthSuccess() {
        lockSettingsManager.setMasterLockEnabled(false)
        
        // Notify AppLockService to dismiss overlay immediately if running
        val serviceIntent = Intent(this, AppLockService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        AppLockWidgetProvider.triggerUpdate(this)
        Toast.makeText(this, "AppLocker Protection Paused! 🔓", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
