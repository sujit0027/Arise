package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.data.db.AppDatabase
import com.example.data.model.RoutineAlarm
import com.example.data.model.WakeupLog
import com.example.data.repository.AlarmRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AlarmRepository
    private val alarmScheduler: AlarmScheduler

    val alarms: StateFlow<List<RoutineAlarm>>
    val wakeupLogs: StateFlow<List<WakeupLog>>
    val totalWakeupCount: StateFlow<Int>
    val avgDurationSeconds: StateFlow<Float>

    private val _activeRingingAlarm = MutableStateFlow<RoutineAlarm?>(null)
    val activeRingingAlarm: StateFlow<RoutineAlarm?> = _activeRingingAlarm.asStateFlow()

    private val _ringingStartTime = MutableStateFlow<Long>(0L)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = AlarmRepository(db.routineAlarmDao(), db.wakeupLogDao())
        alarmScheduler = AlarmScheduler(application)

        alarms = repository.allAlarms
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        wakeupLogs = repository.allLogs
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        totalWakeupCount = repository.totalWakeupCount
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0
            )

        avgDurationSeconds = repository.avgDurationSeconds
            .map { it ?: 0f }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0f
            )
    }

    fun toggleAlarmEnabled(alarm: RoutineAlarm, isEnabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = isEnabled)
            repository.updateAlarm(updated)
            if (isEnabled) {
                alarmScheduler.scheduleAlarm(updated)
            } else {
                alarmScheduler.cancelAlarm(updated.id)
            }
        }
    }

    fun saveAlarm(alarm: RoutineAlarm) {
        viewModelScope.launch {
            if (alarm.id == 0L) {
                val newId = repository.insertAlarm(alarm)
                val newAlarm = alarm.copy(id = newId)
                if (newAlarm.isEnabled) {
                    alarmScheduler.scheduleAlarm(newAlarm)
                }
            } else {
                repository.updateAlarm(alarm)
                if (alarm.isEnabled) {
                    alarmScheduler.scheduleAlarm(alarm)
                } else {
                    alarmScheduler.cancelAlarm(alarm.id)
                }
            }
        }
    }

    fun deleteAlarm(alarm: RoutineAlarm) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm.id)
            repository.deleteAlarm(alarm)
        }
    }

    fun startTestRinging(alarm: RoutineAlarm) {
        _activeRingingAlarm.value = alarm
        _ringingStartTime.value = System.currentTimeMillis()
    }

    fun setRingingAlarm(alarm: RoutineAlarm) {
        _activeRingingAlarm.value = alarm
        _ringingStartTime.value = System.currentTimeMillis()
    }

    fun completeActiveChallenge() {
        val active = _activeRingingAlarm.value ?: return
        val startTime = _ringingStartTime.value
        val elapsedSec = if (startTime > 0L) ((System.currentTimeMillis() - startTime) / 1000).toInt() else 30

        viewModelScope.launch {
            repository.logSuccessfulWakeup(
                alarmId = active.id,
                routineName = active.title,
                durationSeconds = elapsedSec,
                repeatCount = 1,
                conditionTextMatched = active.conditionText
            )
            _activeRingingAlarm.value = null
            _ringingStartTime.value = 0L
        }
    }

    fun dismissRingingWithoutComplete() {
        _activeRingingAlarm.value = null
        _ringingStartTime.value = 0L
    }
}
