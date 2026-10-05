package com.applockerapp.presentation.profile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.applockerapp.R
import com.applockerapp.databinding.ItemProfileCardBinding

class ProfileAdapter(
    private val onProfileActiveChanged: (AppLockProfile, Boolean) -> Unit,
    private val onEditProfile: (AppLockProfile) -> Unit,
    private val onDeleteProfile: (AppLockProfile) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    private var profiles = listOf<AppLockProfile>()

    fun setProfiles(list: List<AppLockProfile>) {
        profiles = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val binding = ItemProfileCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProfileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]
        holder.bind(profile)
    }

    override fun getItemCount() = profiles.size

    inner class ProfileViewHolder(val binding: ItemProfileCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(profile: AppLockProfile) {
            binding.tvProfileName.text = profile.name
            binding.tvProfileDesc.text = profile.description

            val context = itemView.context

            // Icon background color based on active/inactive
            val primaryColor = ContextCompat.getColor(context, R.color.primary)
            val cardBorderColor = ContextCompat.getColor(context, R.color.card_border)
            val cardElevatedColor = ContextCompat.getColor(context, R.color.bg_card_elevated)
            val textMutedColor = ContextCompat.getColor(context, R.color.text_muted)

            if (profile.isActive) {
                binding.cardProfileIcon.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#2900E676")))
                binding.cardProfileIcon.strokeColor = primaryColor
            } else {
                binding.cardProfileIcon.setCardBackgroundColor(ColorStateList.valueOf(cardElevatedColor))
                binding.cardProfileIcon.strokeColor = cardBorderColor
            }

            // Set Profile Icon
            val iconRes = when (profile.iconType) {
                "guest" -> R.drawable.ic_profile
                "kids" -> R.drawable.ic_profile
                else -> R.drawable.ic_profile
            }
            binding.imgProfileIcon.setImageResource(iconRes)
            
            if (profile.iconType == "kids") {
                binding.imgProfileIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B")) // Amber for kids
            } else if (profile.isActive) {
                binding.imgProfileIcon.imageTintList = ColorStateList.valueOf(primaryColor) // Emerald active
            } else {
                binding.imgProfileIcon.imageTintList = ColorStateList.valueOf(textMutedColor) // Muted secondary
            }

            // Set active state switch (disable listener while setting state to prevent triggers)
            binding.switchProfileActive.setOnCheckedChangeListener(null)
            binding.switchProfileActive.isChecked = profile.isActive
            binding.switchProfileActive.setOnCheckedChangeListener { _, isChecked ->
                onProfileActiveChanged(profile, isChecked)
            }

            // Display horizontal list of selected app package icons
            val pm = context.packageManager
            binding.layoutAppIcons.removeAllViews()

            val density = context.resources.displayMetrics.density
            val size = (30 * density).toInt()
            val margin = (6 * density).toInt()

            profile.packages.take(8).forEach { packageName ->
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val appIcon = pm.getApplicationIcon(appInfo)

                    val imageView = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            marginEnd = margin
                        }
                        setImageDrawable(appIcon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    binding.layoutAppIcons.addView(imageView)
                } catch (e: Exception) {
                    // Ignore package info errors
                }
            }

            // If more than 8 apps, add a text indicator
            if (profile.packages.size > 8) {
                val moreCount = profile.packages.size - 8
                val moreView = android.widget.TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, size).apply {
                        marginStart = margin
                    }
                    text = "+$moreCount"
                    textColor = ContextCompat.getColor(context, R.color.text_secondary)
                    textSize = 12f
                    gravity = android.view.Gravity.CENTER
                }
                binding.layoutAppIcons.addView(moreView)
            }

            // Options menu (popup)
            binding.btnProfileOptions.setOnClickListener { view ->
                val popup = PopupMenu(context, view)
                popup.menu.add("Edit")
                if (profile.id != "guest" && profile.id != "kids") {
                    popup.menu.add("Delete")
                }
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.title) {
                        "Edit" -> onEditProfile(profile)
                        "Delete" -> onDeleteProfile(profile)
                    }
                    true
                }
                popup.show()
            }
        }
    }
}
// Helper property extension to set text color easily
private var android.widget.TextView.textColor: Int
    get() = currentTextColor
    set(v) = setTextColor(v)
