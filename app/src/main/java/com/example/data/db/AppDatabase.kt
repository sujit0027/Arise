package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.RoutineAlarm
import com.example.data.model.WakeupLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [RoutineAlarm::class, WakeupLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineAlarmDao(): RoutineAlarmDao
    abstract fun wakeupLogDao(): WakeupLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "routine_guard_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Populate default study routine alarms
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getDatabase(context).routineAlarmDao()
                            dao.insertAlarm(
                                RoutineAlarm(
                                    title = "Math Practice",
                                    hour = 6,
                                    minute = 30,
                                    repeatDays = "1,2,3,4,5", // Mon-Fri
                                    isEnabled = true,
                                    gapIntervalMinutes = 2,
                                    maxRepeats = 5,
                                    conditionText = "I am awake and ready to study",
                                    wallpaperType = "preset_sunrise",
                                    overlayOpacity = 0.5f,
                                    blurIntensity = 8f
                                )
                            )
                            dao.insertAlarm(
                                RoutineAlarm(
                                    title = "DSA & Algorithms Drill",
                                    hour = 8,
                                    minute = 0,
                                    repeatDays = "1,2,3,4,5,6", // Mon-Sat
                                    isEnabled = true,
                                    gapIntervalMinutes = 3,
                                    maxRepeats = 3,
                                    conditionText = "Consistency is the key to my success",
                                    wallpaperType = "preset_cyber",
                                    overlayOpacity = 0.6f,
                                    blurIntensity = 12f
                                )
                            )
                            dao.insertAlarm(
                                RoutineAlarm(
                                    title = "Evening Deep Focus Session",
                                    hour = 19,
                                    minute = 0,
                                    repeatDays = "0,1,2,3,4,5,6", // Daily
                                    isEnabled = false,
                                    gapIntervalMinutes = 5,
                                    maxRepeats = 3,
                                    conditionText = "I choose my future over comfort today",
                                    wallpaperType = "preset_library",
                                    overlayOpacity = 0.55f,
                                    blurIntensity = 10f
                                )
                            )
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
