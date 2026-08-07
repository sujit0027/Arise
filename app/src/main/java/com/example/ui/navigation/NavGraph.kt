package com.example.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.viewmodel.AlarmViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: AlarmViewModel,
    modifier: Modifier = Modifier
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val wakeupLogs by viewModel.wakeupLogs.collectAsStateWithLifecycle()
    val totalWakeups by viewModel.totalWakeupCount.collectAsStateWithLifecycle()
    val avgDurationSeconds by viewModel.avgDurationSeconds.collectAsStateWithLifecycle()
    val activeRingingAlarm by viewModel.activeRingingAlarm.collectAsStateWithLifecycle()

    // Active ringing screen popup override
    if (activeRingingAlarm != null) {
        AlarmRingingScreen(
            alarm = activeRingingAlarm!!,
            onChallengeCompleted = { viewModel.completeActiveChallenge() },
            onDismissWithoutComplete = { viewModel.dismissRingingWithoutComplete() },
            modifier = modifier
        )
        return
    }

    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = modifier
    ) {
        composable("dashboard") {
            DashboardScreen(
                alarms = alarms,
                totalWakeups = totalWakeups,
                onToggleEnabled = { alarm, isEnabled -> viewModel.toggleAlarmEnabled(alarm, isEnabled) },
                onTestRinging = { alarm -> viewModel.startTestRinging(alarm) },
                onAddAlarm = { navController.navigate("edit_alarm/-1") },
                onEditAlarm = { alarm -> navController.navigate("edit_alarm/${alarm.id}") },
                onDeleteAlarm = { alarm -> viewModel.deleteAlarm(alarm) },
                onNavigateStats = { navController.navigate("stats") },
                onNavigatePrd = { navController.navigate("prd") }
            )
        }

        composable(
            route = "edit_alarm/{alarmId}",
            arguments = listOf(navArgument("alarmId") { type = NavType.LongType })
        ) { backStackEntry ->
            val alarmId = backStackEntry.arguments?.getLong("alarmId") ?: -1L
            val alarmToEdit = alarms.find { it.id == alarmId }

            AlarmEditScreen(
                alarmToEdit = alarmToEdit,
                onSaveAlarm = { alarm ->
                    viewModel.saveAlarm(alarm)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("stats") {
            StatsScreen(
                totalWakeups = totalWakeups,
                avgDurationSeconds = avgDurationSeconds,
                logs = wakeupLogs,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("prd") {
            PrdDocumentScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
