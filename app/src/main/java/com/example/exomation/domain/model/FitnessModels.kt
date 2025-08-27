package com.example.exomation.domain.model

import java.util.Date

/**
 * Domain models for the fitness tracker app
 */

data class Workout(
    val id: Long = 0,
    val date: Date,
    val type: ExerciseType,
    val duration: Int,
    val steps: Int = 0,
    val distance: Float = 0f,
    val caloriesBurned: Float = 0f,
    val repetitions: Int = 0,
    val averageHeartRate: Int? = null,
    val notes: String? = null,
    val isCompleted: Boolean = true
)

enum class ExerciseType {
    NONE,
    WALKING,
    RUNNING,
    SQUATS,
    PUSHUPS,
    KICKS,
    BICEP_CURLS,
    LUNGES,
    PLANK,
    MIXED_EXERCISE,
    OTHER
}

data class DailySteps(
    val date: Date,
    val totalSteps: Int,
    val distance: Float,
    val caloriesBurned: Float,
    val activeMinutes: Int = 0,
    val goalSteps: Int = 10000,
    val lastUpdated: Date = Date()
) {
    val progressPercentage: Float
        get() = (totalSteps.toFloat() / goalSteps.toFloat() * 100).coerceAtMost(100f)
    
    val isGoalReached: Boolean
        get() = totalSteps >= goalSteps
}

data class ExerciseGoal(
    val exerciseType: String,
    val targetValue: Int,
    val currentValue: Int = 0,
    val frequency: Frequency = Frequency.DAILY,
    val isActive: Boolean = true
) {
    val progressPercentage: Float
        get() = if (targetValue > 0) {
            (currentValue.toFloat() / targetValue.toFloat() * 100).coerceAtMost(100f)
        } else 0f
    
    val isCompleted: Boolean
        get() = currentValue >= targetValue
}

enum class Frequency {
    DAILY,
    WEEKLY,
    MONTHLY
}

data class StepData(
    val steps: Int,
    val distance: Float, // in meters
    val calories: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class PoseDetectionResult(
    val exerciseType: ExerciseType,
    val repetitions: Int,
    val confidence: Float,
    val landmarks: List<PoseLandmark>? = null
)

data class PoseLandmark(
    val type: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
)

data class ExerciseSession(
    val exerciseType: ExerciseType,
    val startTime: Date,
    val endTime: Date? = null,
    val repetitions: Int = 0,
    val caloriesBurned: Float = 0f,
    val isActive: Boolean = true
)

data class UserProfile(
    val name: String = "",
    val age: Int = 25,
    val weight: Float = 70f, // in kg
    val height: Float = 170f, // in cm
    val stepLength: Float = 0.75f // in meters
) {
    fun calculateCaloriesFromSteps(steps: Int): Float {
        // Simple calorie calculation: ~0.04 calories per step for average person
        val caloriesPerStep = weight * 0.0004f
        return steps * caloriesPerStep
    }
    
    fun calculateDistanceFromSteps(steps: Int): Float {
        return steps * stepLength
    }
}
