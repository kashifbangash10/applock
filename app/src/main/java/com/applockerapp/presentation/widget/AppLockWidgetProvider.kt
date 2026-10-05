package com.applockerapp.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.applockerapp.R
import com.applockerapp.utils.LockSettingsManager

class AppLockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_UPDATE_STATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, AppLockWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        const val ACTION_WIDGET_UPDATE_STATE = "com.applockerapp.widget.UPDATE_STATE"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val lockSettingsManager = LockSettingsManager(context)
            val isEnabled = lockSettingsManager.isMasterLockEnabled()

            val views = RemoteViews(context.packageName, R.layout.widget_app_lock)

            if (isEnabled) {
                views.setImageViewResource(R.id.imgWidgetIcon, R.drawable.ic_lock_closed)
                views.setInt(R.id.imgWidgetIcon, "setColorFilter", 0xFF1E88E5.toInt()) // Brand primary color
                views.setTextViewText(R.id.tvWidgetStatus, "Protection Active 🔒")
                views.setTextViewText(R.id.tvWidgetAction, "Tap to Pause")
            } else {
                views.setImageViewResource(R.id.imgWidgetIcon, R.drawable.ic_lock_open)
                views.setInt(R.id.imgWidgetIcon, "setColorFilter", 0xFFE53935.toInt()) // Red warning color
                views.setTextViewText(R.id.tvWidgetStatus, "Protection Paused 🔓")
                views.setTextViewText(R.id.tvWidgetAction, "Tap to Resume")
            }

            // Click PendingIntent leads to WidgetAuthActivity
            val clickIntent = Intent(context, WidgetAuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetContainer, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun triggerUpdate(context: Context) {
            val updateIntent = Intent(context, AppLockWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_UPDATE_STATE
            }
            context.sendBroadcast(updateIntent)
        }
    }
}
