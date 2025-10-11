package com.dev.ora.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WidgetUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WidgetUpdateReceiver"
        const val ACTION_DATABASE_CHANGED = "com.dev.ora.DATABASE_CHANGED"

        fun notifyDatabaseChanged(context: Context) {
            // Option 1: Explicit intent with package name (Recommended)
            val intent = Intent(ACTION_DATABASE_CHANGED).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)

            // Alternative Option 2: Direct class reference
            // val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            //     action = ACTION_DATABASE_CHANGED
            // }
            // context.sendBroadcast(intent)

            Log.d(TAG, "Database change notification sent")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            ACTION_DATABASE_CHANGED -> {
                Log.d(TAG, "Database changed, updating widgets")
                CountdownWidgetProvider.forceUpdateAllWidgets(context)
            }
        }
    }
}