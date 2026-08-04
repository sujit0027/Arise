package com.arise.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class RoutineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val routineId = intent.getStringExtra("routine_id")
        Log.d("RoutineReceiver", "Received broadcast action: $action, routineId: $routineId")

        if (action == "ACTION_START_ROUTINE" && routineId != null) {
            val serviceIntent = Intent(context, RoutineService::class.java).apply {
                this.action = "START"
                putExtra("routine_id", routineId)
            }
            try {
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.e("RoutineReceiver", "Failed to start service from alarm: ${e.message}")
            }
        } else if (action == "ACTION_STOP_ROUTINE") {
            val serviceIntent = Intent(context, RoutineService::class.java).apply {
                this.action = "STOP"
            }
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e("RoutineReceiver", "Failed to stop service from alarm: ${e.message}")
            }
        }
    }
}
