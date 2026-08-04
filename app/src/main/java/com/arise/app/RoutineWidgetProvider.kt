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
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, 
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

            val sharedPrefs = context.getSharedPreferences("ArisePrefs", Context.MODE_PRIVATE)
            val boundRoutineId = sharedPrefs.getString("widget_${appWidgetId}_routine_id", null) ?: return
            val activeId = sharedPrefs.getString("active_routine_id", null)

            Log.d("RoutineWidgetProvider", "Widget toggle clicked: id=$appWidgetId, boundRoutine=$boundRoutineId, active=$activeId")

            if (activeId == boundRoutineId) {
                // Stop the active routine
                val stopIntent = Intent(context, RoutineService::class.java).apply {
                    action = "STOP"
                }
                context.startService(stopIntent)
            } else {
                // Start this specific routine
                val serviceIntent = Intent(context, RoutineService::class.java).apply {
                    action = "START"
                    putExtra("routine_id", boundRoutineId)
                }
                context.startForegroundService(serviceIntent)
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
            val boundRoutineId = sharedPrefs.getString("widget_${appWidgetId}_routine_id", null)
            val activeRoutineId = sharedPrefs.getString("active_routine_id", null)
            val isRunning = boundRoutineId != null && activeRoutineId == boundRoutineId

            var routineName = "Arise"
            var iconEmoji = "⭐"

            if (boundRoutineId != null) {
                val routineJson = sharedPrefs.getString("routine_$boundRoutineId", null)
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

            // Click listener with Widget ID parameter
            val intent = Intent(context, RoutineWidgetProvider::class.java).apply {
                action = "com.arise.app.WIDGET_TOGGLE"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, intent, // Use appWidgetId as requestCode to keep intents unique
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_background, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
