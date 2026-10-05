package com.applockerapp.presentation.lock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.applockerapp.R
import com.applockerapp.databinding.ActivityLockSetupBinding
import com.applockerapp.presentation.MainActivity
import com.applockerapp.presentation.customview.PatternLockView
import com.applockerapp.utils.LockSettingsManager
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LockSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockSetupBinding
    private lateinit var lockSettingsManager: LockSettingsManager
    
    private var lockType = "pattern"
    private var fromOnboarding = false
    
    // Pattern State
    private var firstPattern: List<Int>? = null
    
    // PIN State
    private var firstPin: String? = null
    private var enteredPin = ""
    
    // Password State
    private var firstPassword: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lockSettingsManager = LockSettingsManager(this)
        // TODO: Screenshot protection disabled temporarily — re-enable later
        // if (lockSettingsManager.isScreenshotProtectionEnabled()) {
        //     window.setFlags(
        //         android.view.WindowManager.LayoutParams.FLAG_SECURE,
        //         android.view.WindowManager.LayoutParams.FLAG_SECURE
        //     )
        // }

        val forceSetup = intent.getBooleanExtra("force_setup", false)
        fromOnboarding = intent.getBooleanExtra("from_onboarding", false)
        val forceLockType = intent.getStringExtra("force_lock_type")
        if (forceLockType != null) {
            lockSettingsManager.setLockType(forceLockType)
        }

        lockType = lockSettingsManager.getLockType()

        // If lock is already configured and not in force setup, skip to main screen
        if (!forceSetup && lockSettingsManager.isAnyLockSet()) {
            startMainActivity()
            return
        }

        setupViews()
    }

    private fun setupViews() {
        // Toggle input views based on lock type
        when (lockType) {
            "pin" -> {
                binding.patternLockView.visibility = View.GONE
                binding.includePinInput.root.visibility = View.VISIBLE
                binding.includePasswordInput.root.visibility = View.GONE
                binding.btnResetSetup.text = "Reset PIN"
                binding.tvSetupTitle.text = "Set PIN Lock"
                binding.tvSetupSubtitle.text = "Enter 4 digit PIN to protect apps"
                setupPinKeypad()
            }
            "password" -> {
                binding.patternLockView.visibility = View.GONE
                binding.includePinInput.root.visibility = View.GONE
                binding.includePasswordInput.root.visibility = View.VISIBLE
                binding.btnResetSetup.visibility = View.GONE
                binding.tvSetupTitle.text = "Set Password"
                binding.tvSetupSubtitle.text = "Enter password to protect apps"
                setupPasswordForm()
            }
            else -> { // pattern
                binding.patternLockView.visibility = View.VISIBLE
                binding.includePinInput.root.visibility = View.GONE
                binding.includePasswordInput.root.visibility = View.GONE
                binding.btnResetSetup.visibility = View.VISIBLE
                binding.btnResetSetup.text = "Reset Pattern"
                binding.tvSetupTitle.text = "Set Pattern Lock"
                binding.tvSetupSubtitle.text = "Draw a pattern to protect your applications"
                // Apply Steel Cyan color theme to pattern dots (matches app theme)
                binding.patternLockView.setPatternColors(
                    normalColor  = 0xFF5A6582.toInt(),   // muted gray for unselected
                    selectedColor = 0xFF38BDF8.toInt(),  // primary Steel Cyan for selected
                    lineColor    = 0xAA38BDF8.toInt()    // semi-transparent Steel Cyan line
                )
                setupPatternCanvas()
            }
        }

        binding.btnResetSetup.setOnClickListener {
            resetSetupState()
        }
    }

    // ==================== PATTERN LOCK SETUP ====================
    private fun setupPatternCanvas() {
        binding.patternLockView.setOnPatternListener(object : PatternLockView.OnPatternListener {
            override fun onPatternComplete(pattern: List<Int>) {
                handlePatternDrawn(pattern)
            }
        })
    }

    private fun handlePatternDrawn(pattern: List<Int>) {
        if (pattern.size < 4) {
            binding.patternLockView.setPatternError()
            binding.tvSetupSubtitle.text = "Connect at least 4 dots!"
            binding.tvSetupSubtitle.setTextColor(0xFFFF5252.toInt())
            clearPatternWithDelay()
            return
        }

        val first = firstPattern
        if (first == null) {
            firstPattern = pattern
            binding.tvSetupTitle.text = "Confirm Pattern"
            binding.tvSetupSubtitle.text = "Draw pattern again to confirm"
            binding.tvSetupSubtitle.setTextColor(0xFF8894B4.toInt()) // text_secondary
            binding.patternLockView.clearPattern()
        } else {
            if (pattern == first) {
                binding.patternLockView.clearPattern()
                lockSettingsManager.savePattern(pattern)
                Toast.makeText(this, "Lock Pattern set successfully! 🔒", Toast.LENGTH_SHORT).show()
                startMainActivity()
            } else {
                binding.patternLockView.setPatternError()
                binding.tvSetupTitle.text = "Set Pattern Lock"
                binding.tvSetupSubtitle.text = "Patterns do not match. Try again."
                binding.tvSetupSubtitle.setTextColor(0xFFFF5252.toInt())
                firstPattern = null
                clearPatternWithDelay()
            }
        }
    }

    private fun clearPatternWithDelay() {
        lifecycleScope.launch {
            delay(1000)
            binding.patternLockView.clearPattern()
            if (firstPattern == null) {
                binding.tvSetupSubtitle.text = "Draw a pattern to protect your applications"
                binding.tvSetupSubtitle.setTextColor(0xFF8894B4.toInt())
            }
        }
    }

    // ==================== PIN LOCK SETUP ====================
    private fun setupPinKeypad() {
        val keys = listOf(
            R.id.btnKey0 to "0", R.id.btnKey1 to "1", R.id.btnKey2 to "2",
            R.id.btnKey3 to "3", R.id.btnKey4 to "4", R.id.btnKey5 to "5",
            R.id.btnKey6 to "6", R.id.btnKey7 to "7", R.id.btnKey8 to "8",
            R.id.btnKey9 to "9"
        )

        keys.forEach { (id, digit) ->
            binding.includePinInput.root.findViewById<MaterialButton>(id)?.setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin += digit
                    updatePinDots()
                }
            }
        }

        binding.includePinInput.root.findViewById<MaterialButton>(R.id.btnKeyDelete)?.setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.substring(0, enteredPin.length - 1)
                updatePinDots()
            }
        }

        binding.includePinInput.root.findViewById<MaterialButton>(R.id.btnKeyConfirm)?.setOnClickListener {
            if (enteredPin.length == 4) {
                handlePinEntered(enteredPin)
            } else {
                Toast.makeText(this, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePinDots() {
        val dotIds = listOf(
            R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4
        )
        dotIds.forEachIndexed { index, id ->
            val dotView = binding.includePinInput.root.findViewById<View>(id)
            if (index < enteredPin.length) {
                dotView?.setBackgroundResource(R.drawable.bg_pin_dot_filled)
            } else {
                dotView?.setBackgroundResource(R.drawable.bg_pin_dot_empty)
            }
        }
    }

    private fun handlePinEntered(pin: String) {
        val first = firstPin
        if (first == null) {
            firstPin = pin
            enteredPin = ""
            updatePinDots()
            binding.tvSetupTitle.text = "Confirm PIN"
            binding.tvSetupSubtitle.text = "Enter PIN again to confirm"
            binding.tvSetupSubtitle.setTextColor(0xFF8894B4.toInt())
        } else {
            if (pin == first) {
                lockSettingsManager.savePin(pin)
                Toast.makeText(this, "PIN set successfully! 🔒", Toast.LENGTH_SHORT).show()
                startMainActivity()
            } else {
                Toast.makeText(this, "PINs do not match. Try again.", Toast.LENGTH_SHORT).show()
                binding.tvSetupTitle.text = "Set PIN Lock"
                binding.tvSetupSubtitle.text = "PINs do not match. Try again."
                binding.tvSetupSubtitle.setTextColor(0xFFFF5252.toInt())
                firstPin = null
                enteredPin = ""
                updatePinDots()
            }
        }
    }

    // ==================== PASSWORD LOCK SETUP ====================
    private fun setupPasswordForm() {
        val btnConfirm = binding.includePasswordInput.btnPasswordConfirm
        val etPassword = binding.includePasswordInput.etPasswordInput

        btnConfirm.setOnClickListener {
            val input = etPassword.text.toString().trim()
            if (input.length < 4) {
                Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            handlePasswordEntered(input)
        }
    }

    private fun handlePasswordEntered(password: String) {
        val etPassword = binding.includePasswordInput.etPasswordInput
        val first = firstPassword

        if (first == null) {
            firstPassword = password
            etPassword.setText("")
            binding.tvSetupTitle.text = "Confirm Password"
            binding.tvSetupSubtitle.text = "Enter password again to confirm"
            binding.tvSetupSubtitle.setTextColor(0xFF8894B4.toInt())
        } else {
            if (password == first) {
                lockSettingsManager.savePassword(password)
                Toast.makeText(this, "Password set successfully! 🔒", Toast.LENGTH_SHORT).show()
                startMainActivity()
            } else {
                Toast.makeText(this, "Passwords do not match. Try again.", Toast.LENGTH_SHORT).show()
                binding.tvSetupTitle.text = "Set Password"
                binding.tvSetupSubtitle.text = "Passwords do not match. Try again."
                binding.tvSetupSubtitle.setTextColor(0xFFFF5252.toInt())
                firstPassword = null
                etPassword.setText("")
            }
        }
    }

    // ==================== GENERAL RESET STATE ====================
    private fun resetSetupState() {
        firstPattern = null
        firstPin = null
        firstPassword = null
        enteredPin = ""
        
        binding.tvSetupSubtitle.setTextColor(0xFF8894B4.toInt())
        
        when (lockType) {
            "pin" -> {
                binding.tvSetupTitle.text = "Set PIN Lock"
                binding.tvSetupSubtitle.text = "Enter 4 digit PIN to protect apps"
                updatePinDots()
            }
            "password" -> {
                binding.tvSetupTitle.text = "Set Password"
                binding.tvSetupSubtitle.text = "Enter password to protect apps"
                binding.includePasswordInput.etPasswordInput.setText("")
            }
            else -> {
                binding.tvSetupTitle.text = "Set Pattern Lock"
                binding.tvSetupSubtitle.text = "Draw a pattern to protect your applications"
                binding.patternLockView.clearPattern()
            }
        }
    }

    private fun startMainActivity() {
        val intent = if (fromOnboarding && !lockSettingsManager.isOnboardingCompleted()) {
            Intent(this, SecurityQuestionSetupActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
