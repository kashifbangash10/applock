package com.applockerapp.presentation.search

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.applockerapp.presentation.home.AppItem
import com.applockerapp.utils.LockSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Loading)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val lockSettingsManager = LockSettingsManager(context)
    private val allAppsList = mutableListOf<AppItem>()
    private val searchQuery = MutableStateFlow("")
    private var isAppLoadingFinished = false

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            
            val installedApps = withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val intent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveInfos = pm.queryIntentActivities(intent, 0)
                    resolveInfos.mapNotNull { resolveInfo ->
                        try {
                            val actInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                            val packageName = actInfo.packageName ?: return@mapNotNull null
                            val appName = resolveInfo.loadLabel(pm)?.toString() ?: packageName
                            val isSystem = ((actInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0
                            val lockedState = lockSettingsManager.isAppLocked(packageName)
                            
                            AppItem(
                                packageName = packageName,
                                appName = appName,
                                isSystemApp = isSystem,
                                isLocked = lockedState,
                                iconDrawableId = android.R.drawable.sym_def_app_icon
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            allAppsList.clear()
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
                    it.isLocked = lockSettingsManager.isAppLocked(it.packageName)
                }
                allAppsList.addAll(mockList)
            } else {
                allAppsList.addAll(installedApps)
            }

            isAppLoadingFinished = true
            emitFilteredApps()
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        if (isAppLoadingFinished) {
            emitFilteredApps()
        }
    }

    fun toggleAppLock(app: AppItem) {
        val index = allAppsList.indexOfFirst { it.packageName == app.packageName }
        if (index != -1) {
            val newLockState = !allAppsList[index].isLocked
            lockSettingsManager.setAppLocked(app.packageName, newLockState)
            allAppsList[index] = allAppsList[index].copy(isLocked = newLockState)
            emitFilteredApps()
        }
    }

    private fun emitFilteredApps() {
        if (!isAppLoadingFinished) return
        val query = searchQuery.value.lowercase().trim()
        val filtered: List<AppItem> = if (query.isEmpty()) {
            // Return a new list copy so DiffUtil always gets a fresh reference
            allAppsList.toList()
        } else {
            val startsWithList = allAppsList.filter { it.appName.lowercase().startsWith(query) }
            val containsList = allAppsList.filter {
                it.appName.lowercase().contains(query) && !it.appName.lowercase().startsWith(query)
            }
            startsWithList + containsList
        }
        _uiState.value = SearchUiState.Success(filtered)
    }

    fun refreshLockStates() {
        allAppsList.forEach { app ->
            app.isLocked = lockSettingsManager.isAppLocked(app.packageName)
        }
        if (isAppLoadingFinished) {
            emitFilteredApps()
        }
    }
}

sealed class SearchUiState {
    data object Loading : SearchUiState()
    data class Success(val apps: List<AppItem>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
