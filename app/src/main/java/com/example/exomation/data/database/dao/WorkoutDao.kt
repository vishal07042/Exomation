package com.example.exomation.data.database.dao

import androidx.room.*
import com.example.exomation.data.database.entities.WorkoutEntity
import com.example.exomation.data.database.entities.WorkoutType
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Data Access Object for workout operations
 */
@Dao
interface WorkoutDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long
    
    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)
    
    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity)
    
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutById(workoutId: Long): WorkoutEntity?
    
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getWorkoutsBetweenDates(startDate: Date, endDate: Date): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE type = :type ORDER BY date DESC")
    fun getWorkoutsByType(type: WorkoutType): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE date >= :date ORDER BY date DESC LIMIT :limit")
    fun getRecentWorkouts(date: Date, limit: Int = 10): Flow<List<WorkoutEntity>>
    
    @Query("SELECT SUM(steps) FROM workouts WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalStepsBetweenDates(startDate: Date, endDate: Date): Int?
    
    @Query("SELECT SUM(caloriesBurned) FROM workouts WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalCaloriesBetweenDates(startDate: Date, endDate: Date): Float?
    
    @Query("SELECT SUM(distance) FROM workouts WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalDistanceBetweenDates(startDate: Date, endDate: Date): Float?
    
    @Query("SELECT COUNT(*) FROM workouts WHERE type = :type AND date >= :startDate")
    suspend fun getExerciseCountSince(type: WorkoutType, startDate: Date): Int
    
    @Query("SELECT SUM(repetitions) FROM workouts WHERE type = :type AND date >= :startDate")
    suspend fun getTotalRepetitionsSince(type: WorkoutType, startDate: Date): Int?
}
