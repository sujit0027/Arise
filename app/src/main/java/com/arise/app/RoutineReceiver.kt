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
        } else if (action == "ACTION_STOP_ROUTINE") {
            val serviceIntent = Intent(context, RoutineService::class.java).apply {
                this.action = "STOP"
            }
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e("RoutineReceiver", "Failed to stop service from alarm: ${e.message}")
            }
        } else if (action == "ACTION_PRE_START_WARNING") {
            showPreStartNotification(context, routineName, minutesBefore)
        } else if (action == "ACTION_WAKE_ALARM") {
            showWakeAlarmNotification(context)
        }
    }

    private fun showWakeAlarmNotification(context: Context) {
        val channelId = "AriseWakeAlarmChannel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "Arise Wake Up Alarms", 
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fires wake-up alarms at the end of routines."
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
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
