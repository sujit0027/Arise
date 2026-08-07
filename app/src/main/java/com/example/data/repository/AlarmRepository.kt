package com.example.data.repository

import com.example.data.db.RoutineAlarmDao
import com.example.data.db.WakeupLogDao
import com.example.data.model.RoutineAlarm
import com.example.data.model.WakeupLog
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val routineAlarmDao: RoutineAlarmDao,
    private val wakeupLogDao: WakeupLogDao
) {
    val allAlarms: Flow<List<RoutineAlarm>> = routineAlarmDao.getAllAlarms()
    val allLogs: Flow<List<WakeupLog>> = wakeupLogDao.getAllLogs()
    val totalWakeupCount: Flow<Int> = wakeupLogDao.getTotalWakeupCount()
    val avgDurationSeconds: Flow<Float?> = wakeupLogDao.getAverageDurationSeconds()

    suspend fun getAlarmById(id: Long): RoutineAlarm? = routineAlarmDao.getAlarmById(id)

    suspend fun insertAlarm(alarm: RoutineAlarm): Long = routineAlarmDao.insertAlarm(alarm)

    suspend fun updateAlarm(alarm: RoutineAlarm) = routineAlarmDao.updateAlarm(alarm)

    suspend fun deleteAlarm(alarm: RoutineAlarm) = routineAlarmDao.deleteAlarm(alarm)

    suspend fun setAlarmEnabled(id: Long, isEnabled: Boolean) = routineAlarmDao.setAlarmEnabled(id, isEnabled)

    suspend fun logSuccessfulWakeup(
        alarmId: Long,
        routineName: String,
        durationSeconds: Int,
        repeatCount: Int,
        conditionTextMatched: String
    ) {
        wakeupLogDao.insertLog(
            WakeupLog(
                alarmId = alarmId,
                routineName = routineName,
                durationSeconds = durationSeconds,
                repeatCount = repeatCount,
                conditionTextMatched = conditionTextMatched
            )
        )
        routineAlarmDao.incrementCompletedCount(alarmId)
    }
}
