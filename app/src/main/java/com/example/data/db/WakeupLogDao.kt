package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.WakeupLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WakeupLogDao {
    @Query("SELECT * FROM wakeup_logs ORDER BY completedAt DESC")
    fun getAllLogs(): Flow<List<WakeupLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: WakeupLog): Long

    @Query("SELECT COUNT(*) FROM wakeup_logs")
    fun getTotalWakeupCount(): Flow<Int>

    @Query("SELECT AVG(durationSeconds) FROM wakeup_logs")
    fun getAverageDurationSeconds(): Flow<Float?>
}
