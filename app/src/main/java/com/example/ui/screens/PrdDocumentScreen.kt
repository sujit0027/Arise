package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.AmberLight

const val PRD_FULL_TEXT = """# PRODUCT REQUIREMENTS DOCUMENT (PRD)

## 1. Executive Summary & Product Vision
Routine Guard is a strict routine-enforcement alarm clock application engineered for students, exam aspirants, and professionals who struggle with morning procrastination or missing key study blocks. Traditional alarm apps allow users to easily press 'Snooze' or dismiss alarms while half-asleep. Routine Guard eliminates this weakness by requiring users to pass an Awakening Challenge (exact sentence text matching) on top of customizable motivational wallpapers before the alarm sound can be silenced.

## 2. User Personas
- **Persona A: Alex (Competitive Exam Aspirant - 21 yrs)**
  - *Goal:* Wake up at 6:00 AM daily for 3 hours of undisturbed problem-solving practice.
  - *Pain Point:* Habitually hits snooze 4 times, waking up at 7:30 AM feeling guilty and behind schedule.
  - *Solution:* Must type "Math requires focus, logic, and persistence" to stop the alarm.
- **Persona B: Sarah (Remote Professional & Part-time Student - 28 yrs)**
  - *Goal:* Complete daily vocabulary and coding drills before work.
  - *Pain Point:* Dismisses standard phone alarm while asleep without realizing it.
  - *Solution:* Continuous interval gap repeats every 2 minutes with custom motivational wallpaper.

## 3. User Stories (Agile Format)
- **US-1 (Routine Setup):** As a student, I want to create named routine alarms (e.g. 'Math Practice') with repeat days, so that my study timetable runs automatically every week.
- **US-2 (Interval Gap Repeat):** As a heavy sleeper, I want the alarm to repeat continuously after a defined interval (e.g., 2 minutes) if ignored, so that I cannot sleep through my study block.
- **US-3 (Awakening Challenge):** As a user, I want the 'Stop Alarm' button to remain strictly disabled until I type a predefined sentence exactly as shown, so that I prove I am awake and alert.
- **US-4 (Motivational Wallpaper):** As a user, I want custom motivational background images with dark/blur overlay on the alarm screen, so that I feel inspired as soon as I open my eyes.

## 4. Functional Requirements
1. **Routine & Alarm Setup:**
   - Configure exact alarm time (HH:MM AM/PM).
   - Assign routine name (e.g. 'Math Practice', 'DSA Drill').
   - Select repeating days (Sun, Mon, Tue, Wed, Thu, Fri, Sat).
   - Toggle routine alarm active/inactive.
2. **Interval & Repeat Logic (The Gap):**
   - User-defined gap interval (1 to 15 minutes).
   - Continuous retry triggers if the alarm is ignored or closed without solving the challenge up to a configurable maximum repeat limit.
3. **The Awakening Value (Condition Text):**
   - Predefined condition text sentence per alarm (e.g., "I am awake and ready to study").
   - Real-time character matching with green (correct) / red (typo) feedback.
   - **Mandatory Constraint:** The 'Stop Alarm' button is strictly disabled until input text matches target sentence exactly.
4. **Motivational Alarm Wallpaper:**
   - Preset motivational visual art (Dawn Summit, Deep Focus, Cyber Drive, Teal Calm).
   - Ability to upload custom gallery photo from local storage.
   - Customizable dark overlay opacity (20% to 85%) and blur intensity (0 to 25 dp) for readability.

## 5. Non-Functional Requirements & Technical Architecture
- **Android Constraints:**
  - Uses `AlarmManager.setExactAndAllowWhileIdle` with `SCHEDULE_EXACT_ALARM` permissions to ensure reliable execution even in Doze Mode.
  - Fullscreen Intent Notification with high-priority channel (`IMPORTANCE_HIGH`) launching the ringing challenge view.
  - Local database persistence via Room DB and `Flow<List<RoutineAlarm>>` for reactive UI updates.
- **iOS Architectural Parity:**
  - Uses `UNUserNotificationCenter` for local notification scheduling and UserNotifications framework with custom Category Actions for full-screen alarm invocation.
- **Performance:** Ringing screen load latency < 100ms; instant character-by-character UI diffing.

## 6. Future Enhancements
- Dynamic randomized awakening sentences (e.g., randomized math equations, logic puzzles).
- Step counter sensor challenge (require 20 physical steps before unlocking).
- Cloud study group routine sharing & accountability partner alerts.
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrdDocumentScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val copyToClipboard = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Routine Guard PRD", PRD_FULL_TEXT)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "PRD copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Product Requirements Document",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("prd_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = copyToClipboard,
                        modifier = Modifier.testTag("copy_prd_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy PRD",
                            tint = AmberLight
                        )
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
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = AmberPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "PRD SPECIFICATION v1.0",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Button(
                            onClick = copyToClipboard,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Copy Text", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = PRD_FULL_TEXT,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
