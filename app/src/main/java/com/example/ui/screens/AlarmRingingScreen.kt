package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.RingtonePlayerManager
import com.example.data.model.RoutineAlarm
import com.example.ui.components.AwakeningTextChallenge
import com.example.ui.components.WallpaperBackground
import com.example.ui.theme.AmberLight
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlarmRingingScreen(
    alarm: RoutineAlarm,
    onChallengeCompleted: () -> Unit,
    onDismissWithoutComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTimeString by remember { mutableStateOf("") }
    var isChallengeDone by remember { mutableStateOf(false) }

    // Live clock ticker
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            currentTimeString = sdf.format(Date())
            delay(1000L)
        }
    }

    // Stop alarm sound when challenge is completed
    val handleStopAlarm = {
        isChallengeDone = true
        RingtonePlayerManager.getInstance(context).stop()
        onChallengeCompleted()
    }

    WallpaperBackground(
        wallpaperType = alarm.wallpaperType,
        customWallpaperUri = alarm.customWallpaperUri,
        overlayOpacity = alarm.overlayOpacity.coerceAtLeast(0.65f),
        blurIntensity = alarm.blurIntensity,
        modifier = modifier.testTag("alarm_ringing_screen")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Top Pulsing Alarm Status Header
                Surface(
                    color = Color(0xCCEF4444),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Ringing",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STRICT ROUTINE ALARM RINGING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // High-Contrast Glass Banner for Clock & Routine Info
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xCC0F172A),
                    shape = RoundedCornerShape(24.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF334155))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Big Digital Clock Display
                        Text(
                            text = currentTimeString.ifEmpty { "06:30:00 AM" },
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Routine Title & Gap Interval Badge
                        Text(
                            text = alarm.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberLight,
                            maxLines = 1,
                            softWrap = false
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Gap: ${alarm.gapIntervalMinutes} min | Continuous Retry Mode",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Awakening Text Challenge Component
                AwakeningTextChallenge(
                    targetText = alarm.conditionText,
                    strictCase = alarm.strictCaseMatching,
                    onChallengeCompleted = handleStopAlarm
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Completion Overlay Animation
            AnimatedVisibility(visible = isChallengeDone) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = SuccessGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "GREAT JOB!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "You verified your awakening for ${alarm.title}!",
                            fontSize = 14.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}
