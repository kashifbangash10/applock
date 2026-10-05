package com.applockerapp.presentation.vault

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.applockerapp.R
import com.applockerapp.databinding.FragmentDeviceAlbumsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class DeviceAlbumsFragment : Fragment() {

    private var _binding: FragmentDeviceAlbumsBinding? = null
    private val binding get() = _binding!!

    private lateinit var albumAdapter: VaultAlbumAdapter
    private var currentTab = 0 // 0=Images, 1=Videos

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start on tab passed from VaultFragment (0=images, 1=videos)
        currentTab = arguments?.getInt("startTab", 0) ?: 0

        setupAdapter()
        setupTabs()
        binding.btnBackDeviceAlbums.setOnClickListener { findNavController().popBackStack() }
        loadAlbums()
    }

    private fun setupAdapter() {
        albumAdapter = VaultAlbumAdapter { album ->
            val args = Bundle().apply {
                putString("albumName", album.name)
                putLong("bucketId", album.bucketId)
                putBoolean("isVideo", album.mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                putBoolean("hideMode", true) // Coming from device browser = hide mode
            }
            findNavController().navigate(R.id.action_deviceAlbumsFragment_to_deviceAlbumDetailFragment, args)
        }
        binding.rvDeviceAlbums.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = albumAdapter
        }
    }

    private fun setupTabs() {
        updateTabUI()
        binding.tabDeviceImages.setOnClickListener {
            currentTab = 0; updateTabUI(); loadAlbums()
        }
        binding.tabDeviceVideos.setOnClickListener {
            currentTab = 1; updateTabUI(); loadAlbums()
        }
    }

    private fun updateTabUI() {
        val isImages = currentTab == 0
        val primary = ContextCompat.getColor(requireContext(), R.color.primary)
        val secondary = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.tabDeviceImages.setTextColor(if (isImages) primary else secondary)
        binding.tabDeviceImages.setTypeface(null, if (isImages) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.tabDeviceVideos.setTextColor(if (!isImages) primary else secondary)
        binding.tabDeviceVideos.setTypeface(null, if (!isImages) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        val parent = binding.root as androidx.constraintlayout.widget.ConstraintLayout
        val cs = androidx.constraintlayout.widget.ConstraintSet()
        cs.clone(parent)
        val anchorId = if (isImages) R.id.tabDeviceImages else R.id.tabDeviceVideos
        cs.connect(R.id.deviceTabIndicator, androidx.constraintlayout.widget.ConstraintSet.START, anchorId, androidx.constraintlayout.widget.ConstraintSet.START)
        cs.connect(R.id.deviceTabIndicator, androidx.constraintlayout.widget.ConstraintSet.END, anchorId, androidx.constraintlayout.widget.ConstraintSet.END)
        cs.applyTo(parent)
    }

    private fun loadAlbums() {
        binding.progressDeviceAlbums.visibility = View.VISIBLE
        binding.rvDeviceAlbums.visibility = View.GONE
        binding.tvDeviceAlbumsEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val albums = withContext(Dispatchers.IO) {
                if (currentTab == 0) loadImageAlbums() else loadVideoAlbums()
            }
            binding.progressDeviceAlbums.visibility = View.GONE
            if (albums.isEmpty()) {
                binding.tvDeviceAlbumsEmpty.visibility = View.VISIBLE
            } else {
                binding.rvDeviceAlbums.visibility = View.VISIBLE
                albumAdapter.submitList(albums)
            }
        }
    }

    private fun loadImageAlbums(): List<VaultAlbum> {
        val ctx = context ?: return emptyList()
        val albums = mutableMapOf<Long, Triple<String, Int, Uri?>>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        try {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val bucketId = if (bucketIdCol >= 0 && !cursor.isNull(bucketIdCol)) cursor.getLong(bucketIdCol) else 1L
                    val bucketName = if (bucketNameCol >= 0 && !cursor.isNull(bucketNameCol)) cursor.getString(bucketNameCol) else null
                    val name = if (!bucketName.isNullOrEmpty()) bucketName else "Photos"

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val existing = albums[bucketId]
                    if (existing == null) albums[bucketId] = Triple(name, 1, uri)
                    else albums[bucketId] = Triple(existing.first, existing.second + 1, existing.third)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return albums.map { (bucketId, t) ->
            VaultAlbum(t.first, t.second, t.third, bucketId, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
        }.sortedByDescending { it.count }
    }

    private fun loadVideoAlbums(): List<VaultAlbum> {
        val ctx = context ?: return emptyList()
        val albums = mutableMapOf<Long, Triple<String, Int, Uri?>>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        try {
            ctx.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val bucketId = if (bucketIdCol >= 0 && !cursor.isNull(bucketIdCol)) cursor.getLong(bucketIdCol) else 1L
                    val bucketName = if (bucketNameCol >= 0 && !cursor.isNull(bucketNameCol)) cursor.getString(bucketNameCol) else null
                    val name = if (!bucketName.isNullOrEmpty()) bucketName else "Videos"

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val existing = albums[bucketId]
                    if (existing == null) albums[bucketId] = Triple(name, 1, uri)
                    else albums[bucketId] = Triple(existing.first, existing.second + 1, existing.third)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return albums.map { (bucketId, t) ->
            VaultAlbum(t.first, t.second, t.third, bucketId, MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        }.sortedByDescending { it.count }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
