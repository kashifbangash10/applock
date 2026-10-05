package com.applockerapp.presentation.lock

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.applockerapp.databinding.ActivitySecurityQuestionResetBinding
import com.applockerapp.presentation.service.AppLockService
import com.applockerapp.utils.LockSettingsManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SecurityQuestionResetActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityQuestionResetBinding
    private lateinit var lockSettingsManager: LockSettingsManager
    private var targetPackage: String? = null

    private val questions = listOf(
        "What is your date of birth?",
        "What is your pet's name?",
        "What is your favorite school teacher's name?"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityQuestionResetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lockSettingsManager = LockSettingsManager(this)
        // TODO: Screenshot protection disabled temporarily — re-enable later
        // if (lockSettingsManager.isScreenshotProtectionEnabled()) {
        //     window.setFlags(
        //         android.view.WindowManager.LayoutParams.FLAG_SECURE,
        //         android.view.WindowManager.LayoutParams.FLAG_SECURE
        //     )
        // }
        targetPackage = intent.getStringExtra("target_package")

        setupViews()
    }

    private fun setupViews() {
        binding.btnBackReset.setOnClickListener {
            finish()
        }

        // Load configured question index
        val qIndex = lockSettingsManager.getSecurityQuestionIndex()
        if (qIndex >= 0 && qIndex < questions.size) {
            binding.tvSecurityQuestion.text = questions[qIndex]
        } else {
            binding.tvSecurityQuestion.text = "What is your date of birth?"
        }

        binding.btnVerifyReset.setOnClickListener {
            val answerInput = binding.etAnswerInput.text.toString().trim().lowercase()

            if (answerInput.isEmpty()) {
                Toast.makeText(this, "Please enter the answer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (lockSettingsManager.verifySecurityAnswer(answerInput)) {
                // Correct answer! Unlock target package
                targetPackage?.let { pkg ->
                    AppLockService.temporarilyUnlockedApps.add(pkg)
                }

                Toast.makeText(this, "Verification Successful! Please reset your lock.", Toast.LENGTH_LONG).show()

                // Redirect to Setup screen to set new lock
                val setupIntent = Intent(this, LockSetupActivity::class.java).apply {
                    putExtra("force_setup", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(setupIntent)
                finish()
            } else {
                Toast.makeText(this, "Incorrect answer! Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
