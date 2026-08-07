package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wakeup_logs")
data class WakeupLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alarmId: Long,
    val routineName: String,
    val completedAt: Long = System.currentTimeMillis(),
    val durationSeconds: Int,
    val repeatCount: Int = 1,
    val conditionTextMatched: String
)
