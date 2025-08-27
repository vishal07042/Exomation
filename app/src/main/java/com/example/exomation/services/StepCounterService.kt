package com.example.exomation.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.exomation.R
import com.example.exomation.data.repository.FitnessRepository
import com.example.exomation.domain.model.UserProfile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground service for continuous step tracking
 */
@AndroidEntryPoint
class StepCounterService : Service(), SensorEventListener {
    
    companion object {
        private const val TAG = "StepCounterService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "step_counter_channel"
        private const val ACTION_START = "com.example.exomation.action.START_STEP_COUNTER"
        private const val ACTION_STOP = "com.example.exomation.action.STOP_STEP_COUNTER"
        
        fun startService(context: Context) {
            val intent = Intent(context, StepCounterService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, StepCounterService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
    
    @Inject
    lateinit var repository: FitnessRepository
    
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Step tracking variables
    private var initialStepCount = -1
    private var currentSteps = 0
    private var lastSavedSteps = 0
    private var sessionStartTime = System.currentTimeMillis()
    
    // User profile for calculations
    private val userProfile = UserProfile() // TODO: Load from preferences
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        setupSensors()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startTracking()
            }
            ACTION_STOP -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopTracking()
        serviceScope.cancel()
    }
    
    private fun setupSensors() {
        // Try to get TYPE_STEP_COUNTER first (more battery efficient)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        // Fall back to TYPE_STEP_DETECTOR if counter not available
        if (stepCounterSensor == null) {
            stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        }
        
        if (stepCounterSensor == null && stepDetectorSensor == null) {
            Log.e(TAG, "No step sensors available on this device")
        }
    }
    
    private fun startTracking() {
        Log.d(TAG, "Starting step tracking")
        
        stepCounterSensor?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Registered TYPE_STEP_COUNTER sensor")
        } ?: stepDetectorSensor?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Registered TYPE_STEP_DETECTOR sensor")
        }
        
        // Start periodic save job
        startPeriodicSave()
        
        // Load today's existing steps
        loadTodaySteps()
    }
    
    private fun stopTracking() {
        Log.d(TAG, "Stopping step tracking")
        sensorManager.unregisterListener(this)
        
        // Save final steps before stopping
        saveCurrentSteps()
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    handleStepCounter(it.values[0].toInt())
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    handleStepDetector()
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: $accuracy")
    }
    
    private fun handleStepCounter(totalSteps: Int) {
        if (initialStepCount == -1) {
            // First reading, initialize
            initialStepCount = totalSteps
            Log.d(TAG, "Initial step count: $initialStepCount")
        } else {
            // Calculate steps since service started
            val stepsSinceStart = totalSteps - initialStepCount
            
            // Handle device reboot (counter resets)
            if (stepsSinceStart < 0) {
                initialStepCount = totalSteps
                currentSteps = 0
            } else {
                currentSteps = stepsSinceStart
            }
            
            Log.v(TAG, "Current steps: $currentSteps")
            updateNotification()
        }
    }
    
    private fun handleStepDetector() {
        // Each event is a single step
        currentSteps++
        Log.v(TAG, "Step detected. Total: $currentSteps")
        updateNotification()
    }
    
    private fun loadTodaySteps() {
        serviceScope.launch {
            try {
                val todaySteps = repository.getTodaySteps()
                lastSavedSteps = todaySteps?.totalSteps ?: 0
                
                // If using step detector, add to existing steps
                if (stepDetectorSensor != null && stepCounterSensor == null) {
                    currentSteps = lastSavedSteps
                }
                
                Log.d(TAG, "Loaded today's steps: $lastSavedSteps")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading today's steps", e)
            }
        }
    }
    
    private fun saveCurrentSteps() {
        serviceScope.launch {
            try {
                val totalStepsToday = if (stepCounterSensor != null) {
                    lastSavedSteps + currentSteps
                } else {
                    currentSteps // For step detector
                }
                
                val distance = userProfile.calculateDistanceFromSteps(totalStepsToday)
                val calories = userProfile.calculateCaloriesFromSteps(totalStepsToday)
                
                repository.updateTodaySteps(totalStepsToday, distance, calories)
                
                Log.d(TAG, "Saved steps: $totalStepsToday, distance: $distance, calories: $calories")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving steps", e)
            }
        }
    }
    
    private fun startPeriodicSave() {
        serviceScope.launch {
            while (isActive) {
                delay(60_000) // Save every minute
                saveCurrentSteps()
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows current step count"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val totalSteps = if (stepCounterSensor != null) {
            lastSavedSteps + currentSteps
        } else {
            currentSteps
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Step Tracker Active")
            .setContentText("Steps today: $totalSteps")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // TODO: Replace with custom icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
}
