package com.applockerapp.presentation.vault

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.applockerapp.databinding.ItemVaultMediaBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey

class VaultMediaAdapter(
    private val onClick: (VaultMediaItem) -> Unit,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : ListAdapter<VaultMediaItem, VaultMediaAdapter.MediaViewHolder>(DIFF) {

    var isSelectionMode = false
        private set

    val selectedItems = mutableSetOf<VaultMediaItem>()

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<VaultMediaItem>() {
            override fun areItemsTheSame(a: VaultMediaItem, b: VaultMediaItem) = a.id == b.id
            override fun areContentsTheSame(a: VaultMediaItem, b: VaultMediaItem) = a == b
        }
    }

    fun startSelection(item: VaultMediaItem) {
        isSelectionMode = true
        selectedItems.clear()
        selectedItems.add(item)
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    fun stopSelection() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun toggleSelection(item: VaultMediaItem) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemVaultMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        // Cancel any pending Glide request when ViewHolder is recycled
        Glide.with(holder.binding.imgMediaThumb.context).clear(holder.binding.imgMediaThumb)
    }

    inner class MediaViewHolder(val binding: ItemVaultMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VaultMediaItem) {
            // Show/hide video play icon
            binding.imgPlayOverlay.visibility = if (item.isVideo) View.VISIBLE else View.GONE

            // Use Glide for both images and video thumbnails
            val glideRequest = if (item.isVideo) {
                // For videos, Glide extracts the frame thumbnail automatically
                Glide.with(binding.imgMediaThumb.context)
                    .load(item.uri)
                    .apply(
                        RequestOptions()
                            .frame(0L) // Frame 0 microseconds works for all video durations
                            .centerCrop()
                            .placeholder(android.R.color.darker_gray)
                            .error(android.R.color.darker_gray)
                    )
                    .signature(ObjectKey(item.uri.toString()))
                    .transition(DrawableTransitionOptions.withCrossFade(150))
            } else {
                Glide.with(binding.imgMediaThumb.context)
                    .load(item.uri)
                    .apply(
                        RequestOptions()
                            .centerCrop()
                            .placeholder(android.R.color.darker_gray)
                    )
                    .signature(ObjectKey(item.uri.toString()))
                    .transition(DrawableTransitionOptions.withCrossFade(150))
            }
            glideRequest.into(binding.imgMediaThumb)

            // Selection state UI
            if (isSelectionMode) {
                binding.viewSelectionDim.visibility = View.VISIBLE
                val isSelected = selectedItems.contains(item)
                if (isSelected) {
                    binding.imgSelectionChecked.visibility = View.VISIBLE
                    binding.imgSelectionOutline.visibility = View.GONE
                } else {
                    binding.imgSelectionChecked.visibility = View.GONE
                    binding.imgSelectionOutline.visibility = View.VISIBLE
                }
            } else {
                binding.viewSelectionDim.visibility = View.GONE
                binding.imgSelectionChecked.visibility = View.GONE
                binding.imgSelectionOutline.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item)
                } else {
                    onClick(item)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    startSelection(item)
                }
                true
            }
        }
    }
}
