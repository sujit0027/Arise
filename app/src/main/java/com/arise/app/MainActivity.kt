package com.arise.app

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SamsungRoutineUI()
            }
        }
    }
}

@Composable
fun SamsungRoutineUI() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE) }
    
    // Core states
    var routine by remember { mutableStateOf(RoutineModel(name = "Arise Mode")) }
    var activeRoutineId by remember { mutableStateOf<String?>(null) }
    
    var overlayPermissionGranted by remember { mutableStateOf(false) }
    var usageStatsPermissionGranted by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember { mutableStateOf(false) }
    
    var defaultWallpaperUri by remember { mutableStateOf<String?>(null) }
    
    // Dialog / Selection states
    var showDurationDialog by remember { mutableStateOf(false) }
    var showAutoTriggerDialog by remember { mutableStateOf(false) }
    var showAppPickerDialog by remember { mutableStateOf(false) }
    var customMinutesInput by remember { mutableStateOf("") }
    var showCustomMinutesDialog by remember { mutableStateOf(false) }
    
    val allApps = remember { mutableStateListOf<AppItem>() }

    // Helper functions
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

    // Load initial states
    LaunchedEffect(Unit) {
        checkPermissions()
        defaultWallpaperUri = sharedPrefs.getString("default_wallpaper_uri", null)
        activeRoutineId = sharedPrefs.getString("active_routine_id", null)
        
        // Load or create single default routine
        val routineJson = sharedPrefs.getString("routine_arise_default", null)
        if (routineJson != null) {
            try {
                routine = RoutineModel.fromJson(routineJson)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to parse routine: ${e.message}")
            }
        } else {
            val defaultRoutine = RoutineModel(id = "arise_default", name = "Arise Focus Mode")
            sharedPrefs.edit().putString("routine_arise_default", defaultRoutine.toJsonObject().toString()).apply()
            routine = defaultRoutine
        }
    }

    // Recheck permissions when dialogs change
    LaunchedEffect(showDurationDialog, showAutoTriggerDialog, showAppPickerDialog) {
        checkPermissions()
    }

    // Wallpaper Pickers
    val lockScreenWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val updated = routine.copy(wallpaperUri = it.toString())
                    routine = updated
                    sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
                    Toast.makeText(context, "Lockscreen wallpaper selected!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read image permissions.", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val homeScreenWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    defaultWallpaperUri = it.toString()
                    sharedPrefs.edit().putString("default_wallpaper_uri", it.toString()).apply()
                    Toast.makeText(context, "Homescreen (Reset) wallpaper saved!", Toast.LENGTH_SHORT).show()
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

    // AlarmManager scheduling
    fun scheduleAutoTrigger(r: RoutineModel) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // 1. Schedule auto start
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
        }

        // 2. Schedule auto end
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
        Log.d("MainActivity", "Cancelled AlarmManager triggers")
    }

    // App loader (thanks to QUERY_ALL_PACKAGES, this works fully now!)
    fun loadInstalledApps() {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val filteredApps = mutableListOf<AppItem>()
        
        apps.forEach { app ->
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null && app.packageName != context.packageName) {
                val label = pm.getApplicationLabel(app).toString()
                val isSelected = routine.blockedApps.contains(app.packageName)
                filteredApps.add(AppItem(label, app.packageName, isSelected))
            }
        }
        filteredApps.sortBy { it.name }
        allApps.clear()
        allApps.addAll(filteredApps)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F1014) // Dark premium AMOLED black/navy background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Header: Close arrow & Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "〈",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clickable { /* Exit or minimize */ }
                )
                Text(
                    text = "︙",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Samsung-like visual badge at the top
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circle Badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2D1B1E)), // Coral dark tint
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "★",
                        color = Color(0xFFFCA5A5), // warm red/salmon star
                        fontSize = 32.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Mode Title
                Text(
                    text = routine.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Big "Turn on" / "Turn off" Button
                val isRunning = activeRoutineId == routine.id
                Button(
                    onClick = {
                        if (isRunning) {
                            // Turn Off
                            val serviceIntent = Intent(context, RoutineService::class.java).apply {
                                action = "STOP"
                            }
                            context.startService(serviceIntent)
                            activeRoutineId = null
                        } else {
                            // Turn On (Validate permissions)
                            if (!overlayPermissionGranted || !usageStatsPermissionGranted) {
                                Toast.makeText(context, "Overlay and Usage access permissions are required!", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            
                            val serviceIntent = Intent(context, RoutineService::class.java).apply {
                                action = "START"
                                putExtra("routine_id", routine.id)
                            }
                            context.startForegroundService(serviceIntent)
                            activeRoutineId = routine.id
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFF374151) else Color(0xFF3B82F6) // Gray for off, Blue for on
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 36.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (isRunning) "Turn off" else "Turn on",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Scrollable List of Settings
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section: Permissions Alert if missing
                val needsPermissions = !overlayPermissionGranted || !usageStatsPermissionGranted || !notificationPermissionGranted
                if (needsPermissions) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x32EF4444))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Permissions Required",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (!overlayPermissionGranted) {
                                    PermissionItem("Draw over other apps", onRequest = {
                                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                        context.startActivity(intent)
                                    })
                                }
                                if (!usageStatsPermissionGranted) {
                                    PermissionItem("Usage Stats access", onRequest = {
                                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        context.startActivity(intent)
                                    })
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                                    PermissionItem("Push Notifications", onRequest = {
                                        requestNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    })
                                }
                            }
                        }
                    }
                }

                // Card: Turn on manually (Duration)
                item {
                    SamsungSettingsCard(
                        title = "Turn on manually",
                        subtitle = if (routine.durationMinutes > 0) "Duration: For ${routine.durationMinutes} minutes" else "Duration: Until I turn it off",
                        iconText = "⏳",
                        onClick = { showDurationDialog = true }
                    )
                }

                // Card: Turn on automatically (Auto trigger time scheduler)
                item {
                    val autoText = if (routine.isAutoTriggerEnabled && routine.autoStartTime != null && routine.autoEndTime != null) {
                        "Time: ${routine.autoStartTime} - ${routine.autoEndTime}"
                    } else {
                        "When to start this mode"
                    }
                    SamsungSettingsCard(
                        title = "Turn on automatically",
                        subtitle = autoText,
                        iconText = "➕",
                        onClick = { showAutoTriggerDialog = true }
                    )
                }

                // Card Header: Choose what this mode does
                item {
                    Text(
                        text = "Choose what this mode does",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                // Card: Stay focused (App blocker list)
                item {
                    SamsungSettingsCard(
                        title = "Stay focused",
                        subtitle = if (routine.blockedApps.isNotEmpty()) {
                            "Blocked apps: ${routine.blockedApps.size} apps selected"
                        } else {
                            "Ways to avoid distractions"
                        },
                        iconText = "📵",
                        onClick = {
                            loadInstalledApps()
                            showAppPickerDialog = true
                        }
                    )
                }

                // Card Header: Change appearance
                item {
                    Text(
                        text = "Change appearance",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                // Appearance column / screens preview side-by-side
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Lock screen preview Card
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E2026))
                                    .clickable { lockScreenWallpaperPicker.launch(arrayOf("image/*")) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (routine.wallpaperUri != null) {
                                    Text("✓ Selected", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("+ Lock", color = Color(0xFF64748B), fontSize = 13.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Lock screen", fontSize = 12.sp, color = Color.White)
                        }

                        // Home screen preview Card
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E2026))
                                    .clickable { homeScreenWallpaperPicker.launch(arrayOf("image/*")) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (defaultWallpaperUri != null) {
                                    Text("✓ Selected", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("+ Home", color = Color(0xFF64748B), fontSize = 13.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Home screen", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Dialog 1: Duration Select Dialog
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
                        val updated = routine.copy(durationMinutes = 0)
                        routine = updated
                        sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
                        showDurationDialog = false
                    })
                    DurationOption("15 minutes", onSelect = {
                        val updated = routine.copy(durationMinutes = 15)
                        routine = updated
                        sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
                        showDurationDialog = false
                    })
                    DurationOption("30 minutes", onSelect = {
                        val updated = routine.copy(durationMinutes = 30)
                        routine = updated
                        sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
                        showDurationDialog = false
                    })
                    DurationOption("1 hour", onSelect = {
                        val updated = routine.copy(durationMinutes = 60)
                        routine = updated
                        sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
                        showDurationDialog = false
                    })
                    DurationOption("2 hours", onSelect = {
                        val updated = routine.copy(durationMinutes = 120)
                        routine = updated
                        sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
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

    // Dialog 1.5: Custom Minutes dialog
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
                                    val updated = routine.copy(durationMinutes = mins)
                                    routine = updated
                                    sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
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

    // Dialog 2: Auto Start Trigger Editor
    if (showAutoTriggerDialog) {
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
                    Text(
                        text = "Choose starting and ending times for this focus routine.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Auto Trigger Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Enable Auto Trigger", color = Color.White, fontSize = 15.sp)
                        Switch(
                            checked = routine.isAutoTriggerEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && (routine.autoStartTime == null || routine.autoEndTime == null)) {
                                    Toast.makeText(context, "Configure start/end times first!", Toast.LENGTH_SHORT).show()
                                    return@Switch
                                }
                                val updated = routine.copy(isAutoTriggerEnabled = isChecked)
                                routine = updated
                                sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
                                
                                if (isChecked) {
                                    scheduleAutoTrigger(updated)
                                    Toast.makeText(context, "Auto trigger scheduled!", Toast.LENGTH_SHORT).show()
                                } else {
                                    cancelAutoTrigger(updated)
                                    Toast.makeText(context, "Auto trigger disabled.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Start Time trigger selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val picker = TimePickerDialog(context, { _, h, m ->
                                    val startStr = String.format("%02d:%02d", h, m)
                                    val updated = routine.copy(autoStartTime = startStr)
                                    routine = updated
                                    sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
                                    
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
                        Text(text = routine.autoStartTime ?: "Configure ➔", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }

                    // End Time trigger selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val picker = TimePickerDialog(context, { _, h, m ->
                                    val endStr = String.format("%02d:%02d", h, m)
                                    val updated = routine.copy(autoEndTime = endStr)
                                    routine = updated
                                    sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
                                    
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
                        Text(text = routine.autoEndTime ?: "Configure ➔", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
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

    // Dialog 3: Manage Restricted Apps Checklist
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
                                val selectedPackages = allApps.filter { it.isSelected }.map { it.packageName }
                                val updated = routine.copy(blockedApps = selectedPackages)
                                routine = updated
                                sharedPrefs.edit().putString("routine_arise_default", updated.toJsonObject().toString()).apply()
                                showAppPickerDialog = false
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
                    .background(Color(0xFF2C2F36)), // darker badge bg
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
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
fun PermissionItem(name: String, onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "• $name", fontSize = 12.sp, color = Color(0xFFFCA5A5))
        Text(
            text = "Grant ➔",
            fontSize = 12.sp,
            color = Color(0xFF3B82F6),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onRequest() }
        )
    }
}

@Composable
fun DurationOption(text: String, onSelect: () -> Unit) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 12.dp)
    )
}
