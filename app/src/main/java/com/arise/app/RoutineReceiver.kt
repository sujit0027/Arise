package com.arise.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class RoutineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val routineId = intent.getStringExtra("routine_id")
        val routineName = intent.getStringExtra("routine_name") ?: "Arise Mode"
        val minutesBefore = intent.getIntExtra("minutes_before", 5)
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
            // Reschedule start alarm for NEXT DAY (daily repetition)
            rescheduleStartAlarm(context, routineId)
            
        } else if (action == "ACTION_STOP_ROUTINE") {
            val serviceIntent = Intent(context, RoutineService::class.java).apply {
                this.action = "STOP"
            }
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e("RoutineReceiver", "Failed to stop service from alarm: ${e.message}")
            }
            // Reschedule stop alarm for NEXT DAY if routineId known
            if (routineId != null) {
                rescheduleStopAlarm(context, routineId)
            }
        } else if (action == "ACTION_PRE_START_WARNING") {
            showPreStartNotification(context, routineName, minutesBefore)
            // Reschedule pre-start warning for NEXT DAY
            if (routineId != null) {
                rescheduleWarningAlarm(context, routineId, routineName, minutesBefore)
            }
        } else if (action == "ACTION_WAKE_ALARM") {
            val routineId = intent.getStringExtra("routine_id")
            showWakeAlarmNotification(context, routineId)
        }
    }

    private fun rescheduleStartAlarm(context: Context, routineId: String) {
        val sharedPrefs = context.getSharedPreferences("ArisePrefs", android.content.Context.MODE_PRIVATE)
        val routineJson = sharedPrefs.getString("routine_$routineId", null) ?: return
        val routine = try { RoutineModel.fromJson(routineJson) } catch (e: Exception) { return }
        if (!routine.isAutoTriggerEnabled || routine.autoStartTime == null) return

        val parts = routine.autoStartTime!!.split(":")
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(java.util.Calendar.MINUTE, parts[1].toInt())
            set(java.util.Calendar.SECOND, 0)
            add(java.util.Calendar.DATE, 1) // always next day
        }
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val startIntent = Intent(context, RoutineReceiver::class.java).apply {
            action = "ACTION_START_ROUTINE"
            putExtra("routine_id", routineId)
        }
        val pending = android.app.PendingIntent.getBroadcast(
            context, routineId.hashCode(), startIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            Log.d("RoutineReceiver", "Rescheduled start alarm for next day: ${routine.autoStartTime}")
        } catch (e: Exception) { Log.e("RoutineReceiver", "Reschedule start failed: ${e.message}") }
    }

    private fun rescheduleStopAlarm(context: Context, routineId: String) {
        val sharedPrefs = context.getSharedPreferences("ArisePrefs", android.content.Context.MODE_PRIVATE)
        val routineJson = sharedPrefs.getString("routine_$routineId", null) ?: return
        val routine = try { RoutineModel.fromJson(routineJson) } catch (e: Exception) { return }
        if (!routine.isAutoTriggerEnabled || routine.autoEndTime == null) return

        val parts = routine.autoEndTime!!.split(":")
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(java.util.Calendar.MINUTE, parts[1].toInt())
            set(java.util.Calendar.SECOND, 0)
            add(java.util.Calendar.DATE, 1) // always next day
        }
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val stopIntent = Intent(context, RoutineReceiver::class.java).apply {
            action = "ACTION_STOP_ROUTINE"
            putExtra("routine_id", routineId)
        }
        val pending = android.app.PendingIntent.getBroadcast(
            context, routineId.hashCode() + 1, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            Log.d("RoutineReceiver", "Rescheduled stop alarm for next day: ${routine.autoEndTime}")
        } catch (e: Exception) { Log.e("RoutineReceiver", "Reschedule stop failed: ${e.message}") }
    }

    private fun rescheduleWarningAlarm(context: Context, routineId: String, name: String, minutesBefore: Int) {
        val sharedPrefs = context.getSharedPreferences("ArisePrefs", android.content.Context.MODE_PRIVATE)
        val routineJson = sharedPrefs.getString("routine_$routineId", null) ?: return
        val routine = try { RoutineModel.fromJson(routineJson) } catch (e: Exception) { return }
        if (!routine.isAutoTriggerEnabled || routine.autoStartTime == null || routine.preStartWarningMinutes <= 0) return

        val parts = routine.autoStartTime!!.split(":")
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(java.util.Calendar.MINUTE, parts[1].toInt())
            set(java.util.Calendar.SECOND, 0)
            add(java.util.Calendar.DATE, 1)
        }
        val warningMillis = calendar.timeInMillis - (minutesBefore * 60 * 1000)
        if (warningMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val warningIntent = Intent(context, RoutineReceiver::class.java).apply {
            action = "ACTION_PRE_START_WARNING"
            putExtra("routine_id", routineId)
            putExtra("routine_name", name)
            putExtra("minutes_before", minutesBefore)
        }
        val pending = android.app.PendingIntent.getBroadcast(
            context, routineId.hashCode() + 2, warningIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, warningMillis, pending)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun showWakeAlarmNotification(context: Context, routineId: String?) {
        var ringtoneUri: android.net.Uri? = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        var isSilent = false
        if (routineId != null) {
            val sharedPrefs = context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
            val routineJson = sharedPrefs.getString("routine_$routineId", null)
            if (routineJson != null) {
                try {
                    val routine = RoutineModel.fromJson(routineJson)
                    if (routine.ringtoneUri == "silent") {
                        isSilent = true
                        ringtoneUri = null
                    } else if (routine.ringtoneUri != null) {
                        ringtoneUri = android.net.Uri.parse(routine.ringtoneUri)
                    }
                } catch (e: Exception) {}
            }
        }
        
        val channelId = "AriseWakeAlarmChannel_${ringtoneUri?.hashCode() ?: "silent"}"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "Arise Wake Up Alarms", 
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fires wake-up alarms at the end of routines."
                if (isSilent || ringtoneUri == null) {
                    setSound(null, null)
                } else {
                    setSound(
                        ringtoneUri,
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Wake Up Alert!")
            .setContentText("Your sleep routine has ended. Time to wake up!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2003, notification)
    }

    private fun showPreStartNotification(context: Context, name: String, minutes: Int) {
        val channelId = "AriseWarningChannel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "Arise Warning Alerts", 
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sends warning alerts before routines start."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Routine Starting Soon")
            .setContentText("\"$name\" will start in $minutes minutes. Wrap up your work!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2002, notification)
    }
}
