package com.applockerapp.presentation.vault

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.applockerapp.R
import com.applockerapp.databinding.FragmentVaultAlbumBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class DeviceAlbumDetailFragment : Fragment() {

    private var _binding: FragmentVaultAlbumBinding? = null
    private val binding get() = _binding!!
    private lateinit var mediaAdapter: VaultMediaAdapter

    private var albumName = ""
    private var isVideo = false
    private var bucketId = -1L

    // Launcher for handling MediaStore delete permission requests
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "✓ Selected files hidden successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Vault files saved, but gallery deletion was cancelled", Toast.LENGTH_SHORT).show()
        }
        // Navigate back to Vault main screen
        findNavController().popBackStack(R.id.vaultFragment, false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVaultAlbumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        albumName = arguments?.getString("albumName") ?: "Album"
        bucketId  = arguments?.getLong("bucketId") ?: -1L
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

        // Subtitle hint
        binding.tvMediaCount.text = "Long press any item to hide in vault"

        // Setup bottom Hide Action
        binding.btnBottomAction.setOnClickListener {
            hideSelectedItems()
        }
    }

    private fun setupRecyclerView() {
        mediaAdapter = VaultMediaAdapter(
            onClick = { /* single tap = do nothing when selection mode is off */ },
            onSelectionChanged = { selectedCount ->
                if (selectedCount > 0) {
                    binding.btnBottomAction.visibility = View.VISIBLE
                    binding.btnBottomAction.text = "Hide ($selectedCount)"
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

    private fun loadMedia() {
        binding.progressAlbum.visibility = View.VISIBLE
        binding.rvAlbumMedia.visibility = View.GONE
        binding.layoutAlbumEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                if (isVideo) loadVideos(bucketId) else loadImages(bucketId)
            }
            binding.progressAlbum.visibility = View.GONE
            if (items.isEmpty()) {
                binding.layoutAlbumEmpty.visibility = View.VISIBLE
                binding.tvMediaCount.text = "No media found"
                binding.btnBottomAction.visibility = View.GONE
            } else {
                binding.rvAlbumMedia.visibility = View.VISIBLE
                binding.tvMediaCount.text = "Long press any item to hide in vault"
                mediaAdapter.submitList(items)
            }
        }
    }

    private fun loadImages(bucketId: Long): List<VaultMediaItem> {
        val ctx = context ?: return emptyList()
        val items = mutableListOf<VaultMediaItem>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = if (bucketId > 0) "${MediaStore.Images.Media.BUCKET_ID} = ?" else null
        val selectionArgs = if (bucketId > 0) arrayOf(bucketId.toString()) else null

        try {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val name = if (nameCol >= 0 && !cursor.isNull(nameCol)) cursor.getString(nameCol) else null
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    items.add(VaultMediaItem(id, uri, name ?: "Image_$id.jpg", false))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    private fun loadVideos(bucketId: Long): List<VaultMediaItem> {
        val ctx = context ?: return emptyList()
        val items = mutableListOf<VaultMediaItem>()
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        val selection = if (bucketId > 0) "${MediaStore.Video.Media.BUCKET_ID} = ?" else null
        val selectionArgs = if (bucketId > 0) arrayOf(bucketId.toString()) else null

        try {
            ctx.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val name = if (nameCol >= 0 && !cursor.isNull(nameCol)) cursor.getString(nameCol) else null
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    items.add(VaultMediaItem(id, uri, name ?: "Video_$id.mp4", true))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    private fun hideSelectedItems() {
        val selected = ArrayList(mediaAdapter.selectedItems)
        if (selected.isEmpty()) return

        binding.progressAlbum.visibility = View.VISIBLE
        binding.btnBottomAction.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Copy files to vault folder (grouped by albumName)
            val copiedUris = withContext(Dispatchers.IO) {
                val sub = if (isVideo) "videos" else "images"
                val dir = File(requireContext().filesDir, "vault/$sub/$albumName")
                if (!dir.exists()) dir.mkdirs()

                val successfulUris = mutableListOf<Uri>()
                for (item in selected) {
                    try {
                        val originalName = item.name ?: if (isVideo) "video_${item.id}.mp4" else "image_${item.id}.jpg"
                        val ext = if (originalName.contains(".")) {
                            "." + originalName.substringAfterLast(".")
                        } else {
                            if (isVideo) ".mp4" else ".jpg"
                        }
                        val baseName = if (originalName.contains(".")) originalName.substringBeforeLast(".") else originalName
                        val destFile = File(dir, "${baseName}_${System.currentTimeMillis()}$ext")

                        var copied = false
                        requireContext().contentResolver.openInputStream(item.uri)?.use { input ->
                            destFile.outputStream().use { out ->
                                input.copyTo(out)
                                copied = true
                            }
                        }
                        if (copied && destFile.length() > 0) {
                            successfulUris.add(item.uri)
                        } else {
                            destFile.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                successfulUris
            }

            binding.progressAlbum.visibility = View.GONE
            mediaAdapter.stopSelection()

            if (copiedUris.isEmpty()) {
                Toast.makeText(context, "Failed to copy files to vault", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 2. Delete original files from device public MediaStore
            deleteOriginalFiles(copiedUris)
        }
    }

    private fun deleteOriginalFiles(uris: List<Uri>) {
        val ctx = context ?: return
        val contentResolver = ctx.contentResolver

        val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else false

        if (hasAllFilesAccess) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                var deletedCount = 0
                for (uri in uris) {
                    try {
                        val path = getPathFromUri(uri)
                        if (path != null) {
                            val f = File(path)
                            if (f.exists()) f.delete()
                        }
                        val rows = contentResolver.delete(uri, null, null)
                        if (rows > 0) deletedCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "✓ $deletedCount files hidden in vault!", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack(R.id.vaultFragment, false)
                }
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                deleteLauncher.launch(request)
            } catch (e: Exception) {
                e.printStackTrace()
                directDeleteFallback(uris)
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Android 10
            try {
                for (uri in uris) {
                    contentResolver.delete(uri, null, null)
                }
                Toast.makeText(ctx, "✓ Selected files hidden successfully!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack(R.id.vaultFragment, false)
            } catch (e: SecurityException) {
                val intentSender = (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                if (intentSender != null) {
                    deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                } else {
                    findNavController().popBackStack(R.id.vaultFragment, false)
                }
            }
        } else {
            // Android 7, 8, 9
            directDeleteFallback(uris)
        }
    }

    private fun directDeleteFallback(uris: List<Uri>) {
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var deletedCount = 0
            for (uri in uris) {
                try {
                    val path = getPathFromUri(uri)
                    if (path != null) {
                        val f = File(path)
                        if (f.exists()) f.delete()
                    }
                    val rows = ctx.contentResolver.delete(uri, null, null)
                    if (rows > 0) deletedCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, "✓ $deletedCount files hidden successfully!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack(R.id.vaultFragment, false)
            }
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        val ctx = context ?: return null
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        return try {
            ctx.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
