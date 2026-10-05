package com.applockerapp.presentation.home

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.applockerapp.presentation.service.AppLockService
import com.applockerapp.utils.LockSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val lockSettingsManager: LockSettingsManager? = try {
        LockSettingsManager(context)
    } catch (e: Exception) {
        android.util.Log.e("HomeViewModel", "Failed to create LockSettingsManager", e)
        null
    }
    private val allAppsList = mutableListOf<AppItem>()
    private var currentFilter = "unlocked"

    private var isOverlayGranted = false
    private var isUsageGranted = false

    init {
        checkPermissionsState()
        loadHomeData()
    }

    fun checkPermissionsState() {
        val overlay = Settings.canDrawOverlays(context)
        
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        val usage = mode == AppOpsManager.MODE_ALLOWED

        android.util.Log.d("AppLockDebug", "HomeViewModel checkPermissionsState: overlay=$overlay, usage=$usage, mode=$mode")

        isOverlayGranted = overlay
        isUsageGranted = usage

        // Start Foreground protection service if both permissions are active
        if (overlay && usage) {
            try {
                android.util.Log.d("AppLockDebug", "HomeViewModel: Attempting to start AppLockService")
                val serviceIntent = Intent(context, AppLockService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
                android.util.Log.d("AppLockDebug", "HomeViewModel: startForegroundService called successfully")
            } catch (e: Exception) {
                android.util.Log.e("AppLockDebug", "HomeViewModel: Failed to start service", e)
            }
        }
        emitSuccessState()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            
            if (allAppsList.isEmpty()) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                
                val installedApps = try {
                    val resolveInfos = pm.queryIntentActivities(intent, 0)
                    resolveInfos.mapNotNull { resolveInfo ->
                        val actInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                        val packageName = actInfo.packageName ?: return@mapNotNull null
                        val appName = resolveInfo.loadLabel(pm)?.toString() ?: packageName
                        val isSystem = (actInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                        
                        // Load persistent locking state
                        val lockedState = lockSettingsManager?.isAppLocked(packageName) ?: false
                        
                        AppItem(
                            packageName = packageName,
                            appName = appName,
                            isSystemApp = isSystem,
                            isLocked = lockedState,
                            iconDrawableId = android.R.drawable.sym_def_app_icon
                        )
                    }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
                } catch (e: Exception) {
                    emptyList()
                }

                if (installedApps.isEmpty()) {
                    val mockList = listOf(
                        AppItem("com.facebook.katana", "Facebook", false, false, android.R.drawable.ic_menu_gallery),
                        AppItem("com.android.vending", "Google Play Store", true, false, android.R.drawable.ic_menu_manage),
                        AppItem("com.google.android.apps.photos", "Photos", true, false, android.R.drawable.ic_menu_gallery),
                        AppItem("com.android.settings", "Settings", true, false, android.R.drawable.ic_menu_preferences),
                        AppItem("com.whatsapp", "WhatsApp", false, false, android.R.drawable.ic_menu_compass),
                        AppItem("com.google.android.gm", "Gmail", true, false, android.R.drawable.ic_dialog_email),
                        AppItem("com.google.android.youtube", "YouTube", true, false, android.R.drawable.ic_media_play)
                    )
                    mockList.forEach {
                        it.isLocked = lockSettingsManager?.isAppLocked(it.packageName) ?: false
                    }
                    allAppsList.addAll(mockList)
                } else {
                    allAppsList.addAll(installedApps)
                }
            } else {
                // Refresh lock status
                allAppsList.forEach {
                    it.isLocked = lockSettingsManager?.isAppLocked(it.packageName) ?: false
                }
            }
            emitSuccessState()
        }
    }

    fun toggleAppLock(app: AppItem) {
        val index = allAppsList.indexOfFirst { it.packageName == app.packageName }
        if (index != -1) {
            val newLockState = !allAppsList[index].isLocked
            try {
                lockSettingsManager?.setAppLocked(app.packageName, newLockState)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error toggling app lock", e)
            }
            allAppsList[index] = allAppsList[index].copy(isLocked = newLockState)

            // Check if service needs to be triggered
            checkPermissionsState()
        }
    }

    fun lockAllApps() {
        viewModelScope.launch {
            allAppsList.forEach { app ->
                if (!app.isLocked) {
                    try {
                        lockSettingsManager?.setAppLocked(app.packageName, true)
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Error locking app ${app.packageName}", e)
                    }
                }
            }
            for (i in 0 until allAppsList.size) {
                allAppsList[i] = allAppsList[i].copy(isLocked = true)
            }
            checkPermissionsState()
        }
    }

    fun unlockAllApps() {
        viewModelScope.launch {
            allAppsList.forEach { app ->
                if (app.isLocked) {
                    try {
                        lockSettingsManager?.setAppLocked(app.packageName, false)
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Error unlocking app ${app.packageName}", e)
                    }
                }
            }
            for (i in 0 until allAppsList.size) {
                allAppsList[i] = allAppsList[i].copy(isLocked = false)
            }
            checkPermissionsState()
        }
    }

    fun setFilter(filter: String) {
        currentFilter = filter
        emitSuccessState()
    }

    private fun emitSuccessState() {
        val filteredList = when (currentFilter) {
            "locked" -> allAppsList.filter { it.isLocked }
            "unlocked" -> allAppsList.filter { !it.isLocked }
            else -> allAppsList
        }
        val lockedCount = allAppsList.count { it.isLocked }

        _uiState.value = HomeUiState.Success(
            apps = filteredList,
            allApps = allAppsList.toList(),
            lockedAppsCount = lockedCount,
            showPermissionOverlay = !(isOverlayGranted && isUsageGranted),
            isOverlayGranted = isOverlayGranted,
            isUsageGranted = isUsageGranted,
            currentFilter = currentFilter
        )
    }

    fun onRetryClicked() {
        loadHomeData()
    }
}

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val apps: List<AppItem>,
        val allApps: List<AppItem> = emptyList(),
        val lockedAppsCount: Int = 0,
        val showPermissionOverlay: Boolean,
        val isOverlayGranted: Boolean,
        val isUsageGranted: Boolean,
        val currentFilter: String
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
