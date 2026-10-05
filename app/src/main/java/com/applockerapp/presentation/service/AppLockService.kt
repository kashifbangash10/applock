package com.applockerapp.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.applockerapp.presentation.MainActivity
import com.applockerapp.utils.LockSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockService : LifecycleService() {

    companion object {
        private const val TAG = "AppLockService"
        private const val NOTIFICATION_ID = 8123
        private const val CHANNEL_ID = "applocker_protection_channel"
        private const val MONITOR_INTERVAL_MS = 450L

        // Static set shared across instances
        val temporarilyUnlockedApps = HashSet<String>()
    }

    private lateinit var lockSettingsManager: LockSettingsManager
    private lateinit var lockOverlayWindow: LockOverlayWindow
    private var monitorJob: Job? = null
    private var lastForegroundPackage: String? = null

    override fun onCreate() {
        android.util.Log.d("AppLockDebug", "AppLockService onCreate called")
        super.onCreate()
        lockSettingsManager = LockSettingsManager(this)

        lockOverlayWindow = LockOverlayWindow(this, this) { unlockedPkg ->
            temporarilyUnlockedApps.add(unlockedPkg)
            Log.d(TAG, "App temporarily unlocked: $unlockedPkg")
        }

        createNotificationChannel()
        startForegroundServiceNotification()
        startMonitoring()
        android.util.Log.d("AppLockDebug", "AppLockService onCreate completed successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("AppLockDebug", "AppLockService onStartCommand called")
        super.onStartCommand(intent, flags, startId)
        startForegroundServiceNotification()
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        android.util.Log.d("AppLockDebug", "AppLockService: Creating and showing foreground notification")
        try {
            val openIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, "applocker_protection_channel_v2")
                .setContentTitle("AppLocker Active 🔒")
                .setContentText("Your apps are protected.")
                .setSmallIcon(android.R.drawable.ic_secure)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } catch (e: Exception) {
                        android.util.Log.w("AppLockDebug", "startForeground with SPECIAL_USE failed, falling back to 2-arg startForeground", e)
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppLockDebug", "Critical fallback startForeground call", e)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (fallbackEx: Exception) {
                    android.util.Log.e("AppLockDebug", "Fatal exception starting foreground service", fallbackEx)
                }
            }
            android.util.Log.d("AppLockDebug", "AppLockService: startForeground completed")
        } catch (e: Exception) {
            android.util.Log.e("AppLockDebug", "AppLockService: Exception building notification", e)
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                try {
                    val currentTopPkg = getForegroundPackage()
                    if (currentTopPkg != null) {
                        withContext(Dispatchers.Main) {
                            handleAppTransition(currentTopPkg)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop: ${e.message}")
                }
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun isPackageLocked(pkgName: String): Boolean {
        return lockSettingsManager.isAppLocked(pkgName)
    }

    private fun isHomeOrSystem(pkgName: String): Boolean {
        if (pkgName == "com.android.systemui" || pkgName == "android") return true
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        val homePkg = resolveInfo?.activityInfo?.packageName
        return homePkg == pkgName
    }

    private fun handleAppTransition(pkgName: String) {
        // If master lock is disabled, hide overlay if showing and do nothing
        if (!lockSettingsManager.isMasterLockEnabled()) {
            if (lockOverlayWindow.isShowing()) {
                lockOverlayWindow.hide()
            }
            return
        }

        // If user is inside AppLocker itself
        if (pkgName == packageName) {
            // Keep overlay showing if it was already active for another package (e.g. Biometric prompt)
            if (lockOverlayWindow.isShowing()) {
                return
            }
        }

        // Handle Home Screen / Launcher transitions
        if (isHomeOrSystem(pkgName)) {
            if (lockOverlayWindow.isShowing()) {
                lockOverlayWindow.hide()
            }
            if (lastForegroundPackage != null && lastForegroundPackage != pkgName) {
                temporarilyUnlockedApps.clear()
                Log.d(TAG, "User returned to Home/System. Cleared temp unlocked apps.")
            }
            lastForegroundPackage = pkgName
            return
        }

        if (pkgName == lastForegroundPackage) {
            // Same app still in foreground — check if overlay should show again
            if (isPackageLocked(pkgName) &&
                !temporarilyUnlockedApps.contains(pkgName) &&
                !lockOverlayWindow.isShowing()
            ) {
                lockOverlayWindow.show(pkgName)
            }
            return
        }

        // App transition happened — remove previous app's temp unlock if going to different app
        val prevPkg = lastForegroundPackage
        if (prevPkg != null && pkgName != prevPkg) {
            temporarilyUnlockedApps.remove(prevPkg)
            Log.d(TAG, "Removed temp unlock for: $prevPkg (user switched to $pkgName)")
        }

        lastForegroundPackage = pkgName

        if (isPackageLocked(pkgName)) {
            if (!temporarilyUnlockedApps.contains(pkgName)) {
                Log.d(TAG, "Showing lock overlay for: $pkgName")
                if (lockOverlayWindow.isShowing()) {
                    lockOverlayWindow.hide()
                }
                lockOverlayWindow.show(pkgName)
            } else {
                Log.d(TAG, "App temporarily unlocked, dismissing overlay: $pkgName")
                if (lockOverlayWindow.isShowing()) {
                    lockOverlayWindow.hide()
                }
            }
        } else {
            // Not a locked app — hide overlay if showing
            if (lockOverlayWindow.isShowing()) {
                lockOverlayWindow.hide()
            }
        }
    }

    private fun getForegroundPackage(): String? {
        // Check overlay permission first
        if (!Settings.canDrawOverlays(this)) return null

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // Query the last 10 seconds for accurate detection
        val events = usm.queryEvents(now - 10000, now)
        val event = UsageEvents.Event()
        var lastResumedPkg: String? = null
        var lastResumedTime: Long = 0

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp >= lastResumedTime) {
                    lastResumedTime = event.timeStamp
                    lastResumedPkg = event.packageName
                }
            }
        }

        return lastResumedPkg
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "applocker_protection_channel_v2",
                "AppLocker Protection",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Background app protection service"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        if (lockOverlayWindow.isShowing()) {
            lockOverlayWindow.hide()
        }
        Log.d(TAG, "AppLockService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
