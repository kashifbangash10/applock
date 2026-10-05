package com.applockerapp.presentation.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.applockerapp.R
import com.applockerapp.databinding.ItemAppBinding
import com.applockerapp.presentation.home.AppItem

class ProfileAppAdapter(
    private val onAppToggled: (AppItem) -> Unit
) : RecyclerView.Adapter<ProfileAppAdapter.AppViewHolder>() {

    private var allApps = listOf<AppItem>()
    private var filteredApps = listOf<AppItem>()

    fun setApps(list: List<AppItem>) {
        allApps = list
        filter("")
    }

    fun getSelectedPackages(): Set<String> {
        return allApps.filter { it.isLocked }.map { it.packageName }.toSet()
    }

    fun filter(query: String) {
        val trimmed = query.lowercase().trim()
        filteredApps = if (trimmed.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.appName.lowercase().contains(trimmed) }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = filteredApps[position]
        holder.bind(item)
    }

    override fun getItemCount() = filteredApps.size

    inner class AppViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppItem) {
            binding.tvAppName.text = item.appName
            binding.tvAppType.text = if (item.isSystemApp) "System Application" else "Third Party Application"

            // Load real app icon
            try {
                val pm = binding.root.context.packageManager
                val icon = pm.getApplicationIcon(item.packageName)
                binding.ivAppIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                binding.ivAppIcon.setImageResource(item.iconDrawableId)
            }

            // Set lock icon to represent checkbox selection
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

            // Clicking row toggles state
            binding.root.setOnClickListener {
                item.isLocked = !item.isLocked
                notifyItemChanged(adapterPosition)
                onAppToggled(item)
            }
            binding.btnLockToggle.setOnClickListener {
                item.isLocked = !item.isLocked
                notifyItemChanged(adapterPosition)
                onAppToggled(item)
            }
        }
    }
}
