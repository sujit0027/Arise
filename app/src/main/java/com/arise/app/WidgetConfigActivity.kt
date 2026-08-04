package com.arise.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED. This will cause the widget host to cancel
        // the placement of the widget if the user presses the back button.
        setResult(RESULT_CANCELED)

        // Find the widget ID from the intent.
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                WidgetConfigScreen(
                    onRoutineSelected = { routineId ->
                        saveWidgetBinding(routineId)
                    }
                )
            }
        }
    }

    private fun saveWidgetBinding(routineId: String) {
        val sharedPrefs = getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("widget_${appWidgetId}_routine_id", routineId).apply()

        // Update the widget immediately
        val appWidgetManager = AppWidgetManager.getInstance(this)
        RoutineWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)

        // Send back Widget ID OK
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        Log.d("WidgetConfig", "Saved widget binding: appWidgetId=$appWidgetId -> routineId=$routineId")
        finish()
    }
}

@Composable
fun WidgetConfigScreen(onRoutineSelected: (String) -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE) }
    val routines = remember { mutableStateListOf<RoutineModel>() }

    LaunchedEffect(Unit) {
        val keys = sharedPrefs.all.keys
        routines.clear()
        keys.forEach { key ->
            if (key.startsWith("routine_") && key != "routine_arise_default") {
                val jsonStr = sharedPrefs.getString(key, null)
                if (jsonStr != null) {
                    try {
                        routines.add(RoutineModel.fromJson(jsonStr))
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F1014)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bind Widget to Routine",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Select which routine this home screen widget will trigger.",
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            if (routines.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = "No routines found. Create one first!", color = Color(0xFFEF4444))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(routines) { item ->
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
                                .clickable { onRoutineSelected(item.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2026))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = iconEmoji, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = item.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
