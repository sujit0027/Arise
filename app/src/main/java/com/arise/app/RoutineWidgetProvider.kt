package com.arise.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews

class RoutineWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.arise.app.WIDGET_TOGGLE") {
            Log.d("RoutineWidgetProvider", "Widget toggle clicked!")
            val sharedPrefs = context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
            val activeId = sharedPrefs.getString("active_routine_id", null)
            
            if (activeId != null) {
                // Stop the active routine
                val stopIntent = Intent(context, RoutineService::class.java).apply {
                    action = "STOP"
                }
                context.startService(stopIntent)
            } else {
                // Start the default manual routine
                val routineJson = sharedPrefs.getString("routine_arise_default", null)
                if (routineJson != null) {
                    val serviceIntent = Intent(context, RoutineService::class.java).apply {
                        action = "START"
                        putExtra("routine_id", "arise_default")
                    }
                    context.startForegroundService(serviceIntent)
                }
            }

            // Trigger an update to all widgets immediately
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, RoutineWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, allWidgetIds)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val sharedPrefs = context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
            val activeRoutineId = sharedPrefs.getString("active_routine_id", null)
            val isRunning = activeRoutineId != null

            // Load default routine details to show on widget
            val routineJson = sharedPrefs.getString("routine_arise_default", null)
            var routineName = "Arise"
            var iconEmoji = "⭐"

            if (routineJson != null) {
                try {
                    val routine = RoutineModel.fromJson(routineJson)
                    routineName = routine.name
                    iconEmoji = when (routine.iconResName) {
                        "star" -> "⭐"
                        "book" -> "📖"
                        "bed" -> "🛌"
                        "gym" -> "🏋️"
                        "music" -> "🎧"
                        "work" -> "💼"
                        else -> "⭐"
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.widget_icon, iconEmoji)
            views.setTextViewText(R.id.widget_name, routineName)

            // Dynamic background highlighting on/off state
            if (isRunning) {
                views.setInt(R.id.widget_background, "setBackgroundColor", Color.parseColor("#FF1D4ED8")) // Dark blue highlight
            } else {
                views.setInt(R.id.widget_background, "setBackgroundResource", R.drawable.widget_bg) // Normal AMOLED gray
            }

            // Click listener
            val intent = Intent(context, RoutineWidgetProvider::class.java).apply {
                action = "com.arise.app.WIDGET_TOGGLE"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_background, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
