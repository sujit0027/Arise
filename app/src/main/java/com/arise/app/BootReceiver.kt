package com.arise.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device reboot completed. Restoring scheduled alarms...")
            val sharedPrefs = context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val keys = sharedPrefs.all.keys
            keys.forEach { key ->
                if (key.startsWith("routine_") && key != "routine_arise_default") {
                    val jsonStr = sharedPrefs.getString(key, null)
                    if (jsonStr != null) {
                        try {
                            val r = RoutineModel.fromJson(jsonStr)
                            if (r.isAutoTriggerEnabled) {
                                Log.d("BootReceiver", "Re-registering auto alarms for routine: ${r.name}")
                                scheduleAutoTrigger(context, alarmManager, r)
                            }
                        } catch (e: Exception) {
                            Log.e("BootReceiver", "Failed to restore routine: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun scheduleAutoTrigger(context: Context, alarmManager: AlarmManager, r: RoutineModel) {
        // 1. Start alarm
        r.autoStartTime?.let { startStr ->
            val parts = startStr.split(":")
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                set(Calendar.MINUTE, parts[1].toInt())
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }
            val startIntent = Intent(context, RoutineReceiver::class.java).apply {
                action = "ACTION_START_ROUTINE"
                putExtra("routine_id", r.id)
            }
            val pendingStart = PendingIntent.getBroadcast(
                context, r.id.hashCode(), startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingStart)
            } catch (e: SecurityException) {
                Log.e("BootReceiver", "SecurityException setting exact alarm: ${e.message}")
            }

            // 1.5 Pre-start warning
            if (r.preStartWarningMinutes > 0) {
                val warningTimeMillis = calendar.timeInMillis - (r.preStartWarningMinutes * 60 * 1000)
                if (warningTimeMillis > System.currentTimeMillis()) {
                    val warningIntent = Intent(context, RoutineReceiver::class.java).apply {
                        action = "ACTION_PRE_START_WARNING"
                        putExtra("routine_name", r.name)
                        putExtra("minutes_before", r.preStartWarningMinutes)
                    }
                    val pendingWarning = PendingIntent.getBroadcast(
                        context, r.id.hashCode() + 2, warningIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    try {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningTimeMillis, pendingWarning)
                    } catch (e: SecurityException) {
                        // Ignore
                    }
                }
            }
        }

        // 2. End alarm
        r.autoEndTime?.let { endStr ->
            val parts = endStr.split(":")
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                set(Calendar.MINUTE, parts[1].toInt())
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }
            val stopIntent = Intent(context, RoutineReceiver::class.java).apply {
                action = "ACTION_STOP_ROUTINE"
                putExtra("routine_id", r.id) // needed for daily reschedule
            }
            val pendingStop = PendingIntent.getBroadcast(
                context, r.id.hashCode() + 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingStop)
            } catch (e: SecurityException) {
                // Ignore
            }
        }
    }
}
