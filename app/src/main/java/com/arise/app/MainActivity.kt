package com.arise.app

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AriseAppDashboard()
            }
        }
    }
}

// Helper to resolve app icons dynamically in Compose
@Composable
fun getAppIconBitmap(context: Context, packageName: String): ImageBitmap? {
    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                iconBitmap = bitmap.asImageBitmap()
            } catch (e: Exception) {
                // Return null on failure
            }
        }
    }
    return iconBitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AriseAppDashboard() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE) }

    // Core states
    val routines = remember { mutableStateListOf<RoutineModel>() }
    var activeRoutineId by remember { mutableStateOf<String?>(null) }
    
    // View state: null = dashboard list view, not null = editing this specific routine
    var editingRoutine by remember { mutableStateOf<RoutineModel?>(null) }
    
    var overlayPermissionGranted by remember { mutableStateOf(false) }
    var usageStatsPermissionGranted by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember { mutableStateOf(false) }
    var defaultWallpaperUri by remember { mutableStateOf<String?>(null) }

    // Dialog / Selection states
    var showCreateDialog by remember { mutableStateOf(false) }
    var newRoutineName by remember { mutableStateOf("") }
    
    var showDurationDialog by remember { mutableStateOf(false) }
    var showAutoTriggerDialog by remember { mutableStateOf(false) }
    var showAppPickerDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var showCustomTargetDialog by remember { mutableStateOf(false) }
    var showCustomMinutesDialog by remember { mutableStateOf(false) }
    
    var customMinutesInput by remember { mutableStateOf("") }
    var customTargetInput by remember { mutableStateOf("") }
    val allApps = remember { mutableStateListOf<AppItem>() }

    fun checkPermissions() {
        overlayPermissionGranted = Settings.canDrawOverlays(context)
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        usageStatsPermissionGranted = mode == AppOpsManager.MODE_ALLOWED

        notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // Refresh and load routines
    fun refreshRoutinesList() {
        val keys = sharedPrefs.all.keys
        routines.clear()
        keys.forEach { key ->
            if (key.startsWith("routine_") && key != "routine_arise_default") {
                val jsonStr = sharedPrefs.getString(key, null)
                if (jsonStr != null) {
                    try {
                        routines.add(RoutineModel.fromJson(jsonStr))
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed parsing routine: ${e.message}")
                    }
                }
            }
        }
        
        // Setup initial default study/focus routines if empty
        if (routines.isEmpty()) {
            val focusRoutine = RoutineModel(
                name = "Study Focus",
                iconResName = "book",
                isTimerEnabled = true,
                durationMinutes = 45,
                customFocusMessage = "Focus on your tasks!",
                isAppBlockEnabled = true,
                isWallpaperEnabled = false
            )
            val sleepRoutine = RoutineModel(
                name = "Sleep Routine",
                iconResName = "bed",
                isSoundAlertEnabled = true,
                isWallpaperEnabled = false
            )
            sharedPrefs.edit().apply {
                putString("routine_${focusRoutine.id}", focusRoutine.toJsonObject().toString())
                putString("routine_${sleepRoutine.id}", sleepRoutine.toJsonObject().toString())
                apply()
            }
            routines.add(focusRoutine)
            routines.add(sleepRoutine)
        }
        // Save focus routine as default for widget compatibility
        val defaultRoutine = routines.firstOrNull()
        if (defaultRoutine != null) {
            sharedPrefs.edit().putString("routine_arise_default", defaultRoutine.toJsonObject().toString()).apply()
        }
    }

    // Load initial states
    LaunchedEffect(Unit) {
        checkPermissions()
        defaultWallpaperUri = sharedPrefs.getString("default_wallpaper_uri", null)
        activeRoutineId = sharedPrefs.getString("active_routine_id", null)
        refreshRoutinesList()
    }

    // Recheck permissions on layout transitions
    LaunchedEffect(editingRoutine, showCreateDialog) {
        checkPermissions()
    }

    // Wallpaper pickers
    val lockScreenWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    editingRoutine?.let { routineObj ->
                        val updated = routineObj.copy(wallpaperUri = it.toString(), isWallpaperEnabled = true)
                        editingRoutine = updated
                        sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                        refreshRoutinesList()
                    }
                    Toast.makeText(context, "Lockscreen wallpaper saved!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read image permissions.", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val defaultWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    defaultWallpaperUri = it.toString()
                    sharedPrefs.edit().putString("default_wallpaper_uri", it.toString()).apply()
                    Toast.makeText(context, "Default Reset wallpaper saved!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read image permissions.", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val requestNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            notificationPermissionGranted = isGranted
        }
    )

    // AlarmManager Trigger Schedules
    fun scheduleAutoTrigger(r: RoutineModel) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // 1. Schedule Auto Start Time
        r.autoStartTime?.let { startStr ->
            val parts = startStr.split(":")
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                set(Calendar.MINUTE, parts[1].toInt())
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }
            val startIntent = Intent(context, RoutineReceiver::class.java).apply {
                action = "ACTION_START_ROUTINE"
                putExtra("routine_id", r.id)
            }
            val pendingStart = PendingIntent.getBroadcast(
                context, r.id.hashCode(), startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingStart)
                Log.d("MainActivity", "Scheduled auto start alarm at: $startStr")
            } catch (e: SecurityException) {
                Toast.makeText(context, "Exact alarm permission required.", Toast.LENGTH_LONG).show()
            }

            // 1.5 Schedule Pre-Start Warning Notification if enabled
            if (r.preStartWarningMinutes > 0) {
                val warningTimeMillis = calendar.timeInMillis - (r.preStartWarningMinutes * 60 * 1000)
                if (warningTimeMillis > System.currentTimeMillis()) {
                    val warningIntent = Intent(context, RoutineReceiver::class.java).apply {
                        action = "ACTION_PRE_START_WARNING"
                        putExtra("routine_name", r.name)
                        putExtra("minutes_before", r.preStartWarningMinutes)
                    }
                    val pendingWarning = PendingIntent.getBroadcast(
                        context, r.id.hashCode() + 2, warningIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    try {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningTimeMillis, pendingWarning)
                        Log.d("MainActivity", "Scheduled pre-start warning alarm ${r.preStartWarningMinutes}m before start")
                    } catch (e: SecurityException) {
                        // Ignore
                    }
                }
            }
        }

        // 2. Schedule Auto End Time
        r.autoEndTime?.let { endStr ->
            val parts = endStr.split(":")
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                set(Calendar.MINUTE, parts[1].toInt())
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }
            val stopIntent = Intent(context, RoutineReceiver::class.java).apply {
                action = "ACTION_STOP_ROUTINE"
            }
            val pendingStop = PendingIntent.getBroadcast(
                context, r.id.hashCode() + 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingStop)
                Log.d("MainActivity", "Scheduled auto stop alarm at: $endStr")
            } catch (e: SecurityException) {
                // Ignore
            }
        }
    }

    fun cancelAutoTrigger(r: RoutineModel) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val startIntent = Intent(context, RoutineReceiver::class.java).apply {
            action = "ACTION_START_ROUTINE"
        }
        val pendingStart = PendingIntent.getBroadcast(
            context, r.id.hashCode(), startIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingStart?.let { alarmManager.cancel(it) }

        val stopIntent = Intent(context, RoutineReceiver::class.java).apply {
            action = "ACTION_STOP_ROUTINE"
        }
        val pendingStop = PendingIntent.getBroadcast(
            context, r.id.hashCode() + 1, stopIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingStop?.let { alarmManager.cancel(it) }

        val warningIntent = Intent(context, RoutineReceiver::class.java).apply {
            action = "ACTION_PRE_START_WARNING"
        }
        val pendingWarning = PendingIntent.getBroadcast(
            context, r.id.hashCode() + 2, warningIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingWarning?.let { alarmManager.cancel(it) }
        Log.d("MainActivity", "Cancelled AlarmManager triggers")
    }

    fun loadInstalledApps() {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val filteredApps = mutableListOf<AppItem>()
        
        apps.forEach { app ->
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null && app.packageName != context.packageName) {
                val label = pm.getApplicationLabel(app).toString()
                val isSelected = editingRoutine?.blockedApps?.contains(app.packageName) ?: false
                filteredApps.add(AppItem(label, app.packageName, isSelected))
            }
        }
        filteredApps.sortBy { it.name }
        allApps.clear()
        allApps.addAll(filteredApps)
    }

    // UI RENDER: DASHBOARD OR EDITOR
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F1014) // Dark AMOLED background
    ) {
        if (editingRoutine == null) {
            // VIEW 1: DASHBOARD ROUTINES LIST
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Title & Add Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Arise",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF3B82F6)
                        )
                        Text(
                            text = "Modes & Automation Clones",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Button(
                        onClick = {
                            newRoutineName = ""
                            showCreateDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "+ Add", fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = Color(0xFF1E2026), modifier = Modifier.padding(vertical = 8.dp))

                // Scrollable List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Default Wallpaper Setup Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2026))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Reset Wallpaper Configuration",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Choose the default wallpaper to restore when routines end.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (defaultWallpaperUri != null) "✓ Custom Image Selected" else "No wallpaper set (System default)",
                                        fontSize = 13.sp,
                                        color = if (defaultWallpaperUri != null) Color(0xFF10B981) else Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Button(
                                        onClick = { defaultWallpaperPicker.launch(arrayOf("image/*")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2F36)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(text = "Choose", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // Section Header
                    item {
                        Text(
                            text = "Routines",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Dynamic list of items
                    items(routines) { item ->
                        val isRunning = activeRoutineId == item.id
                        val iconEmoji = when (item.iconResName) {
                            "star" -> "⭐"
                            "book" -> "📖"
                            "bed" -> "🛌"
                            "gym" -> "🏋️"
                            "music" -> "🎧"
                            "work" -> "💼"
                            else -> "⭐"
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingRoutine = item
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRunning) Color(0xFF1D4ED8) else Color(0xFF1E2026) // Highlight active
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icon badge representation
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isRunning) Color(0x3BFFFFFF) else Color(0xFF2C2F36)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = iconEmoji, fontSize = 24.sp)
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    val triggerText = if (item.isAutoTriggerEnabled && item.autoStartTime != null && item.autoEndTime != null) {
                                        "Trigger: Time (${item.autoStartTime} - ${item.autoEndTime})"
                                    } else {
                                        "Trigger: Manual Tap"
                                    }
                                    Text(
                                        text = triggerText,
                                        fontSize = 12.sp,
                                        color = if (isRunning) Color(0xFF93C5FD) else Color(0xFF64748B)
                                    )
                                }

                                Switch(
                                    checked = isRunning,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            // Check permissions
                                            if (!overlayPermissionGranted || !usageStatsPermissionGranted) {
                                                Toast.makeText(context, "Grant overlay and usage access permissions first!", Toast.LENGTH_LONG).show()
                                                return@Switch
                                            }
                                            
                                            val serviceIntent = Intent(context, RoutineService::class.java).apply {
                                                action = "START"
                                                putExtra("routine_id", item.id)
                                            }
                                            context.startForegroundService(serviceIntent)
                                            activeRoutineId = item.id
                                        } else {
                                            val serviceIntent = Intent(context, RoutineService::class.java).apply {
                                                action = "STOP"
                                            }
                                            context.startService(serviceIntent)
                                            activeRoutineId = null
                                        }
                                        refreshRoutinesList()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF60A5FA),
                                        uncheckedThumbColor = Color(0xFF64748B),
                                        uncheckedTrackColor = Color(0xFF2C2F36)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // VIEW 2: SAMSUNG ROUTINE CONFIGURATION EDITOR
            val routineObj = editingRoutine!!
            val isRunning = activeRoutineId == routineObj.id
            val iconEmoji = when (routineObj.iconResName) {
                "star" -> "⭐"
                "book" -> "📖"
                "bed" -> "🛌"
                "gym" -> "🏋️"
                "music" -> "🎧"
                "work" -> "💼"
                else -> "⭐"
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // Header (Close & Save/Delete)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "〈 Back",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.clickable {
                            // Validate flexible action constraints: at least 1 toggle must be active to save
                            if (!routineObj.isWallpaperEnabled && !routineObj.isAppBlockEnabled && !routineObj.isTimerEnabled && !routineObj.isSoundAlertEnabled && !routineObj.isWakeAlarmEnabled) {
                                Toast.makeText(context, "At least one Action must be enabled!", Toast.LENGTH_LONG).show()
                                return@clickable
                            }
                            
                            // Check overlap conflicts
                            if (checkOverlapConflict(routineObj, routines)) {
                                Toast.makeText(context, "Cannot save: Time conflicts with another routine schedule!", Toast.LENGTH_LONG).show()
                                return@clickable
                            }
                            
                            // Save configurations
                            sharedPrefs.edit().putString("routine_${routineObj.id}", routineObj.toJsonObject().toString()).apply()
                            
                            // Sync default routine for widget compatibility
                            val defaultId = sharedPrefs.getString("active_routine_id", null)
                            if (defaultId == null || defaultId == routineObj.id) {
                                sharedPrefs.edit().putString("routine_arise_default", routineObj.toJsonObject().toString()).apply()
                            }
                            
                            editingRoutine = null
                            refreshRoutinesList()
                        }
                    )
                    Text(
                        text = "Delete",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.clickable {
                            cancelAutoTrigger(routineObj)
                            sharedPrefs.edit().remove("routine_${routineObj.id}").apply()
                            
                            if (isRunning) {
                                val serviceIntent = Intent(context, RoutineService::class.java).apply {
                                    action = "STOP"
                                }
                                context.startService(serviceIntent)
                                activeRoutineId = null
                            }
                            
                            editingRoutine = null
                            refreshRoutinesList()
                            Toast.makeText(context, "Routine deleted.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Routine Badge & Title Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2F36))
                            .clickable { showIconDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = iconEmoji, fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = routineObj.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Turn On/Off Pill Button
                    Button(
                        onClick = {
                            if (isRunning) {
                                val serviceIntent = Intent(context, RoutineService::class.java).apply {
                                    action = "STOP"
                                }
                                context.startService(serviceIntent)
                                activeRoutineId = null
                            } else {
                                if (!overlayPermissionGranted || !usageStatsPermissionGranted) {
                                    Toast.makeText(context, "Permissions required first!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                
                                // Save configurations first
                                sharedPrefs.edit().putString("routine_${routineObj.id}", routineObj.toJsonObject().toString()).apply()
                                
                                val serviceIntent = Intent(context, RoutineService::class.java).apply {
                                    action = "START"
                                    putExtra("routine_id", routineObj.id)
                                }
                                context.startForegroundService(serviceIntent)
                                activeRoutineId = routineObj.id
                            }
                            refreshRoutinesList()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFF374151) else Color(0xFF3B82F6)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(text = if (isRunning) "Turn off" else "Turn on", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Scrollable configurations list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // IF: MANUAL TRIGGER CARD
                    item {
                        SamsungSettingsCard(
                            title = "Turn on manually",
                            subtitle = if (routineObj.durationMinutes > 0) "Duration: For ${routineObj.durationMinutes} minutes" else "Duration: Until I turn it off",
                            iconText = "⏳",
                            onClick = { showDurationDialog = true }
                        )
                    }

                    // IF: AUTO TRIGGER CARD
                    item {
                        val autoText = if (routineObj.isAutoTriggerEnabled && routineObj.autoStartTime != null && routineObj.autoEndTime != null) {
                            var text = "Scheduled: ${routineObj.autoStartTime} - ${routineObj.autoEndTime}"
                            if (routineObj.preStartWarningMinutes > 0) {
                                text += " (${routineObj.preStartWarningMinutes}m warning alert)"
                            }
                            text
                        } else {
                            "When to start automatically"
                        }
                        SamsungSettingsCard(
                            title = "Turn on automatically",
                            subtitle = autoText,
                            iconText = "➕",
                            onClick = { showAutoTriggerDialog = true }
                        )
                    }

                    // THEN HEADER
                    item {
                        Text(
                            text = "Choose what this mode does",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    // THEN: APP BLOCKER ACTION
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = routineObj.isAppBlockEnabled,
                                onCheckedChange = {
                                    val updated = routineObj.copy(isAppBlockEnabled = it)
                                    editingRoutine = updated
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF60A5FA),
                                    uncheckedThumbColor = Color(0xFF64748B),
                                    uncheckedTrackColor = Color(0xFF2C2F36)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SamsungSettingsCard(
                                title = "Stay focused (App blocker)",
                                subtitle = if (routineObj.blockedApps.isNotEmpty()) {
                                    "App restrictions active: ${routineObj.blockedApps.size} apps selected"
                                } else {
                                    "Restrict distracting apps"
                                },
                                iconText = "📵",
                                onClick = {
                                    loadInstalledApps()
                                    showAppPickerDialog = true
                                }
                            )
                        }
                    }

                    // THEN: COUNTDOWN TIMER & TARGET MESSAGE ACTION
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = routineObj.isTimerEnabled,
                                onCheckedChange = {
                                    val updated = routineObj.copy(isTimerEnabled = it)
                                    editingRoutine = updated
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF60A5FA),
                                    uncheckedThumbColor = Color(0xFF64748B),
                                    uncheckedTrackColor = Color(0xFF2C2F36)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SamsungSettingsCard(
                                title = "Study Countdown & Target",
                                subtitle = if (routineObj.customFocusMessage.isNotBlank()) {
                                    "Target: ${routineObj.customFocusMessage}"
                                } else {
                                    "Display custom targets & timers on lockscreen"
                                },
                                iconText = "🎯",
                                onClick = {
                                    customTargetInput = routineObj.customFocusMessage
                                    showCustomTargetDialog = true
                                }
                            )
                        }
                    }

                    // THEN: SOUND ALERT ACTION
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = routineObj.isSoundAlertEnabled,
                                onCheckedChange = {
                                    val updated = routineObj.copy(isSoundAlertEnabled = it)
                                    editingRoutine = updated
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF60A5FA),
                                    uncheckedThumbColor = Color(0xFF64748B),
                                    uncheckedTrackColor = Color(0xFF2C2F36)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SamsungSettingsCard(
                                title = "Sound Alert / Beep",
                                subtitle = if (routineObj.isSoundAlertEnabled) "Play notification beep on start & end" else "Silent transitions",
                                iconText = "🔊",
                                onClick = {
                                    val updated = routineObj.copy(isSoundAlertEnabled = !routineObj.isSoundAlertEnabled)
                                    editingRoutine = updated
                                }
                            )
                        }
                    }

                    // THEN: WAKE ALARM ACTION
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = routineObj.isWakeAlarmEnabled,
                                onCheckedChange = {
                                    val updated = routineObj.copy(isWakeAlarmEnabled = it)
                                    editingRoutine = updated
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF60A5FA),
                                    uncheckedThumbColor = Color(0xFF64748B),
                                    uncheckedTrackColor = Color(0xFF2C2F36)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SamsungSettingsCard(
                                title = "Wake Up Alarm",
                                subtitle = if (routineObj.isWakeAlarmEnabled && routineObj.wakeAlarmTime != null) {
                                    "Alarm scheduled for ${routineObj.wakeAlarmTime}"
                                } else {
                                    "Trigger alarm ringtone at routine end"
                                },
                                iconText = "⏰",
                                onClick = {
                                    val picker = TimePickerDialog(context, { _, h, m ->
                                        val timeStr = String.format("%02d:%02d", h, m)
                                        val updated = routineObj.copy(wakeAlarmTime = timeStr, isWakeAlarmEnabled = true)
                                        editingRoutine = updated
                                        sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                                    }, 7, 0, true)
                                    picker.show()
                                }
                            )
                        }
                    }

                    // THEN: WALLPAPERS APPEARANCE ACTION
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = routineObj.isWallpaperEnabled,
                                onCheckedChange = {
                                    val updated = routineObj.copy(isWallpaperEnabled = it)
                                    editingRoutine = updated
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF60A5FA),
                                    uncheckedThumbColor = Color(0xFF64748B),
                                    uncheckedTrackColor = Color(0xFF2C2F36)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Change appearance",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    // Lockscreen picker
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .width(90.dp)
                                                .height(130.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF1E2026))
                                                .clickable { lockScreenWallpaperPicker.launch(arrayOf("image/*")) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (routineObj.wallpaperUri != null) {
                                                Text("✓ Selected", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            } else {
                                                Text("+ Lock", color = Color(0xFF64748B), fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    // Homescreen picker
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .width(90.dp)
                                                .height(130.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF1E2026))
                                                .clickable { defaultWallpaperPicker.launch(arrayOf("image/*")) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (defaultWallpaperUri != null) {
                                                Text("✓ Selected", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            } else {
                                                Text("+ Home", color = Color(0xFF64748B), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG 0: Create Routine Dialog
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2026),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "New Routine Mode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newRoutineName,
                        onValueChange = { newRoutineName = it },
                        label = { Text("Routine Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedLabelColor = Color(0xFF3B82F6),
                            unfocusedLabelColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Cancel", color = Color(0xFF64748B))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newRoutineName.isNotBlank()) {
                                    val r = RoutineModel(name = newRoutineName)
                                    sharedPrefs.edit().putString("routine_${r.id}", r.toJsonObject().toString()).apply()
                                    routines.add(r)
                                    editingRoutine = r // Open editor immediately
                                    showCreateDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }

    // DIALOG 1: Duration chooser (manual)
    if (showDurationDialog) {
        Dialog(onDismissRequest = { showDurationDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2026),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Choose manual duration",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    DurationOption("Until I turn it off", onSelect = {
                        editingRoutine?.let {
                            val updated = it.copy(durationMinutes = 0, isTimerEnabled = false)
                            editingRoutine = updated
                            sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                        }
                        showDurationDialog = false
                    })
                    DurationOption("15 minutes", onSelect = {
                        editingRoutine?.let {
                            val updated = it.copy(durationMinutes = 15, isTimerEnabled = true)
                            editingRoutine = updated
                            sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                        }
                        showDurationDialog = false
                    })
                    DurationOption("30 minutes", onSelect = {
                        editingRoutine?.let {
                            val updated = it.copy(durationMinutes = 30, isTimerEnabled = true)
                            editingRoutine = updated
                            sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                        }
                        showDurationDialog = false
                    })
                    DurationOption("1 hour", onSelect = {
                        editingRoutine?.let {
                            val updated = it.copy(durationMinutes = 60, isTimerEnabled = true)
                            editingRoutine = updated
                            sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                        }
                        showDurationDialog = false
                    })
                    DurationOption("2 hours", onSelect = {
                        editingRoutine?.let {
                            val updated = it.copy(durationMinutes = 120, isTimerEnabled = true)
                            editingRoutine = updated
                            sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                        }
                        showDurationDialog = false
                    })
                    DurationOption("Custom duration...", onSelect = {
                        showDurationDialog = false
                        showCustomMinutesDialog = true
                    })
                }
            }
        }
    }

    // DIALOG 1.5: Custom minutes input
    if (showCustomMinutesDialog) {
        Dialog(onDismissRequest = { showCustomMinutesDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2026),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Set Custom Duration (minutes)",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { customMinutesInput = it },
                        label = { Text("Minutes") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCustomMinutesDialog = false }) {
                            Text("Cancel", color = Color(0xFF64748B))
                        }
                        Button(
                            onClick = {
                                val mins = customMinutesInput.toIntOrNull()
                                if (mins != null && mins > 0) {
                                    editingRoutine?.let {
                                        val updated = it.copy(durationMinutes = mins, isTimerEnabled = true)
                                        editingRoutine = updated
                                        sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                                    }
                                }
                                showCustomMinutesDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // DIALOG 2: Auto start triggers and alerts dialog
    if (showAutoTriggerDialog) {
        val rObj = editingRoutine!!
        Dialog(onDismissRequest = { showAutoTriggerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2026),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Automatic Time Trigger",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto trigger switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Enable Auto Trigger", color = Color.White, fontSize = 15.sp)
                        Switch(
                            checked = rObj.isAutoTriggerEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && (rObj.autoStartTime == null || rObj.autoEndTime == null)) {
                                    Toast.makeText(context, "Configure start/end times first!", Toast.LENGTH_SHORT).show()
                                    return@Switch
                                }
                                if (isChecked) {
                                    val checkModel = rObj.copy(isAutoTriggerEnabled = true)
                                    if (checkOverlapConflict(checkModel, routines)) {
                                        Toast.makeText(context, "Conflict Warning: Overlaps with an existing routine schedule!", Toast.LENGTH_LONG).show()
                                        return@Switch
                                    }
                                }
                                val updated = rObj.copy(isAutoTriggerEnabled = isChecked)
                                editingRoutine = updated
                                sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                                
                                if (isChecked) {
                                    scheduleAutoTrigger(updated)
                                } else {
                                    cancelAutoTrigger(updated)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Start time picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val picker = TimePickerDialog(context, { _, h, m ->
                                    val startStr = String.format("%02d:%02d", h, m)
                                    val updated = rObj.copy(autoStartTime = startStr)
                                    editingRoutine = updated
                                    sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                                    if (updated.isAutoTriggerEnabled) {
                                        scheduleAutoTrigger(updated)
                                    }
                                }, 8, 30, true)
                                picker.show()
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Start Time", color = Color.White)
                        Text(text = rObj.autoStartTime ?: "Set ➔", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }

                    // End time picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val picker = TimePickerDialog(context, { _, h, m ->
                                    val endStr = String.format("%02d:%02d", h, m)
                                    val updated = rObj.copy(autoEndTime = endStr)
                                    editingRoutine = updated
                                    sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                                    if (updated.isAutoTriggerEnabled) {
                                        scheduleAutoTrigger(updated)
                                    }
                                }, 17, 30, true)
                                picker.show()
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "End Time", color = Color.White)
                        Text(text = rObj.autoEndTime ?: "Set ➔", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF2C2F36))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Pre-start Warning Alert selector card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWarningDialog = true }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Pre-Start Warning Alert", color = Color.White, fontSize = 14.sp)
                            Text(
                                text = if (rObj.preStartWarningMinutes > 0) "${rObj.preStartWarningMinutes} minutes before start" else "Disabled",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                        Text(text = "➔", color = Color(0xFF64748B))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showAutoTriggerDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }

    // DIALOG 2.5: Pre-start Warning Selector Option list
    if (showWarningDialog) {
        val rObj = editingRoutine!!
        Dialog(onDismissRequest = { showWarningDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2026),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Choose Warning Interval", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    DurationOption("Disable pre-start alert", onSelect = {
                        val updated = rObj.copy(preStartWarningMinutes = 0)
                        editingRoutine = updated
                        sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                        if (updated.isAutoTriggerEnabled) scheduleAutoTrigger(updated)
                        showWarningDialog = false
                    })
                    DurationOption("1 minute before", onSelect = {
                        val updated = rObj.copy(preStartWarningMinutes = 1)
                        editingRoutine = updated
                        sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                        if (updated.isAutoTriggerEnabled) scheduleAutoTrigger(updated)
                        showWarningDialog = false
                    })
                    DurationOption("5 minutes before", onSelect = {
                        val updated = rObj.copy(preStartWarningMinutes = 5)
                        editingRoutine = updated
                        sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                        if (updated.isAutoTriggerEnabled) scheduleAutoTrigger(updated)
                        showWarningDialog = false
                    })
                    DurationOption("10 minutes before", onSelect = {
                        val updated = rObj.copy(preStartWarningMinutes = 10)
                        editingRoutine = updated
                        sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                        if (updated.isAutoTriggerEnabled) scheduleAutoTrigger(updated)
                        showWarningDialog = false
                    })
                }
            }
        }
    }

    // DIALOG 3: Icon Logo Dialog Picker
    if (showIconDialog) {
        val rObj = editingRoutine!!
        Dialog(onDismissRequest = { showIconDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2026),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Choose Routine Icon", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val icons = listOf(
                        "star" to "⭐ Star",
                        "book" to "📖 Book / Study",
                        "bed" to "🛌 Bed / Sleep",
                        "gym" to "🏋️ Gym / Fitness",
                        "music" to "🎧 Music / Audio",
                        "work" to "💼 Work / Focus"
                    )
                    
                    icons.forEach { (name, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = rObj.copy(iconResName = name)
                                    editingRoutine = updated
                                    sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                                    showIconDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }

    // DIALOG 4: Custom Target note input dialog
    if (showCustomTargetDialog) {
        Dialog(onDismissRequest = { showCustomTargetDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2026),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Set Lockscreen Target Note",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customTargetInput,
                        onValueChange = { customTargetInput = it },
                        label = { Text("Daily target / motivation note") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCustomTargetDialog = false }) {
                            Text("Cancel", color = Color(0xFF64748B))
                        }
                        Button(
                            onClick = {
                                editingRoutine?.let {
                                    val updated = it.copy(customFocusMessage = customTargetInput, isTimerEnabled = true)
                                    editingRoutine = updated
                                    sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                                }
                                showCustomTargetDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // DIALOG 5: Manage Restricted Apps Checklist dialog (Includes App Icon loading!)
    if (showAppPickerDialog) {
        Dialog(onDismissRequest = { showAppPickerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E2026),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Select Restricted Apps",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Choose applications to restrict when this mode starts.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(allApps) { app ->
                            val appIcon = getAppIconBitmap(context, app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        app.isSelected = !app.isSelected
                                        val idx = allApps.indexOfFirst { it.packageName == app.packageName }
                                        if (idx != -1) {
                                            allApps[idx] = app.copy(isSelected = app.isSelected)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = app.isSelected,
                                    onCheckedChange = { isChecked ->
                                        app.isSelected = isChecked
                                        val idx = allApps.indexOfFirst { it.packageName == app.packageName }
                                        if (idx != -1) {
                                            allApps[idx] = app.copy(isSelected = isChecked)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF3B82F6),
                                        uncheckedColor = Color(0xFF64748B)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                // Render App Icon Image if loaded successfully
                                if (appIcon != null) {
                                    Image(
                                        bitmap = appIcon,
                                        contentDescription = "App Icon",
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    // Fallback text card representation
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2C2F36))
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(text = app.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text(text = app.packageName, color = Color(0xFF64748B), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAppPickerDialog = false }) {
                            Text("Cancel", color = Color(0xFF94A3B8))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                editingRoutine?.let { routine ->
                                    val selectedPackages = allApps.filter { it.isSelected }.map { it.packageName }
                                    val updated = routine.copy(blockedApps = selectedPackages, isAppBlockEnabled = true)
                                    editingRoutine = updated
                                    sharedPrefs.edit().putString("routine_${updated.id}", updated.toJsonObject().toString()).apply()
                                }
                                showAppPickerDialog = false
                                refreshRoutinesList()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Text("Save Selection")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SamsungSettingsCard(
    title: String,
    subtitle: String,
    iconText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2026))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2C2F36)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = iconText, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Text(text = "〉", color = Color(0xFF64748B), fontSize = 14.sp)
        }
    }
}

@Composable
fun DurationOption(text: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(text = "➔", color = Color(0xFF64748B))
    }
}

// Time Overlap Check Helpers
fun checkOverlapConflict(updated: RoutineModel, routines: List<RoutineModel>): Boolean {
    if (!updated.isAutoTriggerEnabled || updated.autoStartTime == null || updated.autoEndTime == null) return false
    
    val uStart = timeToMinutes(updated.autoStartTime!!)
    val uEnd = timeToMinutes(updated.autoEndTime!!)
    
    routines.forEach { r ->
        if (r.id != updated.id && r.isAutoTriggerEnabled && r.autoStartTime != null && r.autoEndTime != null) {
            val rStart = timeToMinutes(r.autoStartTime!!)
            val rEnd = timeToMinutes(r.autoEndTime!!)
            
            if (intervalsOverlap(uStart, uEnd, rStart, rEnd)) {
                return true
            }
        }
    }
    return false
}

fun timeToMinutes(timeStr: String): Int {
    val parts = timeStr.split(":")
    return parts[0].toInt() * 60 + parts[1].toInt()
}

fun intervalsOverlap(s1: Int, e1: Int, s2: Int, e2: Int): Boolean {
    val end1 = if (e1 < s1) e1 + 1440 else e1
    val end2 = if (e2 < s2) e2 + 1440 else e2
    
    return s1 < end2 && end1 > s2
}
