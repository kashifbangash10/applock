package com.applockerapp.presentation.settings

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.applockerapp.R
import com.applockerapp.databinding.DialogSelectLockTypeBinding
import com.applockerapp.databinding.FragmentSettingsBinding
import com.applockerapp.databinding.DialogLockScreenSettingsBinding
import com.applockerapp.presentation.lock.LanguageActivity
import com.applockerapp.presentation.lock.LockSetupActivity
import com.applockerapp.presentation.service.AppLockService
import com.applockerapp.receiver.AdminReceiver
import com.applockerapp.utils.LockSettingsManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.content.Intent
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var lockSettingsManager: LockSettingsManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName

    companion object {
        private const val PREFS_NAME = "applock_prefs"
        private const val KEY_DARK_MODE = "is_dark_mode"
    }

    // Launcher for Device Admin activation intent
    private val adminActivationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Admin was activated successfully
            syncUninstallProtectionState()
            Toast.makeText(context, "Uninstall Protection Activated 🛡️", Toast.LENGTH_SHORT).show()
        } else {
            // User cancelled — revert switch to OFF without triggering listener
            binding.switchUninstallProtection.setOnCheckedChangeListener(null)
            binding.switchUninstallProtection.isChecked = false
            syncUninstallProtectionState()
            Toast.makeText(context, "Uninstall Protection not activated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lockSettingsManager = LockSettingsManager(requireContext())
        devicePolicyManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(requireContext(), AdminReceiver::class.java)
        setupSwitches()
        setupListeners()
    }

    private fun setupSwitches() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load Fingerprint toggle state
        binding.switchFingerprint.isChecked = prefs.getBoolean("fingerprint_unlock_enabled", false)

        // Load new switch states
        binding.switchAppLock.isChecked = lockSettingsManager.isMasterLockEnabled()

        // Load Screenshot Protection & Failed Lockout state
        binding.switchScreenshotProtection.isChecked = lockSettingsManager.isScreenshotProtectionEnabled()
        binding.switchFailedLockout.isChecked = lockSettingsManager.isFailedAttemptsLockoutEnabled()
    }

    private fun setupListeners() {
        binding.btnBackSettings.setOnClickListener {
            findNavController().popBackStack()
        }

        // Change password
        binding.rlChangePass.setOnClickListener {
            showLockTypeDialog(isChangeFlow = true)
        }

        // Password type
        binding.rlPasswordType.setOnClickListener {
            showLockTypeDialog(isChangeFlow = false)
        }

        // Security Question
        binding.rlSecurityQuestion.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_securityQuestionFragment)
        }

        // Change App Icon
        binding.rlChangeAppIcon.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_changeAppIconFragment)
        }

        // Fingerprint Unlock toggle
        binding.switchFingerprint.setOnCheckedChangeListener { _, isChecked ->
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("fingerprint_unlock_enabled", isChecked).apply()
            val status = if (isChecked) "enabled ☝️" else "disabled"
            Toast.makeText(context, "Fingerprint Unlock $status", Toast.LENGTH_SHORT).show()
        }

        // Screenshot Protection toggle
        binding.switchScreenshotProtection.setOnCheckedChangeListener { _, isChecked ->
            lockSettingsManager.setScreenshotProtectionEnabled(isChecked)
            val status = if (isChecked) "enabled 📸" else "disabled"
            Toast.makeText(context, "Screenshot Protection $status", Toast.LENGTH_SHORT).show()
            
            // TODO: Screenshot protection disabled temporarily — re-enable later
            // val activity = requireActivity()
            // if (isChecked) {
            //     activity.window.setFlags(
            //         android.view.WindowManager.LayoutParams.FLAG_SECURE,
            //         android.view.WindowManager.LayoutParams.FLAG_SECURE
            //     )
            // } else {
            //     activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            // }
        }

        // Failed Lockout toggle
        binding.switchFailedLockout.setOnCheckedChangeListener { _, isChecked ->
            lockSettingsManager.setFailedAttemptsLockoutEnabled(isChecked)
            val status = if (isChecked) "enabled 🔒" else "disabled"
            Toast.makeText(context, "Failed Attempts Lockout $status", Toast.LENGTH_SHORT).show()
        }

        // Uninstall Protection will be initialized and set up in onResume to sync with actual Device Admin state


        // App lock toggle — actually starts/stops the AppLockService
        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            lockSettingsManager.setMasterLockEnabled(isChecked)
            val serviceIntent = Intent(requireContext(), AppLockService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(serviceIntent)
                } else {
                    requireContext().startService(serviceIntent)
                }
                Toast.makeText(context, "App lock service activated ⚙️ — apps are protected!", Toast.LENGTH_SHORT).show()
            } else {
                requireContext().stopService(serviceIntent)
                Toast.makeText(context, "App lock service deactivated — apps are unprotected!", Toast.LENGTH_SHORT).show()
            }
        }

        // ── Others Section ──

        // Language
        binding.rlLanguage.setOnClickListener {
            val intent = Intent(requireContext(), LanguageActivity::class.java).apply {
                putExtra("from_settings", true)
            }
            startActivity(intent)
        }

        // Rate Us
        binding.rlRateUs.setOnClickListener {
            showRateUsDialog()
        }

        // Share
        binding.rlShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AppLocker Pro - Protect your apps!")
                putExtra(Intent.EXTRA_TEXT,
                    "🔒 Protect your privacy with AppLocker Pro!\n" +
                    "Download now: https://play.google.com/store/apps/details?id=${requireContext().packageName}")
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        // Privacy Policy
        binding.rlPrivacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://sites.google.com/view/applockerapp-privacy-policy"))
            startActivity(intent)
        }

        // Lock Screen Settings
        binding.rlLockScreenSettings.setOnClickListener {
            showLockScreenSettingsDialog()
        }
    }

    private fun showLockTypeDialog(isChangeFlow: Boolean) {
        val dialog = BottomSheetDialog(requireContext(), R.style.LockDialogTheme)
        val dialogBinding = DialogSelectLockTypeBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        if (isChangeFlow) {
            dialogBinding.tvDialogTitle.text = "Change Password"
            dialogBinding.btnDialogApply.text = "Change"
        } else {
            dialogBinding.tvDialogTitle.text = "Lock Type"
            dialogBinding.btnDialogApply.text = "Apply"
        }

        var selectedType = lockSettingsManager.getLockType()

        fun updateSelectionUI() {
            val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
            val textMuted = ContextCompat.getColor(requireContext(), R.color.text_muted)
            val textPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary)
            val surfaceCard = ContextCompat.getColor(requireContext(), R.color.bg_surface_card)
            val bgHighlight = ContextCompat.getColor(requireContext(), R.color.bg_card_highlight)
            val translucentWhite = ContextCompat.getColor(requireContext(), R.color.translucent_white)

            if (selectedType == "pin") {
                dialogBinding.cardPinOption.setCardBackgroundColor(bgHighlight)
                dialogBinding.cardPinOption.strokeColor = primaryColor
                dialogBinding.cardPinOption.strokeWidth = 3
                dialogBinding.imgPinIcon.setColorFilter(primaryColor)
                dialogBinding.tvPinText.setTextColor(primaryColor)
                dialogBinding.imgPinCheck.visibility = View.VISIBLE
            } else {
                dialogBinding.cardPinOption.setCardBackgroundColor(surfaceCard)
                dialogBinding.cardPinOption.strokeColor = translucentWhite
                dialogBinding.cardPinOption.strokeWidth = 1
                dialogBinding.imgPinIcon.setColorFilter(textMuted)
                dialogBinding.tvPinText.setTextColor(textPrimary)
                dialogBinding.imgPinCheck.visibility = View.GONE
            }

            if (selectedType == "pattern") {
                dialogBinding.cardPatternOption.setCardBackgroundColor(bgHighlight)
                dialogBinding.cardPatternOption.strokeColor = primaryColor
                dialogBinding.cardPatternOption.strokeWidth = 3
                dialogBinding.imgPatternIcon.setColorFilter(primaryColor)
                dialogBinding.tvPatternText.setTextColor(primaryColor)
                dialogBinding.imgPatternCheck.visibility = View.VISIBLE
            } else {
                dialogBinding.cardPatternOption.setCardBackgroundColor(surfaceCard)
                dialogBinding.cardPatternOption.strokeColor = translucentWhite
                dialogBinding.cardPatternOption.strokeWidth = 1
                dialogBinding.imgPatternIcon.setColorFilter(textMuted)
                dialogBinding.tvPatternText.setTextColor(textPrimary)
                dialogBinding.imgPatternCheck.visibility = View.GONE
            }

            if (selectedType == "password") {
                dialogBinding.cardPasswordOption.setCardBackgroundColor(bgHighlight)
                dialogBinding.cardPasswordOption.strokeColor = primaryColor
                dialogBinding.cardPasswordOption.strokeWidth = 3
                dialogBinding.imgPasswordIcon.setColorFilter(primaryColor)
                dialogBinding.tvPasswordText.setTextColor(primaryColor)
                dialogBinding.imgPasswordCheck.visibility = View.VISIBLE
            } else {
                dialogBinding.cardPasswordOption.setCardBackgroundColor(surfaceCard)
                dialogBinding.cardPasswordOption.strokeColor = translucentWhite
                dialogBinding.cardPasswordOption.strokeWidth = 1
                dialogBinding.imgPasswordIcon.setColorFilter(textMuted)
                dialogBinding.tvPasswordText.setTextColor(textPrimary)
                dialogBinding.imgPasswordCheck.visibility = View.GONE
            }
        }

        updateSelectionUI()

        dialogBinding.cardPinOption.setOnClickListener {
            selectedType = "pin"
            updateSelectionUI()
        }

        dialogBinding.cardPatternOption.setOnClickListener {
            selectedType = "pattern"
            updateSelectionUI()
        }

        dialogBinding.cardPasswordOption.setOnClickListener {
            selectedType = "password"
            updateSelectionUI()
        }

        dialogBinding.btnDialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDialogApply.setOnClickListener {
            dialog.dismiss()
            lockSettingsManager.setLockType(selectedType)

            val intent = Intent(requireContext(), LockSetupActivity::class.java).apply {
                putExtra("force_setup", true)
                putExtra("force_lock_type", selectedType)
            }
            startActivity(intent)
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        syncUninstallProtectionState()
    }

    private fun syncUninstallProtectionState() {
        val isAdminActive = devicePolicyManager.isAdminActive(adminComponentName)

        // Temporarily clear listener to prevent feedback loop
        binding.switchUninstallProtection.setOnCheckedChangeListener(null)
        binding.switchUninstallProtection.isChecked = isAdminActive

        binding.switchUninstallProtection.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Launch system Device Admin activation screen
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Enable Uninstall Protection to prevent this app from being removed without your password."
                    )
                }
                adminActivationLauncher.launch(intent)
            } else {
                // Remove device admin — this disables uninstall protection
                devicePolicyManager.removeActiveAdmin(adminComponentName)
                Toast.makeText(context, "Uninstall Protection Deactivated", Toast.LENGTH_SHORT).show()
                syncUninstallProtectionState()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showLockScreenSettingsDialog() {
        val dialog = BottomSheetDialog(requireContext(), R.style.LockDialogTheme)
        val dialogBinding = DialogLockScreenSettingsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        dialogBinding.rlOverlayPermission.setOnClickListener {
            openOverlaySettings()
            dialog.dismiss()
        }

        dialogBinding.rlUsageAccessPermission.setOnClickListener {
            openUsageSettings()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openOverlaySettings() {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${requireContext().packageName}")
            )
            startActivity(intent)
            Toast.makeText(context, "Please allow AppLocker to Display Over Other Apps", Toast.LENGTH_LONG).show()
        } catch (e: java.lang.Exception) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
        }
    }

    private fun openUsageSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(context, "Please allow Usage Access permission for AppLocker", Toast.LENGTH_LONG).show()
        } catch (e: java.lang.Exception) {
            Toast.makeText(context, "Failed to open settings automatically.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRateUsDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .create()
        val dialogBinding = com.applockerapp.databinding.DialogRateUsBinding.inflate(layoutInflater)
        dialog.setView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        var selectedRating = 0

        fun updateStars(rating: Int) {
            selectedRating = rating
            val goldColor = android.graphics.Color.parseColor("#FFD700")
            val mutedColor = android.graphics.Color.parseColor("#6F8C80")

            val stars = listOf(
                dialogBinding.star1,
                dialogBinding.star2,
                dialogBinding.star3,
                dialogBinding.star4,
                dialogBinding.star5
            )

            stars.forEachIndexed { i, star ->
                if (i < rating) {
                    star.setImageResource(R.drawable.ic_star)
                    star.setColorFilter(goldColor)
                    star.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).withEndAction {
                        star.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    }.start()
                } else {
                    star.setImageResource(R.drawable.ic_star_outline)
                    star.setColorFilter(mutedColor)
                }
            }
        }

        updateStars(0)

        listOf(
            dialogBinding.star1,
            dialogBinding.star2,
            dialogBinding.star3,
            dialogBinding.star4,
            dialogBinding.star5
        ).forEachIndexed { index, star ->
            star.setOnClickListener {
                updateStars(index + 1)
            }
        }

        dialogBinding.btnNotNow.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnRateNow.setOnClickListener {
            dialog.dismiss()
            try {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=${requireContext().packageName}")
                )
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=${requireContext().packageName}")
                )
                startActivity(intent)
            }
        }

        dialog.show()
    }
}
