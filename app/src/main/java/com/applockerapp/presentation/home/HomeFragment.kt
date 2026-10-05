package com.applockerapp.presentation.home

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.applockerapp.R
import com.applockerapp.databinding.DialogLockAppBinding
import com.applockerapp.databinding.FragmentHomeBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var appAdapter: AppAdapter

    private var iconAnimationJob: kotlinx.coroutines.Job? = null
    private var currentIconIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        setupSlidingPanel()
        observeUiState()
        startShieldPulseAnimation()
    }

    private var shieldPulseAnimator: android.animation.ObjectAnimator? = null

    private fun startShieldPulseAnimation() {
        if (shieldPulseAnimator?.isRunning == true) return
        val scaleX = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.07f, 1.0f)
        val scaleY = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.07f, 1.0f)
        val alpha = android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.90f, 1.0f, 0.90f)

        shieldPulseAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(binding.ivShieldIcon, scaleX, scaleY, alpha).apply {
            duration = 2400L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.RESTART
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        shieldPulseAnimator?.start()
    }

    override fun onResume() {
        super.onResume()
        // Refresh permissions state when user returns to app from system settings
        viewModel.checkPermissionsState()
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { appItem ->
            // Check permissions before allowing lock/unlock
            val state = viewModel.uiState.value
            if (state is HomeUiState.Success && (!state.isOverlayGranted || !state.isUsageGranted)) {
                showPermissionDialogUI(state.isOverlayGranted, state.isUsageGranted)
            } else {
                showLockDialog(appItem)
            }
        }
        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
            setHasFixedSize(true)
        }
    }

    private fun showPermissionDialogUI(isOverlayGranted: Boolean, isUsageGranted: Boolean) {
        binding.permissionOverlayBackdrop.visibility = View.VISIBLE
        binding.permissionOverlayContainer.visibility = View.VISIBLE

        // Update Usage Access status
        val imgUsage = binding.permissionOverlayContainer.findViewById<ImageView>(R.id.imgUsageStatus)
        val tvUsage = binding.permissionOverlayContainer.findViewById<TextView>(R.id.tvUsageStatus)
        val btnUsage = binding.permissionOverlayContainer.findViewById<MaterialButton>(R.id.cbUsageAccess)
        val redColorStateList = android.content.res.ColorStateList.valueOf(0xFFFF5252.toInt())
        val redColorInt = 0xFFFF5252.toInt()

        if (isUsageGranted) {
            imgUsage?.setImageResource(R.drawable.ic_lock_closed)
            imgUsage?.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
            tvUsage?.text = "Allowed"
            tvUsage?.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            btnUsage?.text = "Already Enabled"
            btnUsage?.alpha = 0.6f
            btnUsage?.isEnabled = false
        } else {
            imgUsage?.setImageResource(R.drawable.ic_close)
            imgUsage?.imageTintList = redColorStateList
            tvUsage?.text = "Not Allowed"
            tvUsage?.setTextColor(redColorInt)
            btnUsage?.text = "Enable Usage Access"
            btnUsage?.alpha = 1.0f
            btnUsage?.isEnabled = true
        }

        // Update Overlay Permission status
        val imgOverlay = binding.permissionOverlayContainer.findViewById<ImageView>(R.id.imgOverlayStatus)
        val tvOverlay = binding.permissionOverlayContainer.findViewById<TextView>(R.id.tvOverlayStatus)
        val btnOverlay = binding.permissionOverlayContainer.findViewById<MaterialButton>(R.id.cbDisplayOverApps)
        if (isOverlayGranted) {
            imgOverlay?.setImageResource(R.drawable.ic_lock_closed)
            imgOverlay?.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
            tvOverlay?.text = "Allowed"
            tvOverlay?.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            btnOverlay?.text = "Already Enabled"
            btnOverlay?.alpha = 0.6f
            btnOverlay?.isEnabled = false
        } else {
            imgOverlay?.setImageResource(R.drawable.ic_close)
            imgOverlay?.imageTintList = redColorStateList
            tvOverlay?.text = "Not Allowed"
            tvOverlay?.setTextColor(redColorInt)
            btnOverlay?.text = "Enable Overlay"
            btnOverlay?.alpha = 1.0f
            btnOverlay?.isEnabled = true
        }
    }

    private fun showLockDialog(appItem: AppItem) {
        val dialog = BottomSheetDialog(requireContext(), R.style.LockDialogTheme)
        val dialogBinding = DialogLockAppBinding.inflate(LayoutInflater.from(requireContext()))
        dialog.setContentView(dialogBinding.root)

        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        try {
            val pm = requireContext().packageManager
            val icon: Drawable = pm.getApplicationIcon(appItem.packageName)
            dialogBinding.dialogAppIcon.setImageDrawable(icon)
        } catch (e: Exception) {
            dialogBinding.dialogAppIcon.setImageResource(appItem.iconDrawableId)
        }

        val isCurrentlyLocked = appItem.isLocked

        if (isCurrentlyLocked) {
            dialogBinding.dialogTitle.text = "Unlock ${appItem.appName}"
            dialogBinding.dialogDescription.text =
                "Unlock \"${appItem.appName}\" to allow access to this app."
            dialogBinding.btnLockAction.text = "Unlock"
            dialogBinding.btnLockAction.setIconResource(R.drawable.ic_lock_open)
            dialogBinding.btnLockAction.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.accent_color)
            dialogBinding.dialogLockBadge.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.accent_color)
            )
            dialogBinding.dialogLockBadgeIcon.setImageResource(R.drawable.ic_lock_closed)
        } else {
            dialogBinding.dialogTitle.text = "Lock ${appItem.appName}"
            dialogBinding.dialogDescription.text =
                "Lock \"${appItem.appName}\" to protect your privacy."
            dialogBinding.btnLockAction.text = "Lock"
            dialogBinding.btnLockAction.setIconResource(R.drawable.ic_lock_closed)
            dialogBinding.btnLockAction.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.primary)
            dialogBinding.dialogLockBadge.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.primary)
            )
            dialogBinding.dialogLockBadgeIcon.setImageResource(R.drawable.ic_lock_open)
        }

        dialogBinding.btnLockAction.setOnClickListener {
            viewModel.toggleAppLock(appItem)
            dialog.dismiss()
            val message = if (isCurrentlyLocked)
                "${appItem.appName} unlocked ✅"
            else
                "${appItem.appName} locked 🔒"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        dialogBinding.tvNotNow.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun navigateSafe(actionId: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.homeFragment) {
            try {
                navController.navigate(actionId)
            } catch (e: Exception) {
                // Ignore rapid click destination collision
            }
        }
    }

    private fun setupClickListeners() {
        binding.cardIntruder.setOnClickListener {
            navigateSafe(R.id.action_homeFragment_to_intruderSelfieFragment)
        }
        binding.cardThemes.setOnClickListener {
            navigateSafe(R.id.action_homeFragment_to_themesFragment)
        }
        binding.cardProfiles.setOnClickListener {
            navigateSafe(R.id.action_homeFragment_to_profilesFragment)
        }
        binding.cardVault.setOnClickListener {
            if (hasVaultStoragePermission()) {
                navigateSafe(R.id.action_homeFragment_to_vaultFragment)
            } else {
                showVaultPermissionDialog()
            }
        }

        // Main Tabs change filter on click
        binding.tabUnlocked.setOnClickListener { viewModel.setFilter("unlocked") }
        binding.tabLocked.setOnClickListener { viewModel.setFilter("locked") }
        binding.tabUnlockedHeader.setOnClickListener { viewModel.setFilter("unlocked") }
        binding.tabLockedHeader.setOnClickListener { viewModel.setFilter("locked") }

        binding.btnManageLockedApps.setOnClickListener {
            viewModel.setFilter("locked")
            val behavior = (binding.panelContainer.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams).behavior as? SlidingPanelBehavior
            val maxH = behavior?.layer1Height?.toFloat() ?: 0f
            if (maxH > 0f) {
                val anim = android.animation.ObjectAnimator.ofFloat(
                    binding.panelContainer,
                    "translationY",
                    binding.panelContainer.translationY,
                    0f
                )
                anim.duration = 280L
                anim.interpolator = android.view.animation.DecelerateInterpolator()
                anim.addUpdateListener { animation ->
                    val currentVal = animation.animatedValue as Float
                    behavior?.onPanelTranslationChanged?.invoke(currentVal, maxH.toInt())
                }
                anim.start()
            }
            binding.rvApps.scrollToPosition(0)
        }

        binding.btnSearch.setOnClickListener {
            navigateSafe(R.id.action_homeFragment_to_searchFragment)
        }

        binding.btnSearchExpanded.setOnClickListener {
            binding.btnSearch.performClick()
        }

        binding.btnBackFromExpanded.setOnClickListener {
            // Animate panelContainer translationY back to layer1Height (resting position) AND scroll list to top
            val behavior = (binding.panelContainer.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams).behavior as? SlidingPanelBehavior
            val maxH = behavior?.layer1Height?.toFloat() ?: 0f
            if (maxH > 0f) {
                val anim = android.animation.ObjectAnimator.ofFloat(
                    binding.panelContainer,
                    "translationY",
                    binding.panelContainer.translationY,
                    maxH
                )
                anim.duration = 280L
                anim.interpolator = android.view.animation.DecelerateInterpolator()
                anim.addUpdateListener { animation ->
                    val currentVal = animation.animatedValue as Float
                    behavior?.onPanelTranslationChanged?.invoke(currentVal, maxH.toInt())
                }
                anim.start()
            }
            binding.rvApps.scrollToPosition(0)
        }

        binding.btnDrawer.setOnClickListener {
            navigateSafe(R.id.action_homeFragment_to_settingsFragment)
        }

        // Permission Card Interactions
        val btnOverlay = binding.permissionOverlayContainer.findViewById<MaterialButton>(R.id.cbDisplayOverApps)
        val btnUsage = binding.permissionOverlayContainer.findViewById<MaterialButton>(R.id.cbUsageAccess)
        val btnNotNow = binding.permissionOverlayContainer.findViewById<TextView>(R.id.btnGrantPermissions)
        val rlOverlay = binding.permissionOverlayContainer.findViewById<View>(R.id.rlPermissionOverlays)
        val rlUsage = binding.permissionOverlayContainer.findViewById<View>(R.id.rlPermissionUsage)

        rlOverlay?.setOnClickListener { openOverlaySettings() }
        btnOverlay?.setOnClickListener { openOverlaySettings() }

        rlUsage?.setOnClickListener { openUsageSettings() }
        btnUsage?.setOnClickListener { openUsageSettings() }

        btnNotNow?.setOnClickListener {
            binding.permissionOverlayBackdrop.visibility = View.GONE
            binding.permissionOverlayContainer.visibility = View.GONE
        }
    }

    private fun setupSlidingPanel() {
        val behavior = (binding.panelContainer.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams).behavior as? SlidingPanelBehavior
            ?: return

        behavior.onPanelTranslationChanged = { currentTransY, maxTransY ->
            val ratio = if (maxTransY > 0) 1.0f - (currentTransY / maxTransY.toFloat()) else 0f
            if (ratio >= 0.85f || currentTransY <= 0f) {
                if (binding.layoutHeaderExpanded.visibility != View.VISIBLE) {
                    binding.tabLayoutContainer.visibility = View.GONE
                    binding.layoutHeaderExpanded.visibility = View.VISIBLE
                }
            } else {
                if (binding.tabLayoutContainer.visibility != View.VISIBLE) {
                    binding.layoutHeaderExpanded.visibility = View.GONE
                    binding.tabLayoutContainer.visibility = View.VISIBLE
                }
            }
        }

        binding.layoutFixedBackground.post {
            val height = binding.layoutFixedBackground.height
            if (height > 0) {
                behavior.layer1Height = height
                binding.panelContainer.translationY = height.toFloat()
            }
        }
    }

    private fun openOverlaySettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            startActivity(intent)
            Toast.makeText(context, "Please allow AppLocker to Display Over Other Apps", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
        }
    }

    private fun openUsageSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(context, "Please allow Usage Access permission for AppLocker", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to open settings automatically.", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== VAULT STORAGE PERMISSION =====

    private fun hasVaultStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Below Android 11, READ_EXTERNAL_STORAGE is sufficient
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
            // Navigate to vault anyway (will handle inside VaultFragment)
            findNavController().navigate(R.id.action_homeFragment_to_vaultFragment)
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

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: HomeUiState) {
        when (state) {
            is HomeUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvApps.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
            }
            is HomeUiState.Success -> {
                binding.progressBar.visibility = View.GONE

                appAdapter.submitList(state.apps) {
                    binding.rvApps.visibility = if (state.apps.isNotEmpty()) View.VISIBLE else View.GONE
                    if (state.apps.isEmpty() && state.currentFilter == "locked") {
                        binding.layoutEmptyState.visibility = View.VISIBLE
                    } else {
                        binding.layoutEmptyState.visibility = View.GONE
                    }
                }

                // Update Protection Status Card
                binding.tvLockedAppsCount.text = "${state.lockedAppsCount} Apps Locked"
                if (state.lockedAppsCount == 0) {
                    binding.tvProtectionStatusState.text = "Inactive"
                    binding.tvProtectionStatusState.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning_amber))
                    binding.ivStatusBadge.visibility = View.VISIBLE
                } else {
                    binding.tvProtectionStatusState.text = "Protected"
                    binding.tvProtectionStatusState.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                    binding.ivStatusBadge.visibility = View.GONE
                }

                // Update App Icons Preview Animation
                updateAppIconsPreview(state.allApps)

                if (state.showPermissionOverlay) {
                    showPermissionDialogUI(state.isOverlayGranted, state.isUsageGranted)
                } else {
                    binding.permissionOverlayBackdrop.visibility = View.GONE
                    binding.permissionOverlayContainer.visibility = View.GONE
                }

                val isUnlocked = state.currentFilter == "unlocked"

                if (isUnlocked) {
                    binding.tabUnlocked.setBackgroundResource(R.drawable.bg_tab_capsule_active)
                    binding.tabUnlocked.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    binding.tabUnlocked.setTypeface(null, android.graphics.Typeface.BOLD)

                    binding.tabLocked.setBackgroundResource(R.drawable.bg_tab_capsule_inactive)
                    binding.tabLocked.setTextColor(android.graphics.Color.parseColor("#8A9FA9"))
                    binding.tabLocked.setTypeface(null, android.graphics.Typeface.NORMAL)

                    binding.tabUnlockedHeader.setBackgroundResource(R.drawable.bg_tab_capsule_active)
                    binding.tabUnlockedHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    binding.tabUnlockedHeader.setTypeface(null, android.graphics.Typeface.BOLD)

                    binding.tabLockedHeader.setBackgroundResource(R.drawable.bg_tab_capsule_inactive)
                    binding.tabLockedHeader.setTextColor(android.graphics.Color.parseColor("#8A9FA9"))
                    binding.tabLockedHeader.setTypeface(null, android.graphics.Typeface.NORMAL)
                } else {
                    binding.tabLocked.setBackgroundResource(R.drawable.bg_tab_capsule_active)
                    binding.tabLocked.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    binding.tabLocked.setTypeface(null, android.graphics.Typeface.BOLD)

                    binding.tabUnlocked.setBackgroundResource(R.drawable.bg_tab_capsule_inactive)
                    binding.tabUnlocked.setTextColor(android.graphics.Color.parseColor("#8A9FA9"))
                    binding.tabUnlocked.setTypeface(null, android.graphics.Typeface.NORMAL)

                    binding.tabLockedHeader.setBackgroundResource(R.drawable.bg_tab_capsule_active)
                    binding.tabLockedHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    binding.tabLockedHeader.setTypeface(null, android.graphics.Typeface.BOLD)

                    binding.tabUnlockedHeader.setBackgroundResource(R.drawable.bg_tab_capsule_inactive)
                    binding.tabUnlockedHeader.setTextColor(android.graphics.Color.parseColor("#8A9FA9"))
                    binding.tabUnlockedHeader.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
            is HomeUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateAppIconsPreview(allApps: List<AppItem>) {
        if (allApps.isEmpty()) return
        if (iconAnimationJob?.isActive == true) return

        iconAnimationJob = viewLifecycleOwner.lifecycleScope.launch {
            val pm = requireContext().packageManager
            val size = allApps.size

            while (isActive && isAdded) {
                val icon1App = allApps[currentIconIndex % size]
                val icon2App = allApps[(currentIconIndex + 1) % size]
                val icon3App = allApps[(currentIconIndex + 2) % size]

                val draw1 = loadAppDrawable(pm, icon1App)
                val draw2 = loadAppDrawable(pm, icon2App)
                val draw3 = loadAppDrawable(pm, icon3App)

                binding.ivPreviewIcon1.setImageDrawable(draw1)
                binding.ivPreviewIcon2.setImageDrawable(draw2)
                binding.ivPreviewIcon3.setImageDrawable(draw3)

                currentIconIndex = (currentIconIndex + 1) % size
                delay(2500)
            }
        }
    }

    private fun loadAppDrawable(pm: android.content.pm.PackageManager, appItem: AppItem): Drawable {
        return try {
            pm.getApplicationIcon(appItem.packageName)
        } catch (e: Exception) {
            ContextCompat.getDrawable(requireContext(), appItem.iconDrawableId)
                ?: ContextCompat.getDrawable(requireContext(), android.R.drawable.sym_def_app_icon)!!
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        iconAnimationJob?.cancel()
        _binding = null
    }
}
