package com.applockerapp.presentation.themes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.applockerapp.R
import com.google.android.material.card.MaterialCardView

class ThemeAdapter(
    private var items: List<ThemeItem>,
    private var appliedThemeId: Int,
    private val onThemeClick: (ThemeItem) -> Unit
) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    inner class ThemeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardThemeItem)
        val img: ImageView = view.findViewById(R.id.imgThemeCardBg)
        val name: TextView = view.findViewById(R.id.tvThemeCardName)
        val appliedBadge: TextView = view.findViewById(R.id.tvAppliedBadge)
        val lockOverlay: View = view.findViewById(R.id.viewLockedOverlay)
        val lockIcon: ImageView = view.findViewById(R.id.imgLockIcon)
        val proBadge: TextView = view.findViewById(R.id.tvThemePro)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_theme_card, parent, false)
        return ThemeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val theme = items[position]
        val ctx = holder.itemView.context

        // Set name
        holder.name.text = theme.name

        // Set image
        holder.img.setImageResource(theme.drawableRes)
        holder.img.scaleType = if (theme.isImage) {
            ImageView.ScaleType.CENTER_CROP
        } else {
            ImageView.ScaleType.FIT_XY
        }

        // Applied badge
        val isApplied = theme.id == appliedThemeId && !theme.isPro
        holder.appliedBadge.visibility = if (isApplied) View.VISIBLE else View.GONE

        // Card stroke highlight if applied
        val strokeColor = if (isApplied) {
            ContextCompat.getColor(ctx, R.color.primary)
        } else {
            ContextCompat.getColor(ctx, R.color.translucent_white)
        }
        holder.card.strokeColor = strokeColor
        holder.card.strokeWidth = if (isApplied) {
            (3 * ctx.resources.displayMetrics.density).toInt()
        } else {
            (1 * ctx.resources.displayMetrics.density).toInt()
        }

        // Pro lock overlay
        if (theme.isPro) {
            holder.lockOverlay.visibility = View.VISIBLE
            holder.lockIcon.visibility = View.VISIBLE
            holder.proBadge.visibility = View.VISIBLE
        } else {
            holder.lockOverlay.visibility = View.GONE
            holder.lockIcon.visibility = View.GONE
            holder.proBadge.visibility = View.GONE
        }

        // Click
        holder.card.setOnClickListener {
            onThemeClick(theme)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ThemeItem>, newAppliedId: Int) {
        items = newItems
        appliedThemeId = newAppliedId
        notifyDataSetChanged()
    }
}
