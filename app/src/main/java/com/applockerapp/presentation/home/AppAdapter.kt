package com.applockerapp.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.applockerapp.R
import com.applockerapp.databinding.ItemAppBinding

class AppAdapter(
    private val onAppClicked: (AppItem) -> Unit
) : ListAdapter<AppItem, AppAdapter.AppViewHolder>(AppDiffCallback()) {

    companion object {
        private val iconCache = android.util.LruCache<String, android.graphics.drawable.Drawable>(150)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding, onAppClicked)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(
        private val binding: ItemAppBinding,
        private val onAppClicked: (AppItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppItem) {
            binding.tvAppName.text = item.appName
            binding.tvAppType.text = if (item.isSystemApp) "System Application" else "Third Party Application"

            // Check cached icon first, load from PM only if missing
            val cachedDrawable = iconCache.get(item.packageName)
            if (cachedDrawable != null) {
                binding.ivAppIcon.setImageDrawable(cachedDrawable)
            } else {
                try {
                    val pm = binding.root.context.packageManager
                    val icon = pm.getApplicationIcon(item.packageName)
                    iconCache.put(item.packageName, icon)
                    binding.ivAppIcon.setImageDrawable(icon)
                } catch (e: Exception) {
                    binding.ivAppIcon.setImageResource(item.iconDrawableId)
                }
            }

            // Set professional lock icon and color based on lock state
            if (item.isLocked) {
                binding.btnLockToggle.setImageResource(R.drawable.ic_lock_closed)
                binding.btnLockToggle.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.primary)
                )
            } else {
                binding.btnLockToggle.setImageResource(R.drawable.ic_lock_open)
                binding.btnLockToggle.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.text_muted)
                )
            }

            // Both row click and lock button click show the dialog
            binding.root.setOnClickListener { onAppClicked(item) }
            binding.btnLockToggle.setOnClickListener { onAppClicked(item) }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
            return oldItem == newItem
        }
    }
}
