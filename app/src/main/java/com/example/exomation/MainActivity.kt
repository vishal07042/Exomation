package com.example.exomation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.exomation.ui.theme.ExoMationTheme
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.exomation.ui.navigation.AppRoute
import com.example.exomation.ui.screens.DashboardScreen
import com.example.exomation.ui.screens.ExerciseScreen
import com.example.exomation.ui.screens.ExerciseCameraScreen
import com.example.exomation.ui.screens.HistoryScreen
import com.example.exomation.ui.screens.HistoryScreenBound
import com.example.exomation.ui.screens.SettingsScreen
import com.example.exomation.services.StepCounterService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExoMationTheme {
                val navController = rememberNavController()
                val items = listOf(
                    AppRoute.Dashboard,
                    AppRoute.Exercise,
                    AppRoute.History,
                    AppRoute.Settings
                )
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val current = navBackStackEntry?.destination?.route
                            items.forEach { item ->
                                NavigationBarItem(
                                    selected = current == item.route,
                                    onClick = {
                                        if (current != item.route) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Dashboard.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(AppRoute.Dashboard.route) {
                            DashboardScreen(
                                onStartSteps = { StepCounterService.startService(this@MainActivity) },
                                onStartExercise = { navController.navigate(AppRoute.Exercise.route) }
                            )
                        }
                        composable(AppRoute.Exercise.route) { ExerciseCameraScreen() }
                        composable(AppRoute.History.route) { HistoryScreenBound() }
                        composable(AppRoute.Settings.route) { SettingsScreen() }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewApp() {
    ExoMationTheme { Text("Preview") }
}