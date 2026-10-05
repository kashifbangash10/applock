package com.applockerapp.presentation.vault

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.applockerapp.databinding.ItemVaultAlbumBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey

class VaultAlbumAdapter(
    private val onClick: (VaultAlbum) -> Unit
) : ListAdapter<VaultAlbum, VaultAlbumAdapter.AlbumViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<VaultAlbum>() {
            override fun areItemsTheSame(a: VaultAlbum, b: VaultAlbum) = a.bucketId == b.bucketId && a.mediaType == b.mediaType
            override fun areContentsTheSame(a: VaultAlbum, b: VaultAlbum) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemVaultAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: AlbumViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.binding.imgAlbumThumbnail.context).clear(holder.binding.imgAlbumThumbnail)
    }

    inner class AlbumViewHolder(val binding: ItemVaultAlbumBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(album: VaultAlbum) {
            binding.tvAlbumName.text = album.name
            binding.tvAlbumCount.text = "${album.count} items"

            if (album.thumbnailUri != null) {
                Glide.with(binding.imgAlbumThumbnail.context)
                    .load(album.thumbnailUri)
                    .apply(RequestOptions().centerCrop().placeholder(android.R.color.darker_gray))
                    .signature(ObjectKey(album.thumbnailUri.toString()))
                    .transition(DrawableTransitionOptions.withCrossFade(200))
                    .into(binding.imgAlbumThumbnail)
            } else {
                binding.imgAlbumThumbnail.setImageURI(null)
            }

            binding.root.setOnClickListener { onClick(album) }
        }
    }
}
