package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.data.model.RoutineAlarm
import com.example.ui.navigation.NavGraph
import com.example.ui.theme.RoutineGuardTheme
import com.example.ui.viewmodel.AlarmViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setupLockScreenFlags()
        handleRingingIntent(intent)

        setContent {
            RoutineGuardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupLockScreenFlags()
        handleRingingIntent(intent)
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun handleRingingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.getStringExtra("action")
        if (action == "RINGING_CHALLENGE") {
            val alarmId = intent.getLongExtra("alarmId", -1L)
            val routineName = intent.getStringExtra("routineName") ?: "Study Routine"
            val conditionText = intent.getStringExtra("conditionText") ?: "I am awake and ready to study"
            val gapInterval = intent.getIntExtra("gapInterval", 2)
            val maxRepeats = intent.getIntExtra("maxRepeats", 5)
            val wallpaperType = intent.getStringExtra("wallpaperType") ?: "preset_sunrise"
            val customWallpaperUri = intent.getStringExtra("customWallpaperUri")
            val overlayOpacity = intent.getFloatExtra("overlayOpacity", 0.5f)
            val blurIntensity = intent.getFloatExtra("blurIntensity", 10f)
            val strictCase = intent.getBooleanExtra("strictCase", false)

            val ringingAlarm = RoutineAlarm(
                id = alarmId,
                title = routineName,
                gapIntervalMinutes = gapInterval,
                maxRepeats = maxRepeats,
                conditionText = conditionText,
                wallpaperType = wallpaperType,
                customWallpaperUri = customWallpaperUri,
                overlayOpacity = overlayOpacity,
                blurIntensity = blurIntensity,
                strictCaseMatching = strictCase
            )

            viewModel.setRingingAlarm(ringingAlarm)
        }
    }
}
