package com.example.data.db

import androidx.room.*
import com.example.data.model.RoutineAlarm
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineAlarmDao {
    @Query("SELECT * FROM routine_alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<RoutineAlarm>>

    @Query("SELECT * FROM routine_alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): RoutineAlarm?

    @Query("SELECT * FROM routine_alarms WHERE isEnabled = 1")
    suspend fun getEnabledAlarms(): List<RoutineAlarm>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: RoutineAlarm): Long

    @Update
    suspend fun updateAlarm(alarm: RoutineAlarm)

    @Delete
    suspend fun deleteAlarm(alarm: RoutineAlarm)

    @Query("UPDATE routine_alarms SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setAlarmEnabled(id: Long, isEnabled: Boolean)

    @Query("UPDATE routine_alarms SET completedCount = completedCount + 1 WHERE id = :id")
    suspend fun incrementCompletedCount(id: Long)
}
