package com.example.exomation.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity for tracking daily step counts
 */
@Entity(tableName = "daily_steps")
data class DailyStepsEntity(
    @PrimaryKey
    val date: Date, // Date without time component
    val totalSteps: Int,
    val distance: Float, // in meters
    val caloriesBurned: Float,
    val activeMinutes: Int = 0,
    val goalSteps: Int = 10000,
    val lastUpdated: Date = Date()
)
