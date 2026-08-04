package com.arise.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.WallpaperManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.InputStream
import java.util.Calendar

class RoutineService : Service() {

    private val CHANNEL_ID = "AriseRoutineChannel"
    private val NOTIFICATION_ID = 1001
    
    private var activeRoutine: RoutineModel? = null
    private var handler = Handler(Looper.getMainLooper())
    private var screenReceiver: ScreenReceiver? = null
    private var isMonitoring = false

    private val appCheckRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                checkDurationExpiry()
                checkForegroundApp()
                handler.postDelayed(this, 1000) // check every second
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Dynamically register ScreenReceiver for SCREEN_ON events (Mandatory in Android 8.0+)
        screenReceiver = ScreenReceiver()
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        Log.d("RoutineService", "Service created and ScreenReceiver registered.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val routineId = intent?.getStringExtra("routine_id")

        Log.d("RoutineService", "onStartCommand action: $action, routineId: $routineId")

        if (action == "STOP") {
            stopActiveRoutine()
            stopSelf()
            return START_NOT_STICKY
        }

        if (routineId != null) {
            val sharedPrefs = getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
            val routineJson = sharedPrefs.getString("routine_$routineId", null)
            if (routineJson != null) {
                try {
                    val routine = RoutineModel.fromJson(routineJson)
                    startRoutine(routine)
                } catch (e: Exception) {
                    Log.e("RoutineService", "Failed to start routine: ${e.message}")
                }
            }
        }

        return START_STICKY
    }

    private fun startRoutine(routine: RoutineModel) {
        // If there's an existing routine, stop it first
        if (activeRoutine != null) {
            resetWallpaper()
        }

        activeRoutine = routine
        routine.isActive = true
        
        // Save state in prefs
        val sharedPrefs = getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
        val endTime = if (routine.isTimerEnabled && routine.durationMinutes > 0) {
            System.currentTimeMillis() + (routine.durationMinutes * 60 * 1000)
        } else {
            -1L
        }

        sharedPrefs.edit().apply {
            putString("active_routine_id", routine.id)
            putString("routine_${routine.id}", routine.toJsonObject().toString())
            putLong("active_routine_end_time", endTime)
            apply()
        }

        // 1. Build and show Foreground Notification
        val notification = createNotification(routine.name)
        startForeground(NOTIFICATION_ID, notification)

        // 2. Set custom wallpaper if specified and enabled
        if (routine.isWallpaperEnabled) {
            routine.wallpaperUri?.let { uriStr ->
                applyWallpaper(uriStr)
            }
        }

        // Play alerts if enabled
        playStartStopAlert(routine)

        // Update home screen widgets
        updateWidgets()

        // Schedule Wake Up Alarm if configured and enabled
        if (routine.isWakeAlarmEnabled && routine.wakeAlarmTime != null) {
            scheduleWakeAlarm(routine)
        }

        // 3. Always start monitoring loop to check timer expiry and restricted apps
        isMonitoring = true
        handler.removeCallbacks(appCheckRunnable)
        handler.post(appCheckRunnable)
        Log.d("RoutineService", "App monitoring and timer started for routine: ${routine.name}")
    }

    private fun scheduleWakeAlarm(routine: RoutineModel) {
        val timeStr = routine.wakeAlarmTime ?: return
        val parts = timeStr.split(":")
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(Calendar.MINUTE, parts[1].toInt())
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, RoutineReceiver::class.java).apply {
            action = "ACTION_WAKE_ALARM"
        }
        val pendingAlarm = PendingIntent.getBroadcast(
            this, routine.id.hashCode() + 3, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingAlarm)
            Log.d("RoutineService", "Scheduled wake-up alarm for ${routine.name} at $timeStr")
        } catch (e: Exception) {
            Log.e("RoutineService", "Failed to schedule wake-up alarm: ${e.message}")
        }
    }

    private fun cancelWakeAlarm(routineId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, RoutineReceiver::class.java).apply {
            action = "ACTION_WAKE_ALARM"
        }
        val pendingAlarm = PendingIntent.getBroadcast(
            this, routineId.hashCode() + 3, alarmIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingAlarm?.let { alarmManager.cancel(it) }
    }

    private fun playStartStopAlert(routine: RoutineModel) {
        if (!routine.isSoundAlertEnabled) return
        try {
            // Vibrate
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
            // Sound
            val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val r = android.media.RingtoneManager.getRingtone(applicationContext, notificationUri)
            r.play()
        } catch (e: Exception) {
            Log.e("RoutineService", "Failed to play start/stop alert: ${e.message}")
        }
    }

    private fun updateWidgets() {
        try {
            val widgetIntent = Intent(this, RoutineWidgetProvider::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = android.appwidget.AppWidgetManager.getInstance(applicationContext).getAppWidgetIds(
                android.content.ComponentName(applicationContext, RoutineWidgetProvider::class.java)
            )
            widgetIntent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            sendBroadcast(widgetIntent)
            Log.d("RoutineService", "Sent update broadcast to widget provider.")
        } catch (e: Exception) {
            Log.e("RoutineService", "Failed to update widgets: ${e.message}")
        }
    }

    private fun checkDurationExpiry() {
        val routine = activeRoutine ?: return
        if (routine.isTimerEnabled && routine.durationMinutes > 0) {
            val sharedPrefs = getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
            val endTime = sharedPrefs.getLong("active_routine_end_time", -1L)
            if (endTime > 0 && System.currentTimeMillis() >= endTime) {
                Log.d("RoutineService", "Routine duration expired. Stopping...")
                stopActiveRoutine()
                stopSelf()
            }
        }
    }

    private fun stopActiveRoutine() {
        val sharedPrefs = getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
        val activeId = sharedPrefs.getString("active_routine_id", null)
        var soundEnabled = false
        
        if (activeId != null) {
            val routineJson = sharedPrefs.getString("routine_$activeId", null)
            if (routineJson != null) {
                try {
                    val routine = RoutineModel.fromJson(routineJson)
                    routine.isActive = false
                    soundEnabled = routine.isSoundAlertEnabled
                    sharedPrefs.edit().apply {
                        putString("routine_$activeId", routine.toJsonObject().toString())
                        remove("active_routine_id")
                        remove("active_routine_end_time")
                        apply()
                    }
                } catch (e: Exception) {
                    Log.e("RoutineService", "Error stopping routine: ${e.message}")
                }
            }
        }

        isMonitoring = false
        handler.removeCallbacks(appCheckRunnable)
        
        // Reset wallpaper if it was enabled
        activeRoutine?.let {
            if (it.isWallpaperEnabled) {
                resetWallpaper()
            }
            if (soundEnabled) {
                playStartStopAlert(it)
            }
            // Cancel wake alarm since routine stopped
            cancelWakeAlarm(it.id)
        }
        
        activeRoutine = null
        
        // Update widgets
        updateWidgets()
        
        Log.d("RoutineService", "Routine stopped and settings reset.")
    }

    private fun applyWallpaper(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            val bitmap = uriToBitmap(this, uri)
            if (bitmap != null) {
                val wm = WallpaperManager.getInstance(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Set both home and lock screen wallpaper
                    wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                } else {
                    wm.setBitmap(bitmap)
                }
                Log.d("RoutineService", "Wallpaper applied successfully.")
            } else {
                Log.e("RoutineService", "Decoded bitmap was null.")
            }
        } catch (e: Exception) {
            Log.e("RoutineService", "Error setting wallpaper: ${e.message}")
        }
    }

    private fun resetWallpaper() {
        val sharedPrefs = getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
        val defaultUriStr = sharedPrefs.getString("default_wallpaper_uri", null)
        
        if (defaultUriStr != null) {
            Log.d("RoutineService", "Resetting wallpaper to custom default: $defaultUriStr")
            applyWallpaper(defaultUriStr)
        } else {
            Log.d("RoutineService", "No custom default wallpaper configured. Clearing wallpaper to system default.")
            try {
                WallpaperManager.getInstance(this).clear()
            } catch (e: Exception) {
                Log.e("RoutineService", "Error clearing wallpaper: ${e.message}")
            }
        }
    }

    private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(resolver, uri)
            }
        } catch (e: Exception) {
            Log.e("RoutineService", "uriToBitmap failed: ${e.message}")
            null
        }
    }

    private fun checkForegroundApp() {
        val routine = activeRoutine ?: return
        if (!routine.isAppBlockEnabled || routine.blockedApps.isEmpty()) return

        val foregroundApp = getForegroundPackage(this)
        if (foregroundApp != null && routine.blockedApps.contains(foregroundApp)) {
            Log.d("RoutineService", "Blocked app detected in foreground: $foregroundApp. Launching BlockerActivity...")
            val blockIntent = Intent(this, BlockerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(blockIntent)
        }
    }

    private fun getForegroundPackage(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 5, time)
        if (!stats.isNullOrEmpty()) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            return sortedStats.firstOrNull()?.packageName
        }
        return null
    }

    private fun createNotification(routineName: String): Notification {
        val stopIntent = Intent(this, RoutineService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Arise Mode Active")
            .setContentText("Routine \"$routineName\" is running.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Mode", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Arise Routine Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notifications when a focus routine is active."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isMonitoring = false
        handler.removeCallbacks(appCheckRunnable)
        
        // Unregister ScreenReceiver
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        Log.d("RoutineService", "Service destroyed and resources cleared.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
