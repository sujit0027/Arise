package com.arise.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted, checking for active routines...")
            val sharedPrefs = context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
            val activeRoutineId = sharedPrefs.getString("active_routine_id", null)
            
            if (activeRoutineId != null) {
                Log.d("BootReceiver", "Active routine found: $activeRoutineId. Resuming service...")
                try {
                    val serviceIntent = Intent(context, RoutineService::class.java).apply {
                        action = "START"
                        putExtra("routine_id", activeRoutineId)
                    }
                    context.startForegroundService(serviceIntent)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to start RoutineService on boot: ${e.message}")
                }
            }
        }
    }
}
