package com.example.exomation.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exomation.data.repository.FitnessRepository
import com.example.exomation.domain.model.ExerciseType
import com.example.exomation.domain.model.Workout
import com.example.exomation.services.pose.PoseDetectionAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val repository: FitnessRepository
) : ViewModel() {

    private var lastReps = 0
    private var currentType: ExerciseType = ExerciseType.NONE
    private var sessionStart = System.currentTimeMillis()
    private var lastNonNoneType: ExerciseType = ExerciseType.NONE
    private var selectedExercise: ExerciseType? = null

    fun setSelectedExercise(type: ExerciseType?) {
        selectedExercise = type
        // reset session counters when switching exercise
        lastReps = 0
        sessionStart = System.currentTimeMillis()
    }

    fun onExerciseUpdate(type: PoseDetectionAnalyzer.ExerciseType, repetitions: Int, confidence: Float) {
        // Map analyzer type to domain type and remember last non-NONE
        val mappedType = when (type) {
            PoseDetectionAnalyzer.ExerciseType.SQUATS -> ExerciseType.SQUATS
            PoseDetectionAnalyzer.ExerciseType.PUSHUPS -> ExerciseType.PUSHUPS
            PoseDetectionAnalyzer.ExerciseType.KICKS -> ExerciseType.KICKS
            PoseDetectionAnalyzer.ExerciseType.BICEP_CURLS -> ExerciseType.BICEP_CURLS
            PoseDetectionAnalyzer.ExerciseType.LUNGES -> ExerciseType.LUNGES
            PoseDetectionAnalyzer.ExerciseType.PLANK -> ExerciseType.PLANK
            PoseDetectionAnalyzer.ExerciseType.JUMPING_JACKS -> ExerciseType.OTHER
            PoseDetectionAnalyzer.ExerciseType.NONE -> ExerciseType.NONE
        }
        if (mappedType != ExerciseType.NONE) {
            currentType = mappedType
            lastNonNoneType = mappedType
        }
        val gateOk = selectedExercise == null || lastNonNoneType == selectedExercise
        if (repetitions > lastReps && lastNonNoneType != ExerciseType.NONE && gateOk) {
            val delta = repetitions - lastReps
            lastReps = repetitions
            viewModelScope.launch {
                // Increment goal progress
                repository.incrementGoalProgress(lastNonNoneType.name, delta)
                // Save/update a simple workout snapshot
                repository.saveWorkout(
                    Workout(
                        id = 0,
                        date = Date(),
                        type = lastNonNoneType,
                        duration = ((System.currentTimeMillis() - sessionStart) / 1000).toInt(),
                        steps = 0,
                        distance = 0f,
                        caloriesBurned = 0f,
                        repetitions = repetitions,
                        averageHeartRate = 0,
                        notes = "Auto-logged",
                        isCompleted = false
                    )
                )
            }
        }
    }
}


