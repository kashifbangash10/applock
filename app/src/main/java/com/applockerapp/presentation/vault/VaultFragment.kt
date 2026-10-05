package com.applockerapp.presentation.vault

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.applockerapp.R
import com.applockerapp.databinding.FragmentVaultBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class VaultFragment : Fragment() {

    private var _binding: FragmentVaultBinding? = null
    private val binding get() = _binding!!

    private lateinit var albumAdapter: VaultAlbumAdapter
    private var currentTab = TAB_IMAGES

    companion object {
        const val TAB_IMAGES = 0
        const val TAB_VIDEOS = 1
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            openDeviceAlbums(pendingTab)
        } else {
            Toast.makeText(context, "Storage permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingTab = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVaultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupTabs()
        setupButtons()
        loadVaultFiles()

        // Check All Files Access permission when vault opens
        if (!hasAllFilesPermission()) {
            showVaultPermissionDialog()
        }
    }

    private fun setupRecyclerView() {
        albumAdapter = VaultAlbumAdapter { album ->
            val args = Bundle().apply {
                putString("albumName", album.name)
                putLong("bucketId", album.bucketId)
                putBoolean("isVideo", album.mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            }
            findNavController().navigate(R.id.action_vaultFragment_to_vaultAlbumFragment, args)
        }
        binding.rvVaultMedia.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = albumAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            recycledViewPool.setMaxRecycledViews(0, 20)
        }
    }

    private fun setupTabs() {
        updateTabUI()
        binding.tabImages.setOnClickListener {
            currentTab = TAB_IMAGES
            updateTabUI()
            loadVaultFiles()
        }
        binding.tabVideos.setOnClickListener {
            currentTab = TAB_VIDEOS
            updateTabUI()
            loadVaultFiles()
        }
    }

    private fun updateTabUI() {
        val isImages = currentTab == TAB_IMAGES
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.tabImages.setTextColor(if (isImages) primaryColor else secondaryColor)
        binding.tabImages.setTypeface(null, if (isImages) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.tabVideos.setTextColor(if (!isImages) primaryColor else secondaryColor)
        binding.tabVideos.setTypeface(null, if (!isImages) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        // Animate indicator
        val parent = binding.root as androidx.constraintlayout.widget.ConstraintLayout
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(parent)
        val anchorId = if (isImages) R.id.tabImages else R.id.tabVideos
        constraintSet.connect(R.id.vaultTabIndicator, androidx.constraintlayout.widget.ConstraintSet.START, anchorId, androidx.constraintlayout.widget.ConstraintSet.START)
        constraintSet.connect(R.id.vaultTabIndicator, androidx.constraintlayout.widget.ConstraintSet.END, anchorId, androidx.constraintlayout.widget.ConstraintSet.END)
        constraintSet.applyTo(parent)
    }

    private fun setupButtons() {
        binding.btnBackVault.setOnClickListener { findNavController().popBackStack() }

        binding.btnImportImages.setOnClickListener {
            if (hasPermission()) {
                openDeviceAlbums(0)
            } else {
                pendingTab = 0
                requestPermission()
            }
        }

        binding.btnImportVideos.setOnClickListener {
            if (hasPermission()) {
                openDeviceAlbums(1)
            } else {
                pendingTab = 1
                requestPermission()
            }
        }
    }

    private fun openDeviceAlbums(tab: Int) {
        val args = Bundle().apply { putInt("startTab", tab) }
        findNavController().navigate(R.id.action_vaultFragment_to_deviceAlbumsFragment, args)
    }

    private fun loadVaultFiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isVideo = currentTab == TAB_VIDEOS
            val albums = withContext(Dispatchers.IO) {
                val sub = if (isVideo) "videos" else "images"
                val vaultDir = File(requireContext().filesDir, "vault/$sub")
                if (!vaultDir.exists()) vaultDir.mkdirs()

                val dirs = vaultDir.listFiles { file -> file.isDirectory } ?: emptyArray()
                dirs.map { dir ->
                    val files = dir.listFiles { f -> f.isFile } ?: emptyArray()
                    val thumbnail = files.firstOrNull()?.let { Uri.fromFile(it) }
                    VaultAlbum(
                        name = dir.name,
                        count = files.size,
                        thumbnailUri = thumbnail,
                        bucketId = dir.name.hashCode().toLong(),
                        mediaType = if (isVideo) MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO else MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    )
                }.filter { it.count > 0 }.sortedByDescending { it.count }
            }

            if (albums.isEmpty()) {
                binding.layoutVaultEmpty.visibility = View.VISIBLE
                binding.rvVaultMedia.visibility = View.GONE
            } else {
                binding.layoutVaultEmpty.visibility = View.GONE
                binding.rvVaultMedia.visibility = View.VISIBLE
                albumAdapter.submitList(albums)
            }
        }
    }

    // ===== PERMISSIONS =====

    private fun hasAllFilesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun showVaultPermissionDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_vault_permission, null)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btnGrantVaultPermission
        )?.setOnClickListener {
            dialog.dismiss()
            openAllFilesAccessSettings()
        }

        dialogView.findViewById<TextView>(
            R.id.btnNotNowVaultPermission
        )?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openAllFilesAccessSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }
    }

    private fun hasPermission(): Boolean {
        val ctx = context ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val img = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val vid = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val userSelected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            } else false
            img || vid || userSelected
        } else {
            val read = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val write = ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            read && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || write)
        }
    }

    private fun requestPermission() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        permissionLauncher.launch(perms)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
