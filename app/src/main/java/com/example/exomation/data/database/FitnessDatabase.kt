package com.example.exomation.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.example.exomation.data.database.converters.DateConverters
import com.example.exomation.data.database.dao.DailyStepsDao
import com.example.exomation.data.database.dao.ExerciseGoalDao
import com.example.exomation.data.database.dao.WorkoutDao
import com.example.exomation.data.database.entities.DailyStepsEntity
import com.example.exomation.data.database.entities.ExerciseGoalEntity
import com.example.exomation.data.database.entities.WorkoutEntity

/**
 * Main Room database for the fitness tracker app
 */
@Database(
    entities = [
        WorkoutEntity::class,
        DailyStepsEntity::class,
        ExerciseGoalEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class FitnessDatabase : RoomDatabase() {
    
    abstract fun workoutDao(): WorkoutDao
    abstract fun dailyStepsDao(): DailyStepsDao
    abstract fun exerciseGoalDao(): ExerciseGoalDao
    
    companion object {
        @Volatile
        private var INSTANCE: FitnessDatabase? = null
        
        fun getInstance(context: Context): FitnessDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FitnessDatabase::class.java,
                    "fitness_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
