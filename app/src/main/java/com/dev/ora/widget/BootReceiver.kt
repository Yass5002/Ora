package com.dev.ora.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    private val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Boot completed, updating widgets")

                // Update widgets after a small delay to ensure system is ready
                bootScope.launch {
                    delay(2000) // Wait 2 seconds for system to stabilize
                    CountdownWidgetProvider.updateAllWidgets(context)
                }

                // Also restart notification service if enabled
                val preferencesManager = com.dev.ora.data.PreferencesManager(context)
                if (preferencesManager.getNotificationsEnabled()) {
                    com.dev.ora.notification.CountdownNotificationService.startOrUpdateService(context)
                }
            }
        }
    }
}