package com.example.exomation.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppRoute(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Filled.Home),
    Exercise("exercise", "Exercise", Icons.Filled.FitnessCenter),
    History("history", "History", Icons.Filled.History),
    Settings("settings", "Settings", Icons.Filled.Settings)
}

