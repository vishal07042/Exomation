package com.example.exomation.services.pose

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor
import kotlin.math.atan2
import kotlin.math.abs

/**
 * Analyzes camera frames for pose detection and exercise recognition
 */
class PoseDetectionAnalyzer(
    private val context: Context,
    private val executor: Executor,
    private val modelPath: String = "pose_landmarker_lite.task" // MediaPipe model file
) : ImageAnalysis.Analyzer {
    
    companion object {
        private const val TAG = "PoseDetectionAnalyzer"
        
        // Landmark indices (MediaPipe Pose)
        private const val LEFT_SHOULDER = 11
        private const val RIGHT_SHOULDER = 12
        private const val LEFT_ELBOW = 13
        private const val RIGHT_ELBOW = 14
        private const val LEFT_WRIST = 15
        private const val RIGHT_WRIST = 16
        private const val LEFT_HIP = 23
        private const val RIGHT_HIP = 24
        private const val LEFT_KNEE = 25
        private const val RIGHT_KNEE = 26
        private const val LEFT_ANKLE = 27
        private const val RIGHT_ANKLE = 28
    }
    
    private var poseLandmarker: PoseLandmarker? = null
    private var exerciseClassifier: ExerciseClassifier = ExerciseClassifier()
    
    private val _detectionState = MutableStateFlow(PoseDetectionState())
    val detectionState: StateFlow<PoseDetectionState> = _detectionState
    
    data class PoseDetectionState(
        val exerciseType: ExerciseType = ExerciseType.NONE,
        val repetitions: Int = 0,
        val confidence: Float = 0f,
        val isInPosition: Boolean = false,
        val landmarks: List<Landmark>? = null,
        val feedbackMessage: String = ""
    )
    
    data class Landmark(
        val x: Float,
        val y: Float,
        val z: Float,
        val visibility: Float
    )
    
    enum class ExerciseType {
        NONE,
        SQUATS,
        PUSHUPS,
        KICKS,
        BICEP_CURLS,
        LUNGES,
        PLANK,
        JUMPING_JACKS
    }
    
    data class ExerciseResult(
        val type: ExerciseType,
        val repetitions: Int,
        val confidence: Float,
        val isInPosition: Boolean,
        val feedback: String
    )
    
    enum class SquatState { STANDING, SQUATTING }
    enum class PushupState { UP, DOWN }
    enum class KickState { NEUTRAL, KICKING }
    
    init {
        setupPoseLandmarker()
    }
    
    private fun setupPoseLandmarker() {
        try {
            // Verify model asset exists; if not, log a clear error
            val assetManager = context.assets
            val available = try {
                assetManager.open(modelPath).close(); true
            } catch (e: Exception) {
                false
            }
            if (!available) {
                Log.e(TAG, "Model asset not found: $modelPath. Place the .task file in app/src/main/assets.")
                return
            }
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(modelPath)
                .setDelegate(Delegate.CPU) // Use GPU if available for better performance
                
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setOutputSegmentationMasks(false)
                .setMinPoseDetectionConfidence(0.4f)
                .setMinTrackingConfidence(0.4f)
                .setMinPosePresenceConfidence(0.4f)
                .setResultListener { result, input ->
                    processResults(result)
                }
                .setErrorListener { error ->
                    Log.e(TAG, "Pose detection error: $error")
                }
                .build()
                
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize pose landmarker", e)
        }
    }
    
    override fun analyze(image: ImageProxy) {
        // Convert ImageProxy to a correctly oriented Bitmap for MediaPipe
        val bitmap = image.toBitmap(image.imageInfo.rotationDegrees)
        val mpImage = BitmapImageBuilder(bitmap).build()
        
        // Detect poses
        // MediaPipe expects timestamp in milliseconds for LIVE_STREAM mode
        val tsMs = image.imageInfo.timestamp / 1_000_000L
        poseLandmarker?.detectAsync(mpImage, tsMs)
        
        image.close()
    }
    
    private fun processResults(result: PoseLandmarkerResult) {
        if (result.landmarks().isEmpty()) {
            _detectionState.value = _detectionState.value.copy(
                exerciseType = ExerciseType.NONE,
                confidence = 0f,
                landmarks = null
            )
            return
        }
        
        val poseLandmarks: List<NormalizedLandmark> = result.landmarks()[0] // Get first person's landmarks
        val landmarks: List<PoseDetectionAnalyzer.Landmark> = poseLandmarks.map { normalizedLandmark ->
            PoseDetectionAnalyzer.Landmark(
                x = normalizedLandmark.x(),
                y = normalizedLandmark.y(),
                z = normalizedLandmark.z(),
                visibility = 0.5f
            )
        }
        
        // Calculate joint angles and classify exercise
        val exerciseResult = exerciseClassifier.classifyExercise(landmarks)
        
        _detectionState.value = PoseDetectionState(
            exerciseType = exerciseResult.type,
            repetitions = exerciseResult.repetitions,
            confidence = exerciseResult.confidence,
            isInPosition = exerciseResult.isInPosition,
            landmarks = landmarks,
            feedbackMessage = exerciseResult.feedback
        )
    }
    
    fun resetCounter() {
        exerciseClassifier.resetRepetitions()
        _detectionState.value = _detectionState.value.copy(repetitions = 0)
    }
    
    fun release() {
        poseLandmarker?.close()
    }
    
    /**
     * Inner class for exercise classification based on pose landmarks
     */
    inner class ExerciseClassifier {
        private var squatState = SquatState.STANDING
        private var pushupState = PushupState.UP
        private var kickState = KickState.NEUTRAL
        private var repetitions = 0
        private var lastExerciseType = ExerciseType.NONE
        // Simple exponential smoothing for angles to reduce jitter
        private var smoothedLeftKnee: Float? = null
        private var smoothedRightKnee: Float? = null
        private var smoothedLeftElbow: Float? = null
        private var smoothedRightElbow: Float? = null
        private val smoothingAlpha = 0.3f
        // Kick responsiveness helpers
        private var prevLeftAnkleY: Float? = null
        private var prevRightAnkleY: Float? = null
        private var lastUpdateMs: Long? = null
        private var lastKickTimeMs: Long = 0
        private val minKickIntervalMs: Long = 350 // debounce
        private val kickVelocityThresholdPerSec: Float = 1.2f
        
        fun classifyExercise(landmarks: List<Landmark>): ExerciseResult {
            // Calculate angles for classification
            var leftKneeAngle = calculateAngle(
                landmarks[LEFT_HIP],
                landmarks[LEFT_KNEE],
                landmarks[LEFT_ANKLE]
            )
            var rightKneeAngle = calculateAngle(
                landmarks[RIGHT_HIP],
                landmarks[RIGHT_KNEE],
                landmarks[RIGHT_ANKLE]
            )
            var leftElbowAngle = calculateAngle(
                landmarks[LEFT_SHOULDER],
                landmarks[LEFT_ELBOW],
                landmarks[LEFT_WRIST]
            )
            var rightElbowAngle = calculateAngle(
                landmarks[RIGHT_SHOULDER],
                landmarks[RIGHT_ELBOW],
                landmarks[RIGHT_WRIST]
            )

            // Smooth
            smoothedLeftKnee = smoothedLeftKnee?.let { it + smoothingAlpha * (leftKneeAngle - it) } ?: leftKneeAngle
            smoothedRightKnee = smoothedRightKnee?.let { it + smoothingAlpha * (rightKneeAngle - it) } ?: rightKneeAngle
            smoothedLeftElbow = smoothedLeftElbow?.let { it + smoothingAlpha * (leftElbowAngle - it) } ?: leftElbowAngle
            smoothedRightElbow = smoothedRightElbow?.let { it + smoothingAlpha * (rightElbowAngle - it) } ?: rightElbowAngle
            leftKneeAngle = smoothedLeftKnee!!
            rightKneeAngle = smoothedRightKnee!!
            leftElbowAngle = smoothedLeftElbow!!
            rightElbowAngle = smoothedRightElbow!!
            
            // Check for squats
            val squatResult = detectSquats(leftKneeAngle, rightKneeAngle, landmarks)
            if (squatResult.confidence > 0.7f) {
                if (lastExerciseType != ExerciseType.SQUATS) {
                    resetRepetitions()
                    lastExerciseType = ExerciseType.SQUATS
                }
                return squatResult
            }
            
            // Check for pushups
            val pushupResult = detectPushups(leftElbowAngle, rightElbowAngle, landmarks)
            if (pushupResult.confidence > 0.7f) {
                if (lastExerciseType != ExerciseType.PUSHUPS) {
                    resetRepetitions()
                    lastExerciseType = ExerciseType.PUSHUPS
                }
                return pushupResult
            }
            
            // Check for kicks
            val kickResult = detectKicks(landmarks)
            if (kickResult.confidence > 0.7f) {
                if (lastExerciseType != ExerciseType.KICKS) {
                    resetRepetitions()
                    lastExerciseType = ExerciseType.KICKS
                }
                return kickResult
            }

            // Check for bicep curls (use elbow angle change with wrist proximity)
            val curlResult = detectBicepCurls(leftElbowAngle, rightElbowAngle, landmarks)
            if (curlResult.confidence > 0.7f) {
                if (lastExerciseType != ExerciseType.BICEP_CURLS) {
                    resetRepetitions()
                    lastExerciseType = ExerciseType.BICEP_CURLS
                }
                return curlResult
            }

            // Check for lunges (front knee angle + hip drop)
            val lungeResult = detectLunges(landmarks)
            if (lungeResult.confidence > 0.7f) {
                if (lastExerciseType != ExerciseType.LUNGES) {
                    resetRepetitions()
                    lastExerciseType = ExerciseType.LUNGES
                }
                return lungeResult
            }

            // Check for plank (time under plank posture)
            val plankResult = detectPlank(landmarks)
            if (plankResult.confidence > 0.7f) {
                if (lastExerciseType != ExerciseType.PLANK) {
                    resetRepetitions()
                    lastExerciseType = ExerciseType.PLANK
                }
                return plankResult
            }
            
            return ExerciseResult(
                type = lastExerciseType,
                repetitions = repetitions,
                confidence = 0.2f,
                isInPosition = false,
                feedback = "Position not recognized"
            )
        }
        
        private fun detectSquats(leftKneeAngle: Float, rightKneeAngle: Float, landmarks: List<Landmark>): ExerciseResult {
            val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2f
            val downThreshold = 70f
            val upThreshold = 160f
            val inDown = avgKneeAngle <= downThreshold
            val inUp = avgKneeAngle >= upThreshold

            val feedback = when {
                inDown -> { if (squatState == SquatState.STANDING) squatState = SquatState.SQUATTING; "Down" }
                inUp -> {
                    if (squatState == SquatState.SQUATTING) {
                        squatState = SquatState.STANDING
                        repetitions++
                    }
                    "Up"
                }
                else -> "Keep form"
            }

            val confidence = when {
                inDown -> 0.9f
                inUp -> 0.8f
                else -> 0.4f
            }
            return ExerciseResult(
                type = ExerciseType.SQUATS,
                repetitions = repetitions,
                confidence = confidence,
                isInPosition = inDown || inUp,
                feedback = feedback
            )
        }
        
        private fun detectPushups(leftElbowAngle: Float, rightElbowAngle: Float, landmarks: List<Landmark>): ExerciseResult {
            // Use left side angles similar to the provided Python logic
            val elbow = leftElbowAngle
            val shoulder = calculateAngle(landmarks[LEFT_ELBOW], landmarks[LEFT_SHOULDER], landmarks[LEFT_HIP])
            val hip = calculateAngle(landmarks[LEFT_SHOULDER], landmarks[LEFT_HIP], landmarks[LEFT_KNEE])

            // Form check: elbow > 160, shoulder > 40, hip > 160
            val goodForm = elbow > 160f && shoulder > 40f && hip > 160f

            // Range thresholds from the Python mapping
            val downPos = elbow <= 90f && hip > 160f
            val upPos = elbow > 160f && shoulder > 40f && hip > 160f

            var feedback = "Fix Form"
            if (goodForm && pushupState == PushupState.UP && downPos) {
                // Transition to down
                pushupState = PushupState.DOWN
                feedback = "Down"
            } else if (goodForm && pushupState == PushupState.DOWN && upPos) {
                // Transition to up → count one rep
                pushupState = PushupState.UP
                repetitions++
                feedback = "Up"
            } else if (goodForm) {
                feedback = if (pushupState == PushupState.UP) "Ready" else "Hold"
            }

            val isPlank = hip > 160f
            val confidence = when {
                downPos || upPos -> 0.9f
                goodForm -> 0.6f
                else -> 0.3f
            }

            return ExerciseResult(
                type = ExerciseType.PUSHUPS,
                repetitions = repetitions,
                confidence = confidence,
                isInPosition = isPlank,
                feedback = feedback
            )
        }
        
        private fun detectKicks(landmarks: List<Landmark>): ExerciseResult {
            val nowMs = System.currentTimeMillis()
            val dtSec = lastUpdateMs?.let { (nowMs - it) / 1000f }.takeIf { it != null && it!! > 0f } ?: 0.016f
            lastUpdateMs = nowMs

            val leftAnkleY = landmarks[LEFT_ANKLE].y
            val rightAnkleY = landmarks[RIGHT_ANKLE].y
            val hipY = (landmarks[LEFT_HIP].y + landmarks[RIGHT_HIP].y) / 2f

            val raisedThreshold = 0.16f // slightly easier
            val leftRaised = leftAnkleY < hipY - raisedThreshold
            val rightRaised = rightAnkleY < hipY - raisedThreshold

            // Upward velocity (lower y means higher in image coords)
            val vLeft = prevLeftAnkleY?.let { (it - leftAnkleY) / dtSec } ?: 0f
            val vRight = prevRightAnkleY?.let { (it - rightAnkleY) / dtSec } ?: 0f
            prevLeftAnkleY = leftAnkleY
            prevRightAnkleY = rightAnkleY

            val fastUp = (vLeft > kickVelocityThresholdPerSec || vRight > kickVelocityThresholdPerSec)
            val aboveHip = leftRaised || rightRaised
            val canCount = nowMs - lastKickTimeMs >= minKickIntervalMs

            val feedback: String
            if (fastUp && aboveHip && canCount) {
                repetitions++
                lastKickTimeMs = nowMs
                kickState = KickState.KICKING
                feedback = "Kick"
            } else if (!aboveHip) {
                kickState = KickState.NEUTRAL
                feedback = "Ready"
            } else {
                feedback = "Hold"
            }

            val confidence = when {
                fastUp && aboveHip -> 0.95f
                aboveHip -> 0.7f
                else -> 0.3f
            }
            return ExerciseResult(
                type = ExerciseType.KICKS,
                repetitions = repetitions,
                confidence = confidence,
                isInPosition = aboveHip,
                feedback = feedback
            )
        }
        
        private fun calculateAngle(p1: Landmark, p2: Landmark, p3: Landmark): Float {
            val angle = Math.toDegrees(
                atan2((p3.y - p2.y).toDouble(), (p3.x - p2.x).toDouble()) -
                atan2((p1.y - p2.y).toDouble(), (p1.x - p2.x).toDouble())
            ).toFloat()
            return abs(angle)
        }
        
        private fun calculateDistance(p1: Landmark, p2: Landmark): Float {
            val dx = p1.x - p2.x
            val dy = p1.y - p2.y
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }

        // Bicep curls: count when elbow closes (<50°) then opens (>160°)
        private fun detectBicepCurls(leftElbowAngle: Float, rightElbowAngle: Float, landmarks: List<Landmark>): ExerciseResult {
            val elbow = minOf(leftElbowAngle, rightElbowAngle)
            val down = elbow <= 50f
            val up = elbow >= 160f
            val confidence = when {
                down -> 0.9f
                up -> 0.8f
                else -> 0.4f
            }
            val feedback = when {
                down -> { if (pushupState == PushupState.UP) pushupState = PushupState.DOWN; "Curl" }
                up -> { if (pushupState == PushupState.DOWN) { pushupState = PushupState.UP; repetitions++ }; "Extend" }
                else -> "Hold"
            }
            return ExerciseResult(
                type = ExerciseType.BICEP_CURLS,
                repetitions = repetitions,
                confidence = confidence,
                isInPosition = down || up,
                feedback = feedback
            )
        }

        // Lunges: front knee angle small at bottom then large at top
        private fun detectLunges(landmarks: List<Landmark>): ExerciseResult {
            val leftKnee = calculateAngle(landmarks[LEFT_HIP], landmarks[LEFT_KNEE], landmarks[LEFT_ANKLE])
            val rightKnee = calculateAngle(landmarks[RIGHT_HIP], landmarks[RIGHT_KNEE], landmarks[RIGHT_ANKLE])
            val knee = minOf(leftKnee, rightKnee)
            val down = knee <= 80f
            val up = knee >= 160f
            val confidence = when {
                down -> 0.85f
                up -> 0.7f
                else -> 0.4f
            }
            val feedback = when {
                down -> { if (squatState == SquatState.STANDING) squatState = SquatState.SQUATTING; "Down" }
                up -> { if (squatState == SquatState.SQUATTING) { squatState = SquatState.STANDING; repetitions++ }; "Up" }
                else -> "Hold"
            }
            return ExerciseResult(
                type = ExerciseType.LUNGES,
                repetitions = repetitions,
                confidence = confidence,
                isInPosition = down || up,
                feedback = feedback
            )
        }

        // Plank: maintain straight line shoulders-hips (small vertical delta) and elbows extended
        private var plankStartMs: Long = 0
        private var plankSeconds: Int = 0
        private fun detectPlank(landmarks: List<Landmark>): ExerciseResult {
            val shoulderY = (landmarks[LEFT_SHOULDER].y + landmarks[RIGHT_SHOULDER].y) / 2f
            val hipY = (landmarks[LEFT_HIP].y + landmarks[RIGHT_HIP].y) / 2f
            val aligned = abs(shoulderY - hipY) < 0.10f
            if (aligned) {
                if (plankStartMs == 0L) plankStartMs = System.currentTimeMillis()
                plankSeconds = ((System.currentTimeMillis() - plankStartMs) / 1000L).toInt()
            } else {
                plankStartMs = 0L
            }
            return ExerciseResult(
                type = ExerciseType.PLANK,
                repetitions = plankSeconds,
                confidence = if (aligned) 0.8f else 0.3f,
                isInPosition = aligned,
                feedback = if (aligned) "Hold" else "Align hips"
            )
        }
        
        fun resetRepetitions() {
            repetitions = 0
            squatState = SquatState.STANDING
            pushupState = PushupState.UP
            kickState = KickState.NEUTRAL
        }
    }
    
    // Extension function to convert ImageProxy to Bitmap
    private fun ImageProxy.toBitmap(rotationDegrees: Int): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        // V and U are swapped in ImageProxy for NV21
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            width,
            height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 80, out)
        val imageBytes = out.toByteArray()
        var bmp = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }
        return bmp
    }
}
