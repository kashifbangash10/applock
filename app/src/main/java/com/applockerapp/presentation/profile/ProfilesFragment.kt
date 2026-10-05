package com.applockerapp.presentation.profile

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.applockerapp.R
import com.applockerapp.databinding.DialogEditProfileBinding
import com.applockerapp.databinding.FragmentProfilesBinding
import com.applockerapp.presentation.home.AppItem
import com.applockerapp.utils.LockSettingsManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class ProfilesFragment : Fragment() {

    private var _binding: FragmentProfilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var lockSettingsManager: LockSettingsManager
    private lateinit var profileAdapter: ProfileAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lockSettingsManager = LockSettingsManager(requireContext())
        setupRecyclerView()
        setupClickListeners()
        refreshProfilesList()
    }

    private fun setupRecyclerView() {
        profileAdapter = ProfileAdapter(
            onProfileActiveChanged = { profile, isActive ->
                if (isActive) {
                    lockSettingsManager.activateProfile(profile.id)
                } else {
                    lockSettingsManager.activateProfile(null)
                }
                // Refresh list so other profiles visual state toggles off
                refreshProfilesList()
                val statusText = if (isActive) "activated" else "deactivated"
                Toast.makeText(context, "${profile.name} profile $statusText!", Toast.LENGTH_SHORT).show()
            },
            onEditProfile = { profile ->
                showEditProfileDialog(profile)
            },
            onDeleteProfile = { profile ->
                showDeleteConfirmationDialog(profile)
            }
        )

        binding.rvProfiles.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = profileAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.fabAddProfile.setOnClickListener {
            showEditProfileDialog(null)
        }
    }

    private fun refreshProfilesList() {
        val list = lockSettingsManager.getProfiles()
        profileAdapter.setProfiles(list)
    }

    private fun showEditProfileDialog(profileToEdit: AppLockProfile?) {
        val dialog = BottomSheetDialog(requireContext(), R.style.LockDialogTheme)
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.tvDialogTitle.text = if (profileToEdit != null) "Edit Profile" else "Create Profile"
        dialogBinding.etProfileName.setText(profileToEdit?.name ?: "")
        dialogBinding.etProfileDesc.setText(profileToEdit?.description ?: "")

        // Fetch installed apps list (launchers)
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val appItems = resolveInfos.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(pm).toString()
            val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isSelected = profileToEdit?.packages?.contains(packageName) ?: false
            AppItem(
                packageName = packageName,
                appName = appName,
                isSystemApp = isSystem,
                isLocked = isSelected,
                iconDrawableId = android.R.drawable.sym_def_app_icon
            )
        }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }

        val appAdapter = ProfileAppAdapter { _ -> }
        appAdapter.setApps(appItems)

        dialogBinding.rvProfileApps.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = appAdapter
        }

        // Live filtering search
        dialogBinding.etSearchApps.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appAdapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Save Click
        dialogBinding.btnSaveProfile.setOnClickListener {
            val name = dialogBinding.etProfileName.text?.toString()?.trim() ?: ""
            val desc = dialogBinding.etProfileDesc.text?.toString()?.trim() ?: ""

            if (name.isEmpty()) {
                Toast.makeText(context, "Please enter a profile name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedPackages = appAdapter.getSelectedPackages()
            if (selectedPackages.isEmpty()) {
                Toast.makeText(context, "Please select at least one app to lock", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newProfile = AppLockProfile(
                id = profileToEdit?.id ?: UUID.randomUUID().toString(),
                name = name,
                description = desc,
                packages = selectedPackages,
                isActive = profileToEdit?.isActive ?: false,
                iconType = profileToEdit?.iconType ?: "custom"
            )

            lockSettingsManager.saveProfile(newProfile)
            dialog.dismiss()
            refreshProfilesList()
            Toast.makeText(context, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showDeleteConfirmationDialog(profile: AppLockProfile) {
        AlertDialog.Builder(requireContext(), R.style.TermsDialogTheme)
            .setTitle("Delete Profile")
            .setMessage("Are you sure you want to delete '${profile.name}'?")
            .setPositiveButton("Delete") { dialog, _ ->
                lockSettingsManager.deleteProfile(profile.id)
                refreshProfilesList()
                dialog.dismiss()
                Toast.makeText(context, "Profile deleted!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
