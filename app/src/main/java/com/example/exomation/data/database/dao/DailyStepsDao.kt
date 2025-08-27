package com.example.exomation.data.database.dao

import androidx.room.*
import com.example.exomation.data.database.entities.DailyStepsEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Data Access Object for daily steps operations
 */
@Dao
interface DailyStepsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(dailySteps: DailyStepsEntity)
    
    @Query("SELECT * FROM daily_steps WHERE date = :date")
    suspend fun getStepsForDate(date: Date): DailyStepsEntity?
    
    @Query("SELECT * FROM daily_steps WHERE date = :date")
    fun observeStepsForDate(date: Date): Flow<DailyStepsEntity?>
    
    @Query("SELECT * FROM daily_steps ORDER BY date DESC LIMIT :days")
    fun getRecentDays(days: Int): Flow<List<DailyStepsEntity>>
    
    @Query("SELECT * FROM daily_steps WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getStepsBetweenDates(startDate: Date, endDate: Date): Flow<List<DailyStepsEntity>>
    
    @Query("SELECT AVG(totalSteps) FROM daily_steps WHERE date >= :startDate")
    suspend fun getAverageStepsSince(startDate: Date): Float?
    
    @Query("SELECT MAX(totalSteps) FROM daily_steps")
    suspend fun getMaxStepsRecord(): Int?
    
    @Query("UPDATE daily_steps SET totalSteps = :steps, distance = :distance, caloriesBurned = :calories, lastUpdated = :lastUpdated WHERE date = :date")
    suspend fun updateSteps(date: Date, steps: Int, distance: Float, calories: Float, lastUpdated: Date)
    
    @Query("DELETE FROM daily_steps WHERE date < :beforeDate")
    suspend fun deleteOldRecords(beforeDate: Date)
}
