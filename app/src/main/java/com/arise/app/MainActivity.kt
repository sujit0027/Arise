package com.arise.app

import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                MainDashboardScreen()
            }
        }
    }
}

data class AppItem(val name: String, val packageName: String, var isSelected: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE) }
    
    // States
    val routines = remember { mutableStateListOf<RoutineModel>() }
    var activeRoutineId by remember { mutableStateOf<String?>(null) }
    
    var overlayPermissionGranted by remember { mutableStateOf(false) }
    var usageStatsPermissionGranted by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember { mutableStateOf(false) }
    
    var defaultWallpaperUri by remember { mutableStateOf<String?>(null) }
    
    // Dialog States
    var showCreateDialog by remember { mutableStateOf(false) }
    var newRoutineName by remember { mutableStateOf("") }
    
    var editingRoutine by remember { mutableStateOf<RoutineModel?>(null) }
    var showAppPickerDialog by remember { mutableStateOf(false) }
    val allApps = remember { mutableStateListOf<AppItem>() }

    // Check Permissions
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

    // Load initial data
    LaunchedEffect(Unit) {
        checkPermissions()
        defaultWallpaperUri = sharedPrefs.getString("default_wallpaper_uri", null)
        activeRoutineId = sharedPrefs.getString("active_routine_id", null)
        
        // Load routines from prefs
        val keys = sharedPrefs.all.keys
        routines.clear()
        keys.forEach { key ->
            if (key.startsWith("routine_")) {
                val jsonStr = sharedPrefs.getString(key, null)
                if (jsonStr != null) {
                    try {
                        routines.add(RoutineModel.fromJson(jsonStr))
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error parsing routine JSON: ${e.message}")
                    }
                }
            }
        }
        
        // If empty, add a default manual routine
        if (routines.isEmpty()) {
            val defaultRoutine = RoutineModel(name = "Focus Routine")
            sharedPrefs.edit().putString("routine_${defaultRoutine.id}", defaultRoutine.toJsonObject().toString()).apply()
            routines.add(defaultRoutine)
        }
    }

    // Recheck permissions on window focus
    LaunchedEffect(showCreateDialog, showAppPickerDialog) {
        checkPermissions()
    }

    // Launchers for custom wallpapers
    val routineWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    editingRoutine?.let { routine ->
                        routine.wallpaperUri = it.toString()
                        // Save routine
                        sharedPrefs.edit().putString("routine_${routine.id}", routine.toJsonObject().toString()).apply()
                        // Force recompose
                        val idx = routines.indexOfFirst { r -> r.id == routine.id }
                        if (idx != -1) {
                            routines[idx] = routine.copy(wallpaperUri = it.toString())
                        }
                    }
                    Toast.makeText(context, "Routine Wallpaper Selected!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read image permission.", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(context, "Default Reset Wallpaper Saved!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read image permission.", Toast.LENGTH_LONG).show()
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

    // Load installed launcher apps
    fun loadInstalledApps() {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val filteredApps = mutableListOf<AppItem>()
        
        apps.forEach { app ->
            // Filter system apps, only show launchable ones
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // Sleek Premium dark blue background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header
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
                        color = Color(0xFF0EA5E9)
                    )
                    Text(
                        text = "Modes & Routines Automation",
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "+ Create", fontWeight = FontWeight.Bold)
                }
            }

            Divider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 8.dp))

            // Dashboard items (Scrollable)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Setup Default Wallpaper
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Reset Wallpaper Setup",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Choose the wallpaper you want to restore automatically when routines turn off.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (defaultWallpaperUri != null) "✓ Selected Custom Image" else "No wallpaper set (System default)",
                                    fontSize = 13.sp,
                                    color = if (defaultWallpaperUri != null) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Medium
                                )
                                Button(
                                    onClick = { defaultWallpaperPicker.launch(arrayOf("image/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(text = "Choose Image", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Section: Permissions Checker
                item {
                    val needsPermissions = !overlayPermissionGranted || !usageStatsPermissionGranted || !notificationPermissionGranted
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (needsPermissions) Color(0x28EF4444) else Color(0xFF1E293B)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Permissions Dashboard",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Draw over other apps
                            PermissionRow(
                                title = "Draw Over Other Apps",
                                isGranted = overlayPermissionGranted,
                                onRequest = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // App Usage Access
                            PermissionRow(
                                title = "App Usage Access",
                                isGranted = usageStatsPermissionGranted,
                                onRequest = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                }
                            )

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Spacer(modifier = Modifier.height(8.dp))
                                // Notifications
                                PermissionRow(
                                    title = "Push Notifications",
                                    isGranted = notificationPermissionGranted,
                                    onRequest = {
                                        requestNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                )
                            }
                        }
                    }
                }

                // Section: Routines List Header
                item {
                    Text(
                        text = "Your Routines",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Routines list
                items(routines) { routine ->
                    val isRunning = activeRoutineId == routine.id
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingRoutine = routine
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRunning) Color(0xFF1E3A8A) else Color(0xFF1E293B)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = routine.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (routine.triggerType == "TIMER") "Trigger: Timer (${routine.triggerTime})" else "Trigger: Manual Tap",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Switch(
                                    checked = isRunning,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            // Check permissions first
                                            if (!overlayPermissionGranted || !usageStatsPermissionGranted) {
                                                Toast.makeText(context, "Please grant all overlay and usage access permissions first!", Toast.LENGTH_LONG).show()
                                                return@Switch
                                            }
                                            
                                            // Start service
                                            val serviceIntent = Intent(context, RoutineService::class.java).apply {
                                                action = "START"
                                                putExtra("routine_id", routine.id)
                                            }
                                            context.startForegroundService(serviceIntent)
                                            activeRoutineId = routine.id
                                            
                                            // Update model state locally
                                            val idx = routines.indexOfFirst { r -> r.id == routine.id }
                                            if (idx != -1) {
                                                routines[idx] = routine.copy(isActive = true)
                                            }
                                        } else {
                                            // Stop service
                                            val serviceIntent = Intent(context, RoutineService::class.java).apply {
                                                action = "STOP"
                                            }
                                            context.startService(serviceIntent)
                                            activeRoutineId = null
                                            
                                            // Update model state locally
                                            val idx = routines.indexOfFirst { r -> r.id == routine.id }
                                            if (idx != -1) {
                                                routines[idx] = routine.copy(isActive = false)
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF0EA5E9),
                                        checkedTrackColor = Color(0x3B0EA5E9),
                                        uncheckedThumbColor = Color(0xFF64748B),
                                        uncheckedTrackColor = Color(0xFF334155)
                                    )
                                )
                            }

                            // Quick settings if editing this card
                            AnimatedVisibility(visible = editingRoutine?.id == routine.id) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    Divider(color = Color(0xFF334155), modifier = Modifier.padding(bottom = 12.dp))
                                    
                                    // Wallpaper picker for routine
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Routine Wallpaper:",
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                        TextButton(
                                            onClick = { routineWallpaperPicker.launch(arrayOf("image/*")) }
                                        ) {
                                            Text(
                                                text = if (routine.wallpaperUri != null) "✓ Change Image" else "Select Image",
                                                fontSize = 13.sp,
                                                color = Color(0xFF0EA5E9)
                                            )
                                        }
                                    }

                                    // App blocking selection
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Block Apps (${routine.blockedApps.size} apps):",
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                        TextButton(
                                            onClick = {
                                                loadInstalledApps()
                                                showAppPickerDialog = true
                                            }
                                        ) {
                                            Text(text = "Manage Apps", fontSize = 13.sp, color = Color(0xFF0EA5E9))
                                        }
                                    }

                                    // Trigger Configuration (Manual vs Timer)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Trigger Mode:",
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                        
                                        Row {
                                            TextButton(
                                                onClick = {
                                                    routine.triggerType = "MANUAL"
                                                    sharedPrefs.edit().putString("routine_${routine.id}", routine.toJsonObject().toString()).apply()
                                                    val idx = routines.indexOfFirst { r -> r.id == routine.id }
                                                    if (idx != -1) {
                                                        routines[idx] = routine.copy(triggerType = "MANUAL")
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = "Manual",
                                                    fontSize = 13.sp,
                                                    color = if (routine.triggerType == "MANUAL") Color(0xFF0EA5E9) else Color(0xFF64748B),
                                                    fontWeight = if (routine.triggerType == "MANUAL") FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                            
                                            TextButton(
                                                onClick = {
                                                    val calendar = Calendar.getInstance()
                                                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                                                    val minute = calendar.get(Calendar.MINUTE)
                                                    
                                                    val tpd = TimePickerDialog(context, { _, h, m ->
                                                        val formattedTime = String.format("%02d:%02d", h, m)
                                                        routine.triggerType = "TIMER"
                                                        routine.triggerTime = formattedTime
                                                        sharedPrefs.edit().putString("routine_${routine.id}", routine.toJsonObject().toString()).apply()
                                                        
                                                        val idx = routines.indexOfFirst { r -> r.id == routine.id }
                                                        if (idx != -1) {
                                                            routines[idx] = routine.copy(triggerType = "TIMER", triggerTime = formattedTime)
                                                        }
                                                    }, hour, minute, true)
                                                    tpd.show()
                                                }
                                            ) {
                                                Text(
                                                    text = if (routine.triggerType == "TIMER") "Timer: ${routine.triggerTime}" else "Timer",
                                                    fontSize = 13.sp,
                                                    color = if (routine.triggerType == "TIMER") Color(0xFF0EA5E9) else Color(0xFF64748B),
                                                    fontWeight = if (routine.triggerType == "TIMER") FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    // Lockscreen widget option toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Show widget over lock screen",
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Checkbox(
                                            checked = routine.isLockScreenOverlayEnabled,
                                            onCheckedChange = { isChecked ->
                                                routine.isLockScreenOverlayEnabled = isChecked
                                                sharedPrefs.edit().putString("routine_${routine.id}", routine.toJsonObject().toString()).apply()
                                                val idx = routines.indexOfFirst { r -> r.id == routine.id }
                                                if (idx != -1) {
                                                    routines[idx] = routine.copy(isLockScreenOverlayEnabled = isChecked)
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF0EA5E9),
                                                uncheckedColor = Color(0xFF64748B)
                                            )
                                        )
                                    }

                                    // Delete routine button
                                    Button(
                                        onClick = {
                                            sharedPrefs.edit().remove("routine_${routine.id}").apply()
                                            routines.remove(routine)
                                            editingRoutine = null
                                            if (activeRoutineId == routine.id) {
                                                val serviceIntent = Intent(context, RoutineService::class.java).apply {
                                                    action = "STOP"
                                                }
                                                context.startService(serviceIntent)
                                                activeRoutineId = null
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(text = "Delete Routine", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create Routine
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
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
                            focusedBorderColor = Color(0xFF0EA5E9),
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedLabelColor = Color(0xFF0EA5E9),
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
                            Text("Cancel", color = Color(0xFF94A3B8))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newRoutineName.isNotBlank()) {
                                    val r = RoutineModel(name = newRoutineName)
                                    sharedPrefs.edit().putString("routine_${r.id}", r.toJsonObject().toString()).apply()
                                    routines.add(r)
                                    showCreateDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }

    // Dialog: Manage/Block Apps Checklist
    if (showAppPickerDialog) {
        Dialog(onDismissRequest = { showAppPickerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
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
                                        checkedColor = Color(0xFF0EA5E9),
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
                                editingRoutine?.let { routine ->
                                    val selectedPackages = allApps.filter { it.isSelected }.map { it.packageName }
                                    routine.blockedApps = selectedPackages
                                    sharedPrefs.edit().putString("routine_${routine.id}", routine.toJsonObject().toString()).apply()
                                    
                                    val idx = routines.indexOfFirst { r -> r.id == routine.id }
                                    if (idx != -1) {
                                        routines[idx] = routine.copy(blockedApps = selectedPackages)
                                    }
                                }
                                showAppPickerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
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
fun PermissionRow(title: String, isGranted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(
                text = if (isGranted) "Permission Active" else "Setup Required",
                fontSize = 11.sp,
                color = if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444)
            )
        }
        
        if (!isGranted) {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(text = "Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x2310B981))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "OK", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
            }
        }
    }
}
