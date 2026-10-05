package com.applockerapp.presentation.lock

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.applockerapp.utils.LockSettingsManager
import java.util.concurrent.Executor

class BiometricAuthActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BiometricAuthActivity"
        
        // Dynamic callback bridge
        var onAuthSuccess: (() -> Unit)? = null
        var onAuthCancelled: (() -> Unit)? = null
    }

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lockSettingsManager = LockSettingsManager(this)
        // TODO: Screenshot protection disabled temporarily — re-enable later
        // if (lockSettingsManager.isScreenshotProtectionEnabled()) {
        //     window.setFlags(
        //         android.view.WindowManager.LayoutParams.FLAG_SECURE,
        //         android.view.WindowManager.LayoutParams.FLAG_SECURE
        //     )
        // }
        executor = ContextCompat.getMainExecutor(this)
        setupBiometricPrompt()
        showBiometricPrompt()
    }

    private fun getFallbackButtonText(): String {
        val lockSettings = LockSettingsManager(this)
        return when (lockSettings.getLockType()) {
            "pin" -> "Use PIN"
            "password" -> "Use Password"
            else -> "Use Pattern"
        }
    }

    private fun setupBiometricPrompt() {
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.d(TAG, "Auth error: $errString ($errorCode)")
                    onAuthSuccess = null
                    val cancelCb = onAuthCancelled
                    onAuthCancelled = null
                    cancelCb?.invoke()
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        Toast.makeText(applicationContext, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Auth success!")
                    val callback = onAuthSuccess
                    onAuthSuccess = null
                    callback?.invoke()
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d(TAG, "Auth failed — fingerprint not recognized")
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Unlock")
            .setSubtitle("Touch sensor to unlock this app")
            .setNegativeButtonText(getFallbackButtonText())
            .build()
    }

    private fun showBiometricPrompt() {
        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show biometric prompt: ${e.message}")
            onAuthSuccess = null
            finish()
        }
    }

    override fun onDestroy() {
        onAuthSuccess = null
        onAuthCancelled = null
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
