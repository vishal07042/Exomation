package com.example.exomation.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.exomation.presentation.viewmodels.DashboardViewModel

@Composable
fun HistoryScreenBound(vm: DashboardViewModel = hiltViewModel()) {
    val workouts by vm.recentWorkouts.collectAsState()
    Column(Modifier.fillMaxSize()) {
        LazyColumn {
            items(workouts) { w ->
                Text("${w.date} • ${w.type} • reps=${w.repetitions} • steps=${w.steps}")
            }
        }
    }
}


