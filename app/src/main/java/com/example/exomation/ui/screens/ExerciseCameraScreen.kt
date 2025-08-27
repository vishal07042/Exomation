package com.example.exomation.ui.screens

import android.Manifest
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.exomation.services.pose.PoseDetectionAnalyzer
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.concurrent.Executors
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.exomation.presentation.viewmodels.ExerciseViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.exomation.domain.model.ExerciseType as DomainExerciseType
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ExerciseCameraScreen(viewModel: ExerciseViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissions = rememberMultiplePermissionsState(listOf(Manifest.permission.CAMERA, Manifest.permission.INTERNET))

    DisposableEffect(Unit) {
        val allGranted = permissions.permissions.all { it.status.isGranted }
        if (!allGranted) {
            permissions.launchMultiplePermissionRequest()
        }
        onDispose { }
    }

    if (permissions.permissions.any { !it.status.isGranted }) return

    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { PoseDetectionAnalyzer(context, executor) }
    val state by analyzer.detectionState.collectAsState()
    DisposableEffect(state) {
        viewModel.onExerciseUpdate(
            type = state.exerciseType,
            repetitions = state.repetitions,
            confidence = state.confidence
        )
        onDispose { }
    }

    var useFrontCamera by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(true) }
    var selectedExercise by remember { mutableStateOf<DomainExerciseType?>(null) }
    val exerciseOptions = listOf(
        DomainExerciseType.SQUATS,
        DomainExerciseType.PUSHUPS,
        DomainExerciseType.KICKS,
        DomainExerciseType.BICEP_CURLS,
        DomainExerciseType.LUNGES,
        DomainExerciseType.PLANK
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().also { it.setAnalyzer(executor, analyzer) }

                    fun bind(selector: CameraSelector) {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            analysis
                        )
                    }

                    bind(if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA)

                    // Rebind on recomposition via update block below
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
            ,
            update = { previewView ->
                val cameraProvider = ProcessCameraProvider.getInstance(previewView.context).get()
                val preview = androidx.camera.core.Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { it.setAnalyzer(executor, analyzer) }
                val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            }
        )

        // Skeleton + landmarks overlay (mirror X for front camera)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lm = state.landmarks ?: return@Canvas
            fun p(i: Int): Offset {
                val rawX = lm[i].x * size.width
                val x = if (useFrontCamera) size.width - rawX else rawX
                val y = lm[i].y * size.height
                return Offset(x, y)
            }
            val stroke = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val c = Color(0xFF00E5FF)

            // Draw simple connections: shoulders-hips-knees-ankles and elbows-wrists
            fun line(a: Int, b: Int) = drawLine(c, p(a), p(b), strokeWidth = stroke.width)

            // Torso and limbs per MediaPipe indices
            line(11, 12)
            line(11, 23)
            line(12, 24)
            line(23, 24)
            line(11, 13); line(13, 15)
            line(12, 14); line(14, 16)
            line(23, 25); line(25, 27)
            line(24, 26); line(26, 28)

            // Landmarks points
            val dotColor = Color(0xFFFFC107)
            lm.forEach { l ->
                val rawX = l.x * size.width
                val cx = if (useFrontCamera) size.width - rawX else rawX
                val cy = l.y * size.height
                drawCircle(color = dotColor, radius = 6f, center = Offset(cx, cy))
            }
        }

        // HUD card
        Card(modifier = Modifier
            .padding(16.dp)
            .align(Alignment.TopStart)) {
            Text(
                text = "${selectedExercise?.name ?: state.exerciseType} | Reps: ${state.repetitions} | Conf: ${"%.2f".format(state.confidence)}",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (showExercisePicker) {
            Card(modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
                    Text("Choose exercise")
                    LazyRow(modifier = Modifier.fillMaxWidth()) {
                        items(exerciseOptions) { opt ->
                            FilledTonalButton(onClick = {
                                selectedExercise = opt
                                viewModel.setSelectedExercise(opt)
                                showExercisePicker = false
                            }, modifier = Modifier.padding(end = 8.dp)) { Text(opt.name) }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp)) {
            FilledTonalButton(onClick = { useFrontCamera = !useFrontCamera }) {
                Text(if (useFrontCamera) "Rear Camera" else "Front Camera")
            }
        }
    }
}


