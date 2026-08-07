package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val routineName = intent.getStringExtra(AlarmScheduler.EXTRA_ROUTINE_NAME) ?: "Study Routine"
        val conditionText = intent.getStringExtra(AlarmScheduler.EXTRA_CONDITION_TEXT) ?: "I am awake and ready to study"
        val gapInterval = intent.getIntExtra(AlarmScheduler.EXTRA_GAP_INTERVAL, 2)
        val maxRepeats = intent.getIntExtra(AlarmScheduler.EXTRA_MAX_REPEATS, 5)
        val wallpaperType = intent.getStringExtra(AlarmScheduler.EXTRA_WALLPAPER_TYPE) ?: "preset_sunrise"
        val customWallpaperUri = intent.getStringExtra(AlarmScheduler.EXTRA_CUSTOM_WALLPAPER_URI)
        val overlayOpacity = intent.getFloatExtra(AlarmScheduler.EXTRA_OVERLAY_OPACITY, 0.5f)
        val blurIntensity = intent.getFloatExtra(AlarmScheduler.EXTRA_BLUR_INTENSITY, 10f)
        val ringtoneUri = intent.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_URI) ?: "default"
        val isVibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_VIBRATE, true)
        val strictCase = intent.getBooleanExtra(AlarmScheduler.EXTRA_STRICT_CASE, false)
        val attemptCount = intent.getIntExtra(AlarmScheduler.EXTRA_ATTEMPT_COUNT, 1)

        // Play alarm sound and vibration
        RingtonePlayerManager.getInstance(context).startAlarmRingtone(ringtoneUri, isVibrate)

        // Build full screen ringing intent
        val ringingIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("action", "RINGING_CHALLENGE")
            putExtra("alarmId", alarmId)
            putExtra("routineName", routineName)
            putExtra("conditionText", conditionText)
            putExtra("gapInterval", gapInterval)
            putExtra("maxRepeats", maxRepeats)
            putExtra("wallpaperType", wallpaperType)
            putExtra("customWallpaperUri", customWallpaperUri)
            putExtra("overlayOpacity", overlayOpacity)
            putExtra("blurIntensity", blurIntensity)
            putExtra("strictCase", strictCase)
            putExtra("attemptCount", attemptCount)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            ringingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Routine Alarm Ringing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alarms for strict routine enforcement"
                setBypassDnd(true)
                enableVibration(isVibrate)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ ROUTINE ALARM: $routineName")
            .setContentText("Complete the text challenge to stop: '$conditionText'")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        notificationManager.notify(alarmId.toInt(), notification)

        // Launch activity directly
        try {
            context.startActivity(ringingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val CHANNEL_ID = "routine_guard_alarm_channel"
    }
}
