package com.example.exomation.di

import android.content.Context
import com.example.exomation.data.database.FitnessDatabase
import com.example.exomation.data.repository.FitnessRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FitnessDatabase =
        FitnessDatabase.getInstance(context)

    @Provides
    fun provideWorkoutDao(db: FitnessDatabase) = db.workoutDao()

    @Provides
    fun provideDailyStepsDao(db: FitnessDatabase) = db.dailyStepsDao()

    @Provides
    fun provideExerciseGoalDao(db: FitnessDatabase) = db.exerciseGoalDao()

    @Provides
    @Singleton
    fun provideRepository(db: FitnessDatabase): FitnessRepository =
        FitnessRepository(
            workoutDao = db.workoutDao(),
            dailyStepsDao = db.dailyStepsDao(),
            exerciseGoalDao = db.exerciseGoalDao()
        )
}


