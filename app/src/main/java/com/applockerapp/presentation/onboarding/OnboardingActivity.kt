package com.applockerapp.presentation.onboarding

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.viewpager2.widget.ViewPager2
import com.applockerapp.R
import com.applockerapp.databinding.ActivityOnboardingBinding
import com.applockerapp.presentation.lock.LockSetupActivity
import com.applockerapp.utils.LockSettingsManager
import dagger.hilt.android.AndroidEntryPoint

/**
 * OnboardingActivity — World-Class Fresh Native UI:
 * 1. Step 01 • Instant App Lock Protection
 * 2. Step 02 • Intruder Selfie & Camera Trap
 * 3. Step 03 • AES-256 Vault & Custom Themes
 * 4. Step 04 • Smart Profiles & Complete Security Setup
 */
@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var lockSettingsManager: LockSettingsManager
    private var lastPosition = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockSettingsManager = LockSettingsManager(this)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupIndicators()
        updateIndicators(0)

        binding.btnContinueOnboarding.setOnClickListener {
            val cur = binding.viewPagerOnboarding.currentItem
            if (cur < 3) {
                binding.viewPagerOnboarding.setCurrentItem(cur + 1, true)
            } else {
                navigateToLockSetup()
            }
        }

        binding.btnBackOnboarding.setOnClickListener {
            val cur = binding.viewPagerOnboarding.currentItem
            if (cur > 0) {
                binding.viewPagerOnboarding.setCurrentItem(cur - 1, true)
            }
        }

        binding.btnSkipOnboarding.setOnClickListener {
            navigateToLockSetup()
        }
    }

    private fun setupViewPager() {
        binding.viewPagerOnboarding.apply {
            adapter = OnboardingPagerAdapter()
            setPageTransformer(OnboardingPageTransformer())
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updateIndicators(position)
                    animatePageContent(position)
                    lastPosition = position
                }
            })
        }
    }

    private fun setupIndicators() {
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(10, 0, 10, 0) }

        repeat(4) {
            val iv = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.indicator_inactive))
                layoutParams = lp
            }
            binding.layoutIndicatorsOnboarding.addView(iv)
        }
    }

    private fun updateIndicators(position: Int) {
        val count = binding.layoutIndicatorsOnboarding.childCount
        for (i in 0 until count) {
            val iv = binding.layoutIndicatorsOnboarding.getChildAt(i) as ImageView
            if (i == position) {
                iv.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.indicator_active))
                iv.animate().scaleX(1.15f).scaleY(1.15f).setDuration(220).start()
            } else {
                iv.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.indicator_inactive))
                iv.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220).start()
            }
        }

        binding.btnBackOnboarding.visibility = if (position > 0) View.VISIBLE else View.GONE

        when (position) {
            0, 1, 2 -> {
                binding.btnContinueOnboarding.text = "Continue →"
                binding.btnContinueOnboarding.setBackgroundResource(R.drawable.btn_gradient_primary_3d)
            }
            3 -> {
                binding.btnContinueOnboarding.text = "Get Started →"
                binding.btnContinueOnboarding.setBackgroundResource(R.drawable.btn_gradient_primary_3d)
            }
        }
    }

    private fun animatePageContent(position: Int) {
        binding.viewPagerOnboarding.post {
            val recyclerView = binding.viewPagerOnboarding.getChildAt(0) as? RecyclerView
            val viewHolder = recyclerView?.findViewHolderForAdapterPosition(position)
            val itemView = viewHolder?.itemView ?: return@post

            val targetId = when (position) {
                0 -> R.id.cardHeroPage1
                1 -> R.id.cardHeroPage2
                2 -> R.id.cardHeroPage3
                else -> R.id.cardHeroPage4
            }

            val heroCard = itemView.findViewById<View>(targetId)
            heroCard?.let { hero ->
                hero.alpha = 0.7f
                hero.scaleX = 0.88f
                hero.scaleY = 0.88f
                hero.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(380)
                    .setInterpolator(OvershootInterpolator(1.5f))
                    .start()
            }

            // Start animated vector drawable for the hero icon
            val animIconId = when (position) {
                0 -> R.id.ivHeroAnimPage1
                1 -> R.id.ivHeroAnimPage2
                2 -> R.id.ivHeroAnimPage3
                else -> R.id.ivHeroAnimPage4
            }
            val animIv = itemView.findViewById<ImageView>(animIconId)
            animIv?.let { iv ->
                val d = iv.drawable
                when (d) {
                    is AnimatedVectorDrawable -> { d.stop(); d.start() }
                    is AnimatedVectorDrawableCompat -> { d.stop(); d.start() }
                }
            }

            val title = itemView.findViewById<View>(R.id.tvOnboardingTitle)
            title?.let { t ->
                t.translationY = 18f
                t.alpha = 0.5f
                t.animate()
                    .translationY(0f)
                    .alpha(1.0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun navigateToLockSetup() {
        startActivity(Intent(this, LockSetupActivity::class.java).apply {
            putExtra("from_onboarding", true)
        })
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    // ─────────────────────────────────────────────────────────────
    // Adapter
    // ─────────────────────────────────────────────────────────────
    private inner class OnboardingPagerAdapter : RecyclerView.Adapter<OnboardingPagerAdapter.VH>() {
        inner class VH(val view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val layoutRes = when (viewType) {
                0 -> R.layout.layout_onboarding_page1
                1 -> R.layout.layout_onboarding_page2
                2 -> R.layout.layout_onboarding_page3
                else -> R.layout.layout_onboarding_page4
            }
            return VH(LayoutInflater.from(parent.context).inflate(layoutRes, parent, false))
        }

        override fun getItemViewType(position: Int) = position
        override fun getItemCount() = 4

        override fun onBindViewHolder(holder: VH, position: Int) {
            // Auto-start the animated vector drawable for the first visible page
            val animIconId = when (position) {
                0 -> R.id.ivHeroAnimPage1
                1 -> R.id.ivHeroAnimPage2
                2 -> R.id.ivHeroAnimPage3
                else -> R.id.ivHeroAnimPage4
            }
            val animIv = holder.view.findViewById<ImageView>(animIconId)
            animIv?.let { iv ->
                iv.post {
                    val d = iv.drawable
                    when (d) {
                        is AnimatedVectorDrawable -> d.start()
                        is AnimatedVectorDrawableCompat -> d.start()
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Page transformer — depth fade + title parallax
    // ─────────────────────────────────────────────────────────────
    private inner class OnboardingPageTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            val abs = Math.abs(position)
            when {
                position < -1 -> page.alpha = 0f
                position <= 1 -> {
                    page.alpha = Math.max(0.1f, 1f - abs * 1.1f)
                    val scale = Math.max(0.85f, 1f - abs * 0.15f)
                    page.scaleX = scale
                    page.scaleY = scale

                    val title = page.findViewById<View>(R.id.tvOnboardingTitle)
                    val subtitle = page.findViewById<View>(R.id.tvOnboardingSubtitle)
                    title?.translationX = position * (page.width * 0.12f)
                    subtitle?.translationX = position * (page.width * 0.06f)
                }
                else -> page.alpha = 0f
            }
        }
    }
}
