package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.model.RoutineAlarm
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(alarm: RoutineAlarm) {
        if (!alarm.isEnabled) {
            cancelAlarm(alarm.id)
            return
        }

        val triggerTime = calculateNextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDays)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ROUTINE_NAME, alarm.title)
            putExtra(EXTRA_CONDITION_TEXT, alarm.conditionText)
            putExtra(EXTRA_GAP_INTERVAL, alarm.gapIntervalMinutes)
            putExtra(EXTRA_MAX_REPEATS, alarm.maxRepeats)
            putExtra(EXTRA_WALLPAPER_TYPE, alarm.wallpaperType)
            putExtra(EXTRA_CUSTOM_WALLPAPER_URI, alarm.customWallpaperUri)
            putExtra(EXTRA_OVERLAY_OPACITY, alarm.overlayOpacity)
            putExtra(EXTRA_BLUR_INTENSITY, alarm.blurIntensity)
            putExtra(EXTRA_RINGTONE_URI, alarm.ringtoneUri)
            putExtra(EXTRA_IS_VIBRATE, alarm.isVibrate)
            putExtra(EXTRA_STRICT_CASE, alarm.strictCaseMatching)
            putExtra(EXTRA_ATTEMPT_COUNT, 1)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d("AlarmScheduler", "Scheduled alarm ${alarm.id} (${alarm.title}) for triggerTime $triggerTime")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Exact alarm permission error", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun scheduleRepeatGap(alarmId: Long, routineName: String, conditionText: String, gapMinutes: Int, currentAttempt: Int, maxRepeats: Int, wallpaperType: String, customWallpaperUri: String?, opacity: Float, blur: Float, ringtone: String, isVibrate: Boolean, strictCase: Boolean) {
        if (currentAttempt > maxRepeats) {
            Log.d("AlarmScheduler", "Reached max repeats ($maxRepeats) for alarm $alarmId")
            return
        }

        val triggerTime = System.currentTimeMillis() + (gapMinutes * 60 * 1000L)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ROUTINE_NAME, routineName)
            putExtra(EXTRA_CONDITION_TEXT, conditionText)
            putExtra(EXTRA_GAP_INTERVAL, gapMinutes)
            putExtra(EXTRA_MAX_REPEATS, maxRepeats)
            putExtra(EXTRA_WALLPAPER_TYPE, wallpaperType)
            putExtra(EXTRA_CUSTOM_WALLPAPER_URI, customWallpaperUri)
            putExtra(EXTRA_OVERLAY_OPACITY, opacity)
            putExtra(EXTRA_BLUR_INTENSITY, blur)
            putExtra(EXTRA_RINGTONE_URI, ringtone)
            putExtra(EXTRA_IS_VIBRATE, isVibrate)
            putExtra(EXTRA_STRICT_CASE, strictCase)
            putExtra(EXTRA_ATTEMPT_COUNT, currentAttempt)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId * 1000 + currentAttempt).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error scheduling repeat gap", e)
        }
    }

    fun cancelAlarm(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun calculateNextTriggerMillis(hour: Int, minute: Int, repeatDaysString: String): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val selectedDaysList = parseRepeatDays(repeatDaysString)

        if (selectedDaysList.isEmpty()) {
            // One-off alarm: if today time is past, set for tomorrow
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        // Repeating days: 0=Sun, 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat
        // Calendar days: SUNDAY=1, MONDAY=2, ... SATURDAY=7
        while (true) {
            val calendarDayIndex = target.get(Calendar.DAY_OF_WEEK) // 1 to 7
            val customDayIndex = when (calendarDayIndex) {
                Calendar.SUNDAY -> 0
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                else -> 0
            }

            if (selectedDaysList.contains(customDayIndex) && target.after(now)) {
                return target.timeInMillis
            }
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ROUTINE_NAME = "extra_routine_name"
        const val EXTRA_CONDITION_TEXT = "extra_condition_text"
        const val EXTRA_GAP_INTERVAL = "extra_gap_interval"
        const val EXTRA_MAX_REPEATS = "extra_max_repeats"
        const val EXTRA_WALLPAPER_TYPE = "extra_wallpaper_type"
        const val EXTRA_CUSTOM_WALLPAPER_URI = "extra_custom_wallpaper_uri"
        const val EXTRA_OVERLAY_OPACITY = "extra_overlay_opacity"
        const val EXTRA_BLUR_INTENSITY = "extra_blur_intensity"
        const val EXTRA_RINGTONE_URI = "extra_ringtone_uri"
        const val EXTRA_IS_VIBRATE = "extra_is_vibrate"
        const val EXTRA_STRICT_CASE = "extra_strict_case"
        const val EXTRA_ATTEMPT_COUNT = "extra_attempt_count"

        fun parseRepeatDays(daysStr: String): List<Int> {
            if (daysStr.isBlank()) return emptyList()
            return daysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
    }
}
