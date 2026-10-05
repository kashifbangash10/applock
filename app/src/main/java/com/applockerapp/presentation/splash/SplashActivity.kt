package com.applockerapp.presentation.splash

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.applockerapp.R
import com.applockerapp.databinding.ActivitySplashBinding
import com.applockerapp.presentation.MainActivity
import com.applockerapp.presentation.lock.LanguageActivity
import com.applockerapp.presentation.lock.LockSetupActivity
import com.applockerapp.utils.LockSettingsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SplashActivity - Ultra-fast native entry point with 3D glass lock logo & animated gradient progress bar.
 */
@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var lockSettingsManager: LockSettingsManager

    private var glowPulseAnimator: ObjectAnimator? = null
    private var shieldPulseAnimator: ObjectAnimator? = null
    private var progressLineAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            lockSettingsManager = LockSettingsManager(this)
        } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "Failed to initialize LockSettingsManager", e)
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startLogoAnimations()
        startProgressLineAnimation()

        lifecycleScope.launch {
            delay(3200L)

            if (isFinishing || isDestroyed) return@launch

            try {
                val isOnboardingDone = ::lockSettingsManager.isInitialized && lockSettingsManager.isOnboardingCompleted()
                if (isOnboardingDone) {
                    val isAnyLock = lockSettingsManager.isAnyLockSet()
                    val next = if (isAnyLock) {
                        Intent(this@SplashActivity, MainActivity::class.java)
                    } else {
                        Intent(this@SplashActivity, LockSetupActivity::class.java)
                    }
                    startActivity(next)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                } else {
                    // Smooth transition from Progress Bar Line to Get Started Button
                    binding.progressSplashLine.animate()
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction {
                            binding.progressSplashLine.visibility = View.GONE
                        }
                        .start()

                    binding.btnGetStartedSplash.apply {
                        visibility = View.VISIBLE
                        alpha = 0f
                        translationY = 40f
                        animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(450)
                            .setInterpolator(OvershootInterpolator(1.2f))
                            .start()
                    }
                    binding.btnGetStartedSplash.setOnClickListener {
                        showTermsDialog()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SplashActivity", "Error during splash navigation", e)
                try {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                } catch (ex: Exception) {
                    android.util.Log.e("SplashActivity", "Fatal splash fallback error", ex)
                }
            }
        }
    }

    private fun startLogoAnimations() {
        // Logo Entrance Scale with Overshoot
        binding.cardSplashLogoContainer.apply {
            scaleX = 0.35f
            scaleY = 0.35f
            alpha = 0f
            animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(750)
                .setInterpolator(OvershootInterpolator(2.2f))
                .start()
        }

        // Ambient Glow Pulse
        val glowScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.15f, 1.0f)
        val glowScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.15f, 1.0f)
        val glowAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 0.95f, 0.45f)
        glowPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.viewSplashGlowRing, glowScaleX, glowScaleY, glowAlpha
        ).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // 3D Glass Lock Icon Pulse
        val shieldScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.06f, 1.0f)
        val shieldScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.06f, 1.0f)
        shieldPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.imgSplashLogo, shieldScaleX, shieldScaleY
        ).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startProgressLineAnimation() {
        progressLineAnimator = ObjectAnimator.ofInt(binding.progressSplashLine, "progress", 0, 100).apply {
            duration = 3000L
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun showTermsDialog() {
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_terms, null)

            val dialog = AlertDialog.Builder(this, R.style.TermsDialogTheme)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            dialogView.findViewById<Button>(R.id.btnAcceptTerms)?.setOnClickListener {
                dialog.dismiss()
                startActivity(Intent(this, LanguageActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }

            dialogView.findViewById<Button>(R.id.btnDeclineTerms)?.setOnClickListener {
                dialog.dismiss()
                finishAffinity()
            }

            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "Error displaying terms dialog", e)
            startActivity(Intent(this, LanguageActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    override fun onDestroy() {
        glowPulseAnimator?.cancel()
        shieldPulseAnimator?.cancel()
        progressLineAnimator?.cancel()
        super.onDestroy()
    }
}
