package com.applockerapp.presentation.vault

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.applockerapp.databinding.FragmentVaultAlbumBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class VaultAlbumFragment : Fragment() {

    private var _binding: FragmentVaultAlbumBinding? = null
    private val binding get() = _binding!!

    private lateinit var mediaAdapter: VaultMediaAdapter
    private var albumName = ""
    private var isVideo = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVaultAlbumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        albumName = arguments?.getString("albumName") ?: "Album"
        isVideo   = arguments?.getBoolean("isVideo") ?: false

        binding.tvAlbumTitle.text = albumName
        binding.btnBackAlbum.setOnClickListener {
            if (mediaAdapter.isSelectionMode) {
                mediaAdapter.stopSelection()
            } else {
                findNavController().popBackStack()
            }
        }

        setupRecyclerView()
        loadMedia()

        // Setup Bottom Unhide Action
        binding.btnBottomAction.setOnClickListener {
            unhideSelectedItems()
        }
    }

    private fun setupRecyclerView() {
        mediaAdapter = VaultMediaAdapter(
            onClick = { item -> openMedia(item) },
            onSelectionChanged = { selectedCount ->
                if (selectedCount > 0) {
                    binding.btnBottomAction.visibility = View.VISIBLE
                    binding.btnBottomAction.text = "Unhide ($selectedCount)"
                } else {
                    binding.btnBottomAction.visibility = View.GONE
                }
            }
        )
        binding.rvAlbumMedia.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = mediaAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(30)
            recycledViewPool.setMaxRecycledViews(0, 30)
        }
    }

    private fun openMedia(item: VaultMediaItem) {
        val ctx = context ?: return
        try {
            val file = File(item.uri.path ?: return)
            if (!file.exists()) {
                Toast.makeText(ctx, "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
            val mimeType = getMimeType(file.name, item.isVideo)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(ctx, "Cannot open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(fileName: String, isVideo: Boolean): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!mime.isNullOrEmpty()) return mime
        return if (isVideo) "video/*" else "image/*"
    }

    private fun loadMedia() {
        binding.progressAlbum.visibility = View.VISIBLE
        binding.rvAlbumMedia.visibility = View.GONE
        binding.layoutAlbumEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val sub = if (isVideo) "videos" else "images"
                val dir = File(requireContext().filesDir, "vault/$sub/$albumName")
                if (!dir.exists()) dir.mkdirs()

                dir.listFiles()?.sortedByDescending { it.lastModified() }?.map { file ->
                    VaultMediaItem(
                        id = file.hashCode().toLong(),
                        uri = Uri.fromFile(file),
                        name = file.name,
                        isVideo = isVideo
                    )
                } ?: emptyList()
            }
            binding.progressAlbum.visibility = View.GONE
            binding.tvMediaCount.text = "${items.size} items"

            if (items.isEmpty()) {
                binding.layoutAlbumEmpty.visibility = View.VISIBLE
                binding.btnBottomAction.visibility = View.GONE
            } else {
                binding.rvAlbumMedia.visibility = View.VISIBLE
                mediaAdapter.submitList(items)
            }
        }
    }

    private fun unhideSelectedItems() {
        val selected = ArrayList(mediaAdapter.selectedItems)
        if (selected.isEmpty()) return

        binding.progressAlbum.visibility = View.VISIBLE
        binding.btnBottomAction.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val successCount = withContext(Dispatchers.IO) {
                var restored = 0
                for (item in selected) {
                    if (restoreToGallery(item)) {
                        restored++
                    }
                }
                restored
            }

            binding.progressAlbum.visibility = View.GONE
            mediaAdapter.stopSelection()
            Toast.makeText(context, "✓ $successCount items restored to gallery", Toast.LENGTH_SHORT).show()
            
            // Refresh list or go back if empty
            val sub = if (isVideo) "videos" else "images"
            val dir = File(requireContext().filesDir, "vault/$sub/$albumName")
            val remainingFiles = dir.listFiles()?.filter { it.isFile }?.size ?: 0
            if (remainingFiles == 0) {
                findNavController().popBackStack()
            } else {
                loadMedia()
            }
        }
    }

    private fun restoreToGallery(item: VaultMediaItem): Boolean {
        val ctx = context ?: return false
        return try {
            val contentResolver = ctx.contentResolver
            val file = File(item.uri.path ?: return false)
            if (!file.exists()) return false

            val mimeType = getMimeType(file.name, item.isVideo)

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val subFolder = if (item.isVideo) "Movies/VaultRestored" else "Pictures/VaultRestored"
                    put(MediaStore.MediaColumns.RELATIVE_PATH, subFolder)
                }
            }

            val collection = if (item.isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val restoredUri = contentResolver.insert(collection, contentValues)
            if (restoredUri != null) {
                var copied = false
                contentResolver.openOutputStream(restoredUri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                        copied = true
                    }
                }
                if (copied) {
                    file.delete()
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, restoredUri)
                        ctx.sendBroadcast(scanIntent)
                    }
                    return true
                }
            }

            // Fallback for older Android 7 - 9 or if MediaStore insert returns null
            val destDir = if (item.isVideo) {
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
            } else {
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            }
            val vaultRestoredDir = File(destDir, "VaultRestored")
            if (!vaultRestoredDir.exists()) vaultRestoredDir.mkdirs()

            val targetFile = File(vaultRestoredDir, file.name)
            file.inputStream().use { input ->
                targetFile.outputStream().use { out ->
                    input.copyTo(out)
                }
            }
            file.delete()

            val uri = Uri.fromFile(targetFile)
            ctx.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
