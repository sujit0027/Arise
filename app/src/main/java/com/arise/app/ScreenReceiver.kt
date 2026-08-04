package com.arise.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            Log.d("ScreenReceiver", "Screen turned ON, checking for active routines...")
            val sharedPrefs = context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
            val activeRoutineId = sharedPrefs.getString("active_routine_id", null)
            
            if (activeRoutineId != null) {
                val routineJson = sharedPrefs.getString("routine_$activeRoutineId", null)
                if (routineJson != null) {
                    try {
                        val routine = RoutineModel.fromJson(routineJson)
                        if (routine.isActive && routine.isLockScreenOverlayEnabled) {
                            Log.d("ScreenReceiver", "Launching LockScreenActivity over keyguard")
                            val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                            }
                            context.startActivity(lockIntent)
                        }
                    } catch (e: Exception) {
                        Log.e("ScreenReceiver", "Error parsing active routine: ${e.message}")
                    }
                }
            }
        }
    }
}
