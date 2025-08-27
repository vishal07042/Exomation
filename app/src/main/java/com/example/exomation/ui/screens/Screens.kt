package com.example.exomation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.Manifest
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.example.exomation.presentation.viewmodels.DashboardViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DashboardScreen(
    onStartSteps: () -> Unit,
    onStartExercise: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Your Fitness Today", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxSize().weight(1f)) {
            Column(Modifier.padding(16.dp)) {
                Text("Steps: ${uiState.todaySteps}")
                Text("Distance: ${"%.1f".format(uiState.todayDistance)} m")
                Text("Calories: ${"%.0f".format(uiState.todayCalories)} kcal")
            }
        }
        Button(onClick = {
            val allGranted = permissions.permissions.all { it.status.isGranted }
            if (!allGranted) {
                permissions.launchMultiplePermissionRequest()
            } else onStartSteps()
        }) { Text("Start Step Tracking") }
        Button(onClick = onStartExercise) { Text("Start Exercise Tracking") }
    }
}

@Composable
fun ExerciseScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Exercise camera coming next…")
    }
}

@Composable
fun HistoryScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No workouts yet")
    }
}

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Settings")
    }
}


