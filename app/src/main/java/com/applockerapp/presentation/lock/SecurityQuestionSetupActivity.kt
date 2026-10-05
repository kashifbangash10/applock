package com.applockerapp.presentation.lock

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.applockerapp.R
import com.applockerapp.databinding.ActivitySecurityQuestionSetupBinding
import com.applockerapp.presentation.MainActivity
import com.applockerapp.utils.LockSettingsManager
import dagger.hilt.android.AndroidEntryPoint

/**
 * SecurityQuestionSetupActivity – shown once during first-time onboarding
 * after the user has set their lock pattern/PIN/password.
 *
 * The user picks a security question and enters an answer that will be used
 * later to reset their lock if they forget it.
 *
 * "Skip" and "Save & Continue" both mark onboarding as completed and navigate
 * to MainActivity.
 */
@AndroidEntryPoint
class SecurityQuestionSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityQuestionSetupBinding
    private lateinit var lockSettingsManager: LockSettingsManager

    private val questions = listOf(
        "What is your date of birth?",
        "What is your pet's name?",
        "What is your favorite school teacher's name?"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityQuestionSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lockSettingsManager = LockSettingsManager(this)

        // TODO: Screenshot protection disabled temporarily — re-enable later
        // if (lockSettingsManager.isScreenshotProtectionEnabled()) {
        //     window.setFlags(
        //         android.view.WindowManager.LayoutParams.FLAG_SECURE,
        //         android.view.WindowManager.LayoutParams.FLAG_SECURE
        //     )
        // }

        setupSpinner()
        setupListeners()
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, R.layout.spinner_item, questions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSecSetup.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBackSecSetup.setOnClickListener {
            // Back treated same as skip — mark onboarding done and go to main
            completeOnboarding()
        }

        binding.btnSkipSecSetup.setOnClickListener {
            completeOnboarding()
        }

        binding.btnSaveSecSetup.setOnClickListener {
            val answer = binding.etAnswerSecSetup.text.toString().trim()
            if (answer.isEmpty()) {
                Toast.makeText(this, "Please enter an answer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedIndex = binding.spinnerSecSetup.selectedItemPosition
            lockSettingsManager.saveSecurityQuestion(selectedIndex, answer)
            Toast.makeText(this, "Security question saved! ✅", Toast.LENGTH_SHORT).show()
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        lockSettingsManager.setOnboardingCompleted(true)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
