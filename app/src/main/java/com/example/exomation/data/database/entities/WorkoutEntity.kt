package com.example.exomation.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity representing a workout session in the database
 */
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Date,
    val type: WorkoutType,
    val duration: Long, // in milliseconds
    val steps: Int = 0,
    val distance: Float = 0f, // in meters
    val caloriesBurned: Float = 0f,
    val repetitions: Int = 0, // for exercises like squats, pushups
    val averageHeartRate: Int? = null,
    val notes: String? = null,
    val isCompleted: Boolean = true
)

enum class WorkoutType {
    WALKING,
    RUNNING,
    SQUATS,
    PUSHUPS,
    KICKS,
    MIXED_EXERCISE,
    OTHER
}
