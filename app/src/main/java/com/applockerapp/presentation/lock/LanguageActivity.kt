package com.applockerapp.presentation.lock

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.applockerapp.R
import com.applockerapp.databinding.ActivityLanguageBinding
import com.applockerapp.presentation.onboarding.OnboardingActivity
import com.applockerapp.utils.LockSettingsManager
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LanguageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLanguageBinding
    private lateinit var lockSettingsManager: LockSettingsManager
    private var selectedIndex = 0  // default: English

    data class LanguageItem(val flag: String, val name: String)

    private val languages = listOf(
        LanguageItem("🇬🇧", "English"),
        LanguageItem("🇪🇸", "Spanish"),
        LanguageItem("🇮🇳", "Hindi"),
        LanguageItem("🇫🇷", "French"),
        LanguageItem("🇳🇱", "Dutch"),
        LanguageItem("🇨🇳", "Chinese"),
        LanguageItem("🇮🇹", "Italian"),
        LanguageItem("🇹🇷", "Turkish"),
        LanguageItem("🇩🇪", "German"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockSettingsManager = LockSettingsManager(this)
        selectedIndex = lockSettingsManager.getLanguageIndex()

        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnBackLanguage.setOnClickListener { finish() }

        val fromSettings = intent.getBooleanExtra("from_settings", false)

        binding.btnDoneLanguage.setOnClickListener {
            lockSettingsManager.setLanguageIndex(selectedIndex)
            if (fromSettings) {
                finish()
            } else {
                navigateToOnboarding()
            }
        }
    }

    private fun setupRecyclerView() {
        val adapter = LanguageAdapter(languages, selectedIndex) { index ->
            selectedIndex = index
        }
        binding.rvLanguages.apply {
            layoutManager = LinearLayoutManager(this@LanguageActivity)
            this.adapter = adapter
        }
    }

    private fun navigateToOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    // ── Inner Adapter ──────────────────────────────────────────────────────
    inner class LanguageAdapter(
        private val items: List<LanguageItem>,
        private var selectedPos: Int,
        private val onSelected: (Int) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.VH>() {

        private var lastAnimatedPosition = -1

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.cardLanguageItem)
            val flag: TextView  = view.findViewById(R.id.tvLanguageFlag)
            val name: TextView  = view.findViewById(R.id.tvLanguageName)
            val check: ImageView = view.findViewById(R.id.ivLanguageCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_language, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.flag.text = item.flag
            holder.name.text = item.name

            val isSelected = position == selectedPos
            val ctx = holder.itemView.context

            // Dynamic Checkbox Icon State
            if (isSelected) {
                holder.check.setImageResource(R.drawable.ic_check_circle)
                holder.check.setColorFilter(ContextCompat.getColor(ctx, R.color.primary))
            } else {
                holder.check.setImageResource(R.drawable.ic_circle_outline)
                holder.check.setColorFilter(ContextCompat.getColor(ctx, R.color.text_muted))
            }

            // Dynamic Card State colors and outline
            if (isSelected) {
                holder.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.translucent_primary))
                holder.card.strokeColor = ContextCompat.getColor(ctx, R.color.primary)
                holder.card.strokeWidth = (ctx.resources.displayMetrics.density * 2).toInt()
                holder.name.setTextColor(ContextCompat.getColor(ctx, R.color.white))
            } else {
                holder.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_surface_card))
                holder.card.strokeColor = ContextCompat.getColor(ctx, R.color.translucent_white)
                holder.card.strokeWidth = (ctx.resources.displayMetrics.density * 1).toInt()
                holder.name.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }

            holder.itemView.setOnClickListener {
                val old = selectedPos
                selectedPos = holder.adapterPosition
                onSelected(selectedPos)
                notifyItemChanged(old)
                notifyItemChanged(selectedPos)
            }

            // Staggered slide-in animation
            setAnimation(holder.itemView, position)
        }

        private fun setAnimation(viewToAnimate: View, position: Int) {
            if (position > lastAnimatedPosition) {
                viewToAnimate.translationY = 120f
                viewToAnimate.alpha = 0f
                viewToAnimate.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setInterpolator(DecelerateInterpolator())
                    .setDuration(400)
                    .setStartDelay(position * 45L)
                    .start()
                lastAnimatedPosition = position
            }
        }
    }
}
