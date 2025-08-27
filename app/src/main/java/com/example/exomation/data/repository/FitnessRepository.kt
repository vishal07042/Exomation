package com.example.exomation.data.repository

import com.example.exomation.data.database.dao.DailyStepsDao
import com.example.exomation.data.database.dao.ExerciseGoalDao
import com.example.exomation.data.database.dao.WorkoutDao
import com.example.exomation.data.database.entities.*
import com.example.exomation.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main repository for managing fitness data
 */
@Singleton
class FitnessRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val dailyStepsDao: DailyStepsDao,
    private val exerciseGoalDao: ExerciseGoalDao
) {
    
    // Workout operations
    suspend fun saveWorkout(workout: Workout): Long {
        return workoutDao.insertWorkout(workout.toEntity())
    }
    
    suspend fun updateWorkout(workout: Workout) {
        workoutDao.updateWorkout(workout.toEntity())
    }
    
    suspend fun deleteWorkout(workout: Workout) {
        workoutDao.deleteWorkout(workout.toEntity())
    }
    
    suspend fun getWorkoutById(id: Long): Workout? {
        return workoutDao.getWorkoutById(id)?.toDomainModel()
    }
    
    fun getAllWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    fun getRecentWorkouts(limit: Int = 10): Flow<List<Workout>> {
        val thirtyDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
        }.time
        
        return workoutDao.getRecentWorkouts(thirtyDaysAgo, limit).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    // Daily Steps operations
    suspend fun saveDailySteps(dailySteps: DailySteps) {
        dailyStepsDao.insertOrUpdate(dailySteps.toEntity())
    }
    
    suspend fun getTodaySteps(): DailySteps? {
        val today = getDateWithoutTime(Date())
        return dailyStepsDao.getStepsForDate(today)?.toDomainModel()
    }
    
    fun observeTodaySteps(): Flow<DailySteps?> {
        val today = getDateWithoutTime(Date())
        return dailyStepsDao.observeStepsForDate(today).map { entity ->
            entity?.toDomainModel()
        }
    }
    
    fun getRecentDaysSteps(days: Int): Flow<List<DailySteps>> {
        return dailyStepsDao.getRecentDays(days).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    suspend fun updateTodaySteps(steps: Int, distance: Float, calories: Float) {
        val today = getDateWithoutTime(Date())
        val existingSteps = dailyStepsDao.getStepsForDate(today)
        
        if (existingSteps != null) {
            dailyStepsDao.updateSteps(today, steps, distance, calories, Date())
        } else {
            dailyStepsDao.insertOrUpdate(
                DailyStepsEntity(
                    date = today,
                    totalSteps = steps,
                    distance = distance,
                    caloriesBurned = calories,
                    lastUpdated = Date()
                )
            )
        }
    }
    
    // Exercise Goals operations
    suspend fun saveExerciseGoal(goal: ExerciseGoal) {
        exerciseGoalDao.insertOrUpdateGoal(goal.toEntity())
    }
    
    suspend fun getGoalByType(type: String): ExerciseGoal? {
        return exerciseGoalDao.getGoalByType(type)?.toDomainModel()
    }
    
    fun getActiveGoals(): Flow<List<ExerciseGoal>> {
        return exerciseGoalDao.getActiveGoals().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    suspend fun updateGoalProgress(type: String, value: Int) {
        exerciseGoalDao.updateProgress(type, value)
    }
    
    suspend fun incrementGoalProgress(type: String, increment: Int) {
        exerciseGoalDao.incrementProgress(type, increment)
    }
    
    suspend fun resetDailyGoals() {
        val dailyGoals = listOf("DAILY_STEPS", "SQUATS", "PUSHUPS", "KICKS")
        dailyGoals.forEach { type ->
            exerciseGoalDao.resetProgress(type)
        }
    }
    
    // Statistics
    suspend fun getWeeklyStats(): Map<String, Float> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startDate = calendar.time
        
        val totalSteps = workoutDao.getTotalStepsBetweenDates(startDate, endDate) ?: 0
        val totalCalories = workoutDao.getTotalCaloriesBetweenDates(startDate, endDate) ?: 0f
        val totalDistance = workoutDao.getTotalDistanceBetweenDates(startDate, endDate) ?: 0f
        
        return mapOf(
            "steps" to totalSteps.toFloat(),
            "calories" to totalCalories,
            "distance" to totalDistance
        )
    }
    
    suspend fun getExerciseStats(type: WorkoutType): Map<String, Int> {
        val calendar = Calendar.getInstance()
        val today = calendar.time
        
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekAgo = calendar.time
        
        calendar.add(Calendar.DAY_OF_YEAR, -23) // Total: -30 days
        val monthAgo = calendar.time
        
        val weekCount = workoutDao.getExerciseCountSince(type, weekAgo)
        val monthCount = workoutDao.getExerciseCountSince(type, monthAgo)
        val weekReps = workoutDao.getTotalRepetitionsSince(type, weekAgo) ?: 0
        val monthReps = workoutDao.getTotalRepetitionsSince(type, monthAgo) ?: 0
        
        return mapOf(
            "weekSessions" to weekCount,
            "monthSessions" to monthCount,
            "weekReps" to weekReps,
            "monthReps" to monthReps
        )
    }
    
    // Helper functions
    private fun getDateWithoutTime(date: Date): Date {
        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.time
    }
    
    // Extension functions for converting between entities and domain models
    private fun WorkoutEntity.toDomainModel(): Workout {
        return Workout(
            id = id,
            date = date,
            type = ExerciseType.valueOf(type.name),
            duration = (duration / 1000L).toInt(),
            steps = steps,
            distance = distance,
            caloriesBurned = caloriesBurned,
            repetitions = repetitions,
            averageHeartRate = averageHeartRate,
            notes = notes,
            isCompleted = isCompleted
        )
    }
    
    private fun Workout.toEntity(): WorkoutEntity {
        return WorkoutEntity(
            id = id,
            date = date,
            type = WorkoutType.valueOf(type.name),
            duration = duration.toLong() * 1000L,
            steps = steps,
            distance = distance,
            caloriesBurned = caloriesBurned,
            repetitions = repetitions,
            averageHeartRate = averageHeartRate,
            notes = notes,
            isCompleted = isCompleted
        )
    }
    
    private fun DailyStepsEntity.toDomainModel(): DailySteps {
        return DailySteps(
            date = date,
            totalSteps = totalSteps,
            distance = distance,
            caloriesBurned = caloriesBurned,
            activeMinutes = activeMinutes,
            goalSteps = goalSteps,
            lastUpdated = lastUpdated
        )
    }
    
    private fun DailySteps.toEntity(): DailyStepsEntity {
        return DailyStepsEntity(
            date = date,
            totalSteps = totalSteps,
            distance = distance,
            caloriesBurned = caloriesBurned,
            activeMinutes = activeMinutes,
            goalSteps = goalSteps,
            lastUpdated = lastUpdated
        )
    }
    
    private fun ExerciseGoalEntity.toDomainModel(): ExerciseGoal {
        return ExerciseGoal(
            exerciseType = exerciseType,
            targetValue = targetValue,
            currentValue = currentValue,
            frequency = Frequency.valueOf(frequency.name),
            isActive = isActive
        )
    }
    
    private fun ExerciseGoal.toEntity(): ExerciseGoalEntity {
        return ExerciseGoalEntity(
            exerciseType = exerciseType,
            targetValue = targetValue,
            currentValue = currentValue,
            frequency = GoalFrequency.valueOf(frequency.name),
            isActive = isActive
        )
    }
}
