package com.arise.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure show when locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        setContent {
            MaterialTheme {
                LockScreenUI(
                    onDismiss = {
                        finish()
                    },
                    onStopRoutine = {
                        // Send Stop intent to the foreground service
                        val stopIntent = Intent(this, RoutineService::class.java).apply {
                            action = "STOP"
                        }
                        startService(stopIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LockScreenUI(onDismiss: () -> Unit, onStopRoutine: () -> Unit) {
    val context = LocalContext.current
    var routineName by remember { mutableStateOf("Arise Routine") }
    var timeString by remember { mutableStateOf("00:00") }
    var dateString by remember { mutableStateOf("Date") }
    var countdownText by remember { mutableStateOf("") }

    // Load active routine details & start dynamic ticker
    LaunchedEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
        val activeRoutineId = sharedPrefs.getString("active_routine_id", null)
        if (activeRoutineId != null) {
            val routineJson = sharedPrefs.getString("routine_$activeRoutineId", null)
            if (routineJson != null) {
                try {
                    val routine = RoutineModel.fromJson(routineJson)
                    routineName = routine.name
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        val endTime = sharedPrefs.getLong("active_routine_end_time", -1L)

        // Continuous update loop for ticking clock and countdown
        while (true) {
            // 1. Update clock
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            timeString = timeFormat.format(Date())
            dateString = dateFormat.format(Date())

            // 2. Update countdown
            if (endTime > 0) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining > 0) {
                    val totalSecs = remaining / 1000
                    val minutes = totalSecs / 60
                    val seconds = totalSecs % 60
                    countdownText = String.format("Remaining: %02d:%02d", minutes, seconds)
                } else {
                    countdownText = "Completed"
                    onDismiss()
                }
            } else {
                countdownText = "Duration: Until turned off"
            }

            kotlinx.coroutines.delay(1000)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // If swiped in any direction, dismiss
                    if (Math.abs(dragAmount.x) > 40 || Math.abs(dragAmount.y) > 40) {
                        onDismiss()
                    }
                }
            },
        color = Color(0xF4090D16) // Solid dark gradient color
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Time & Date
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = timeString,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White
                )
                Text(
                    text = dateString,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF94A3B8)
                )
            }

            // Samsung-like Music Control Widget in the center
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Arise Routine Active",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0EA5E9)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = routineName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Countdown text
                    Text(
                        text = countdownText,
                        fontSize = 16.sp,
                        color = Color(0xFF10B981), // Emerald green highlight for ticking timer
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Stop Routine Control Button
                        IconButton(
                            onClick = onStopRoutine,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0xFFEF4444)
                            ),
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        ) {
                            Text(
                                text = "✕",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Swipe Up to Unlock Indicator
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Swipe to unlock",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
