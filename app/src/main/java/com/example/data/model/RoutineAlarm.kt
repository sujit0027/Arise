package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routine_alarms")
data class RoutineAlarm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "Study Routine",
    val hour: Int = 7,
    val minute: Int = 0,
    val repeatDays: String = "1,2,3,4,5", // 0=Sun, 1=Mon, ..., 6=Sat
    val isEnabled: Boolean = true,
    val gapIntervalMinutes: Int = 2, // The gap interval if ignored/snoozed
    val maxRepeats: Int = 5, // Maximum number of continuous repeat attempts
    val conditionText: String = "I am awake and ready to study",
    val wallpaperType: String = "preset_sunrise", // preset_sunrise, preset_library, preset_cyber, preset_nordic, custom_uri
    val customWallpaperUri: String? = null,
    val overlayOpacity: Float = 0.5f, // 0.2f to 0.85f
    val blurIntensity: Float = 10f, // 0f to 25f
    val ringtoneUri: String = "default",
    val isVibrate: Boolean = true,
    val strictCaseMatching: Boolean = false, // Ignore case differences by default for smoother wakeup
    val completedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
