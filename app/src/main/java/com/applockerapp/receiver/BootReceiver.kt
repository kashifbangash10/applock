package com.applockerapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.applockerapp.presentation.service.AppLockService
import com.applockerapp.utils.LockSettingsManager

/**
 * Auto-starts the AppLockService after device reboots.
 * Requires RECEIVE_BOOT_COMPLETED permission (already declared in manifest).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        Log.d(TAG, "Boot completed — checking if AppLockService should start")

        val lockSettingsManager = LockSettingsManager(context)

        // Only start service if master lock is enabled
        if (!lockSettingsManager.isMasterLockEnabled()) {
            Log.d(TAG, "Master lock is OFF — not starting service after boot")
            return
        }

        Log.d(TAG, "Starting AppLockService after boot")
        val serviceIntent = Intent(context, AppLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
