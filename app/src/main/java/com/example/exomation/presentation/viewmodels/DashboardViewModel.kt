package com.example.exomation.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exomation.data.repository.FitnessRepository
import com.example.exomation.domain.model.DailySteps
import com.example.exomation.domain.model.ExerciseGoal
import com.example.exomation.domain.model.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Dashboard screen
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: FitnessRepository
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    // Today's steps
    val todaySteps: StateFlow<DailySteps?> = repository.observeTodaySteps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    // Active goals
    val activeGoals: StateFlow<List<ExerciseGoal>> = repository.getActiveGoals()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Recent workouts
    val recentWorkouts: StateFlow<List<Workout>> = repository.getRecentWorkouts(5)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Weekly statistics
    private val _weeklyStats = MutableStateFlow(WeeklyStats())
    val weeklyStats: StateFlow<WeeklyStats> = _weeklyStats.asStateFlow()
    
    init {
        loadWeeklyStats()
        observeDataChanges()
    }
    
    private fun observeDataChanges() {
        // Combine multiple flows to update UI state
        combine(
            todaySteps,
            activeGoals,
            recentWorkouts
        ) { steps, goals, workouts ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                todaySteps = steps?.totalSteps ?: 0,
                todayDistance = steps?.distance ?: 0f,
                todayCalories = steps?.caloriesBurned ?: 0f,
                stepsGoalProgress = steps?.progressPercentage ?: 0f,
                activeGoalsCount = goals.count { it.isActive },
                completedGoalsCount = goals.count { it.isCompleted },
                recentWorkoutsCount = workouts.size
            )
        }.launchIn(viewModelScope)
    }
    
    private fun loadWeeklyStats() {
        viewModelScope.launch {
            try {
                val stats = repository.getWeeklyStats()
                _weeklyStats.value = WeeklyStats(
                    totalSteps = stats["steps"]?.toInt() ?: 0,
                    totalCalories = stats["calories"] ?: 0f,
                    totalDistance = stats["distance"] ?: 0f,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load weekly stats: ${e.message}"
                )
            }
        }
    }
    
    fun refreshData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            loadWeeklyStats()
        }
    }
    
    fun startStepTracking() {
        // This would trigger the step tracking service
        // Implementation depends on how you want to handle service lifecycle
        _uiState.value = _uiState.value.copy(isStepTrackingActive = true)
    }
    
    fun stopStepTracking() {
        _uiState.value = _uiState.value.copy(isStepTrackingActive = false)
    }
    
    fun updateGoalProgress(goalType: String, progress: Int) {
        viewModelScope.launch {
            try {
                repository.updateGoalProgress(goalType, progress)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update goal: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    // UI State classes
    data class DashboardUiState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val todaySteps: Int = 0,
        val todayDistance: Float = 0f,
        val todayCalories: Float = 0f,
        val stepsGoalProgress: Float = 0f,
        val activeGoalsCount: Int = 0,
        val completedGoalsCount: Int = 0,
        val recentWorkoutsCount: Int = 0,
        val isStepTrackingActive: Boolean = false
    )
    
    data class WeeklyStats(
        val totalSteps: Int = 0,
        val totalCalories: Float = 0f,
        val totalDistance: Float = 0f,
        val isLoading: Boolean = true
    )
}
