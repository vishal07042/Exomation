package com.example.exomation.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing user's exercise goals
 */
@Entity(tableName = "exercise_goals")
data class ExerciseGoalEntity(
    @PrimaryKey
    val exerciseType: String, // "DAILY_STEPS", "SQUATS", "PUSHUPS", "KICKS"
    val targetValue: Int,
    val currentValue: Int = 0,
    val frequency: GoalFrequency = GoalFrequency.DAILY,
    val isActive: Boolean = true
)

enum class GoalFrequency {
    DAILY,
    WEEKLY,
    MONTHLY
}
