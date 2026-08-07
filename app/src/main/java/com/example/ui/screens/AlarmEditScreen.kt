package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.AlarmScheduler
import com.example.data.model.RoutineAlarm
import com.example.ui.components.WallpaperBackground
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.AmberLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarmToEdit: RoutineAlarm?,
    onSaveAlarm: (RoutineAlarm) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(alarmToEdit?.title ?: "Math Practice") }
    var hour by remember { mutableStateOf(alarmToEdit?.hour ?: 6) }
    var minute by remember { mutableStateOf(alarmToEdit?.minute ?: 30) }
    var isAm by remember { mutableStateOf(if (alarmToEdit != null) alarmToEdit.hour < 12 else true) }

    // Repeat Days: 0=Sun, 1=Mon, ..., 6=Sat
    val selectedDays = remember {
        mutableStateListOf<Int>().apply {
            if (alarmToEdit != null) {
                addAll(AlarmScheduler.parseRepeatDays(alarmToEdit.repeatDays))
            } else {
                addAll(listOf(1, 2, 3, 4, 5)) // Default Mon-Fri
            }
        }
    }

    var gapIntervalMinutes by remember { mutableStateOf(alarmToEdit?.gapIntervalMinutes ?: 2) }
    var maxRepeats by remember { mutableStateOf(alarmToEdit?.maxRepeats ?: 5) }
    var conditionText by remember { mutableStateOf(alarmToEdit?.conditionText ?: "I am awake and ready to study") }
    var wallpaperType by remember { mutableStateOf(alarmToEdit?.wallpaperType ?: "preset_sunrise") }
    var customWallpaperUri by remember { mutableStateOf(alarmToEdit?.customWallpaperUri) }
    var overlayOpacity by remember { mutableStateOf(alarmToEdit?.overlayOpacity ?: 0.5f) }
    var blurIntensity by remember { mutableStateOf(alarmToEdit?.blurIntensity ?: 10f) }
    var strictCase by remember { mutableStateOf(alarmToEdit?.strictCaseMatching ?: false) }

    var showLivePreview by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            customWallpaperUri = it.toString()
            wallpaperType = "custom_uri"
        }
    }

    val routinePresets = listOf(
        "Math Practice",
        "DSA & Algorithms",
        "Morning Focus",
        "Vocabulary Drill",
        "Physics Problem Solving",
        "Exam Preparation"
    )

    val sentencePresets = listOf(
        "I am awake and ready to study",
        "Consistency is the key to my success",
        "I choose my future over comfort today",
        "Math requires focus, logic, and persistence",
        "Every morning brings a new opportunity to excel"
    )

    val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (alarmToEdit == null) "New Routine Alarm" else "Edit Routine Alarm",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val computedHour = if (isAm) (if (hour == 12) 0 else hour) else (if (hour == 12) 12 else hour + 12)
                            val daysString = selectedDays.sorted().joinToString(",")
                            val updatedAlarm = (alarmToEdit ?: RoutineAlarm()).copy(
                                title = title.ifBlank { "Study Routine" },
                                hour = computedHour,
                                minute = minute,
                                repeatDays = daysString,
                                gapIntervalMinutes = gapIntervalMinutes,
                                maxRepeats = maxRepeats,
                                conditionText = conditionText.ifBlank { "I am awake and ready to study" },
                                wallpaperType = wallpaperType,
                                customWallpaperUri = customWallpaperUri,
                                overlayOpacity = overlayOpacity,
                                blurIntensity = blurIntensity,
                                strictCaseMatching = strictCase,
                                isEnabled = true
                            )
                            onSaveAlarm(updatedAlarm)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_alarm_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Save", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Time Picker Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. ALARM TIME",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberLight,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hour Control
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { hour = if (hour >= 12) 1 else hour + 1 }) {
                                Icon(Icons.Default.Add, contentDescription = "+Hour", tint = Color.White)
                            }
                            Text(
                                text = String.format("%02d", hour),
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            IconButton(onClick = { hour = if (hour <= 1) 12 else hour - 1 }) {
                                Icon(Icons.Default.Remove, contentDescription = "-Hour", tint = Color.White)
                            }
                        }

                        Text(text = ":", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))

                        // Minute Control (+1 / -1 for exact time like 31, 32 min)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { minute = (minute + 1) % 60 }) {
                                Icon(Icons.Default.Add, contentDescription = "+Min", tint = Color.White)
                            }
                            Text(
                                text = String.format("%02d", minute),
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            IconButton(onClick = { minute = if (minute == 0) 59 else minute - 1 }) {
                                Icon(Icons.Default.Remove, contentDescription = "-Min", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // AM/PM Toggle
                        Column {
                            FilterChip(
                                selected = isAm,
                                onClick = { isAm = true },
                                label = { Text("AM", fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = !isAm,
                                onClick = { isAm = false },
                                label = { Text("PM", fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 2. Routine Name & Days Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. ROUTINE NAME & REPEAT DAYS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberLight,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Routine Name (e.g. Math Practice)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("routine_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(routinePresets) { preset ->
                            SuggestionChip(
                                onClick = { title = preset },
                                label = { Text(preset, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "Repeat Days:", fontSize = 13.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        dayLabels.forEachIndexed { index, day ->
                            val isSelected = selectedDays.contains(index)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AmberPrimary else Color(0xFF0F172A))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) AmberLight else Color(0xFF334155),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (isSelected) selectedDays.remove(index) else selectedDays.add(index)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // 3. Interval & Repeat Logic (The Gap) Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "3. INTERVAL & REPEAT LOGIC (THE GAP)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberLight,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Gap Interval if Ignored:", fontSize = 14.sp, color = Color.White)
                        Text(
                            text = "$gapIntervalMinutes minutes",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberLight
                        )
                    }

                    Slider(
                        value = gapIntervalMinutes.toFloat(),
                        onValueChange = { gapIntervalMinutes = it.toInt() },
                        valueRange = 1f..15f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = AmberPrimary,
                            activeTrackColor = AmberPrimary,
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.testTag("gap_interval_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Maximum Repeat Limit:", fontSize = 14.sp, color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (maxRepeats > 1) maxRepeats-- }) {
                                Icon(Icons.Default.Remove, contentDescription = "-Repeat", tint = Color.White)
                            }
                            Text(
                                text = "$maxRepeats times",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            IconButton(onClick = { if (maxRepeats < 20) maxRepeats++ }) {
                                Icon(Icons.Default.Add, contentDescription = "+Repeat", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // 4. The Awakening Value (Condition Text) Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "4. THE AWAKENING VALUE (CONDITION TEXT)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberLight,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You MUST type this sentence EXACTLY on the alarm screen to stop the alarm.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = conditionText,
                        onValueChange = { conditionText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("condition_text_input"),
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Quick Sentence Presets:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sentencePresets) { sentence ->
                            SuggestionChip(
                                onClick = { conditionText = sentence },
                                label = { Text("\"$sentence\"", fontSize = 11.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }
                }
            }

            // 5. Motivational Alarm Wallpaper Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "5. MOTIVATIONAL ALARM WALLPAPER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberLight,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Wallpaper Presets
                    Text(text = "Choose Motivational Preset:", fontSize = 13.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WallpaperPresetCard(
                            title = "Dawn Summit",
                            type = "preset_sunrise",
                            isSelected = wallpaperType == "preset_sunrise",
                            onSelect = { wallpaperType = "preset_sunrise" },
                            modifier = Modifier.weight(1f)
                        )
                        WallpaperPresetCard(
                            title = "Deep Focus",
                            type = "preset_library",
                            isSelected = wallpaperType == "preset_library",
                            onSelect = { wallpaperType = "preset_library" },
                            modifier = Modifier.weight(1f)
                        )
                        WallpaperPresetCard(
                            title = "Cyber Drive",
                            type = "preset_cyber",
                            isSelected = wallpaperType == "preset_cyber",
                            onSelect = { wallpaperType = "preset_cyber" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Upload Custom Gallery Photo Button
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_custom_photo_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (wallpaperType == "custom_uri") AmberLight else Color.White
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (wallpaperType == "custom_uri") AmberPrimary else Color(0xFF334155)
                            )
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (wallpaperType == "custom_uri") "Custom Gallery Photo Selected ✓" else "Upload Custom Gallery Photo",
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Overlay Dark Opacity & Blur Sliders
                    Text(text = "Dark Overlay Tint: ${(overlayOpacity * 100).toInt()}%", fontSize = 13.sp, color = Color.White)
                    Slider(
                        value = overlayOpacity,
                        onValueChange = { overlayOpacity = it },
                        valueRange = 0.2f..0.85f,
                        colors = SliderDefaults.colors(thumbColor = AmberPrimary, activeTrackColor = AmberPrimary)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Blur Effect: ${blurIntensity.toInt()} dp", fontSize = 13.sp, color = Color.White)
                    Slider(
                        value = blurIntensity,
                        onValueChange = { blurIntensity = it },
                        valueRange = 0f..25f,
                        colors = SliderDefaults.colors(thumbColor = AmberPrimary, activeTrackColor = AmberPrimary)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Wallpaper Preview Toggle Button
                    Button(
                        onClick = { showLivePreview = !showLivePreview },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (showLivePreview) "Hide Ringing Screen Preview" else "Preview Alarm Ringing Screen")
                    }

                    AnimatedVisibility(visible = showLivePreview) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            WallpaperBackground(
                                wallpaperType = wallpaperType,
                                customWallpaperUri = customWallpaperUri,
                                overlayOpacity = overlayOpacity,
                                blurIntensity = blurIntensity
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "06:30 AM",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Text(
                                        text = title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberLight
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xCC0F172A))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = "\"$conditionText\"",
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
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

@Composable
fun WallpaperPresetCard(
    title: String,
    type: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AmberPrimary else Color(0xFF334155),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onSelect)
    ) {
        WallpaperBackground(
            wallpaperType = type,
            overlayOpacity = 0.4f,
            blurIntensity = 0f
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
