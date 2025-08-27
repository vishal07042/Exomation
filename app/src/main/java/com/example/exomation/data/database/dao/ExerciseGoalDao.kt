package com.example.exomation.data.database.dao

import androidx.room.*
import com.example.exomation.data.database.entities.ExerciseGoalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for exercise goals
 */
@Dao
interface ExerciseGoalDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGoal(goal: ExerciseGoalEntity)
    
    @Query("SELECT * FROM exercise_goals WHERE exerciseType = :type")
    suspend fun getGoalByType(type: String): ExerciseGoalEntity?
    
    @Query("SELECT * FROM exercise_goals WHERE isActive = 1")
    fun getActiveGoals(): Flow<List<ExerciseGoalEntity>>
    
    @Query("SELECT * FROM exercise_goals")
    fun getAllGoals(): Flow<List<ExerciseGoalEntity>>
    
    @Query("UPDATE exercise_goals SET currentValue = :value WHERE exerciseType = :type")
    suspend fun updateProgress(type: String, value: Int)
    
    @Query("UPDATE exercise_goals SET currentValue = currentValue + :increment WHERE exerciseType = :type")
    suspend fun incrementProgress(type: String, increment: Int)
    
    @Query("UPDATE exercise_goals SET isActive = :active WHERE exerciseType = :type")
    suspend fun setGoalActive(type: String, active: Boolean)
    
    @Query("UPDATE exercise_goals SET currentValue = 0")
    suspend fun resetAllProgress()
    
    @Query("UPDATE exercise_goals SET currentValue = 0 WHERE exerciseType = :type")
    suspend fun resetProgress(type: String)
    
    @Delete
    suspend fun deleteGoal(goal: ExerciseGoalEntity)
}
