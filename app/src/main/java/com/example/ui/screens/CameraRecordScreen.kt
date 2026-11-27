package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioCrimson
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CinemaCoral
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.OledBackground
import com.example.ui.theme.PlayheadCyan
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "CameraRecordScreen"

private enum class CaptureQuality(val label: String, val quality: Quality) {
    UHD_4K("4K", Quality.UHD),
    FHD_1080("1080p", Quality.FHD),
    HD_720("720p", Quality.HD)
}

@Composable
fun CameraRecordScreen(
    batterySaverEnabled: Boolean,
    appendToActiveProject: Boolean,
    onVideoRecorded: (Uri) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    var useFrontCamera by remember { mutableStateOf(false) }
    var selectedQuality by remember {
        mutableStateOf(if (batterySaverEnabled) CaptureQuality.FHD_1080 else CaptureQuality.UHD_4K)
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var micEnabled by remember { mutableStateOf(true) }
    var showGrid by remember { mutableStateOf(true) }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }

    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var recordedDurationMs by remember { mutableLongStateOf(0L) }
    var recordedBytes by remember { mutableLongStateOf(0L) }
    var audioAmplitude by remember { mutableFloatStateOf(0f) }
    var statusError by remember { mutableStateOf<String?>(null) }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var videoCaptureUseCase by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    BackHandler {
        activeRecording?.stop()
        activeRecording = null
        onNavigateBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            runCatching { cameraProviderRef?.unbindAll() }
        }
    }

    // Bind CameraX Preview + VideoCapture whenever camera lens or quality changes
    LaunchedEffect(hasCameraPermission, useFrontCamera, selectedQuality) {
        if (!hasCameraPermission) return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProviderRef = provider

                val desiredSelector = if (useFrontCamera && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val qualitySelector = QualitySelector.from(
                    selectedQuality.quality,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    desiredSelector,
                    preview,
                    videoCapture
                )

                boundCamera = camera
                videoCaptureUseCase = videoCapture
                torchEnabled = false
                zoomRatio = 1.0f
                statusError = null
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bind failed", e)
                statusError = e.localizedMessage ?: "Unable to initialize camera hardware."
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val videoCapture = videoCaptureUseCase ?: run {
            statusError = "Camera is still initializing. Please try again."
            return
        }
        statusError = null
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "FrameCut_$timestamp"

        var fallbackFile: File? = null
        val pendingRecording = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FrameCut")
            }
            val outputOptions = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()
            videoCapture.output.prepareRecording(context, outputOptions)
        } else {
            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            val outFile = File(dir, "$fileName.mp4")
            fallbackFile = outFile
            val outputOptions = FileOutputOptions.Builder(outFile).build()
            videoCapture.output.prepareRecording(context, outputOptions)
        }

        val configuredPending = if (micEnabled && hasAudioPermission) {
            pendingRecording.withAudioEnabled()
        } else {
            pendingRecording
        }

        activeRecording = configuredPending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecording = true
                    isPaused = false
                    recordedDurationMs = 0L
                    recordedBytes = 0L
                }

                is VideoRecordEvent.Pause -> {
                    isPaused = true
                }

                is VideoRecordEvent.Resume -> {
                    isPaused = false
                }

                is VideoRecordEvent.Status -> {
                    recordedDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                    recordedBytes = event.recordingStats.numBytesRecorded
                    val audioStats = event.recordingStats.audioStats
                    audioAmplitude = if (audioStats.hasAudio()) {
                        audioStats.audioAmplitude.toFloat().coerceIn(0f, 1f)
                    } else 0f
                }

                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    isPaused = false
                    audioAmplitude = 0f
                    activeRecording = null
                    if (event.hasError()) {
                        Log.e(TAG, "Video recording error code=${event.error}", event.cause)
                        statusError = "Recording stopped with error (code ${event.error})."
                    } else {
                        val outputUri = event.outputResults.outputUri
                        val finalUri = if (outputUri != Uri.EMPTY) {
                            outputUri
                        } else {
                            fallbackFile?.let { file ->
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                            }
                        }
                        if (finalUri != null) {
                            onVideoRecorded(finalUri)
                        } else {
                            statusError = "Video recorded but output URI was unavailable."
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBackground)
    ) {
        if (!hasCameraPermission) {
            CameraPermissionPrompt(
                onRequestPermissions = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                },
                onBack = onNavigateBack
            )
            return@Box
        }

        // Full-bleed CameraX PreviewView with Pinch-to-Zoom & Tap-to-Focus
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boundCamera) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val cam = boundCamera ?: return@detectTransformGestures
                        val zoomState = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                        val next = (zoomState.zoomRatio * zoom)
                            .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                        cam.cameraControl.setZoomRatio(next)
                        zoomRatio = next
                    }
                }
                .pointerInput(boundCamera) {
                    detectTapGestures { offset ->
                        val cam = boundCamera ?: return@detectTapGestures
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        cam.cameraControl.startFocusAndMetering(action)
                    }
                }
                .testTag("camerax_preview_view")
        )

        // Optional Rule-of-Thirds Cinema Grid
        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 1.2f
                val gridColor = Color.White.copy(alpha = 0.22f)
                drawLine(gridColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), stroke)
                drawLine(gridColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), stroke)
                drawLine(gridColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), stroke)
                drawLine(gridColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), stroke)
            }
        }

        // Top Cinema HUD Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(listOf(Color(0xCC07090E), Color.Transparent))
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        activeRecording?.stop()
                        activeRecording = null
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x8807090E))
                        .testTag("camera_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close camera",
                        tint = Color.White
                    )
                }

                // Live Recording Timecode or Mode Indicator
                Surface(
                    color = if (isRecording) AudioCrimson.copy(alpha = 0.88f) else Color(0xAA07090E),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(
                        1.dp,
                        if (isRecording) AudioCrimson else Color(0xFF334155),
                        RoundedCornerShape(50)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isPaused -> AudioAmber
                                        isRecording -> Color.White
                                        else -> PlayheadCyan
                                    }
                                )
                        )
                        val secs = recordedDurationMs / 1000L
                        val mb = recordedBytes / (1024.0 * 1024.0)
                        Text(
                            text = if (isRecording) {
                                val stateLabel = if (isPaused) "PAUSED" else "REC"
                                "$stateLabel %02d:%02d • %.1f MB".format(secs / 60L, secs % 60L, mb)
                            } else if (appendToActiveProject) {
                                "CAPTURE CLIP • ${selectedQuality.label}"
                            } else {
                                "CAMERAX STUDIO • ${selectedQuality.label}"
                            },
                            style = MonoTimecodeStyle,
                            color = Color.White
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { showGrid = !showGrid },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x8807090E))
                    ) {
                        Icon(
                            imageVector = if (showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                            contentDescription = "Toggle grid overlay",
                            tint = if (showGrid) PlayheadCyan else Color.White
                        )
                    }
                    val hasFlash = boundCamera?.cameraInfo?.hasFlashUnit() == true
                    if (hasFlash && !useFrontCamera) {
                        IconButton(
                            onClick = {
                                val next = !torchEnabled
                                boundCamera?.cameraControl?.enableTorch(next)
                                torchEnabled = next
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x8807090E))
                                .testTag("camera_torch_button")
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = "Toggle torch",
                                tint = if (torchEnabled) AudioAmber else Color.White
                            )
                        }
                    }
                }
            }
        }

        // Right-edge Live Microphone Level Meter during recording
        if (isRecording && micEnabled && hasAudioPermission) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .width(8.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x8807090E))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(audioAmplitude.coerceIn(0.08f, 1f))
                        .background(
                            Brush.verticalGradient(listOf(AudioCrimson, AudioAmber, AudioEmerald))
                        )
                )
            }
        }

        // Bottom Studio Capture Deck
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xE607090E), Color(0xFA07090E)))
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (statusError != null) {
                Surface(
                    color = AudioCrimson.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFDA4AF),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Quality & Zoom selector pills (locked while actively recording)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CaptureQuality.entries.forEach { q ->
                    val selected = selectedQuality == q
                    Surface(
                        color = if (selected) PlayheadCyan.copy(alpha = 0.22f) else Color(0x99151B27),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (selected) PlayheadCyan else Color(0xFF334155),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !isRecording) { selectedQuality = q }
                            .testTag("camera_quality_${q.label.lowercase()}")
                    ) {
                        Text(
                            text = q.label,
                            style = MonoBadgeStyle,
                            color = if (selected) PlayheadCyan else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = Color(0x99151B27),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .clickable {
                            val cam = boundCamera ?: return@clickable
                            val zs = cam.cameraInfo.zoomState.value ?: return@clickable
                            val nextZoom = if (zoomRatio < 1.5f && zs.maxZoomRatio >= 2f) 2.0f else 1.0f
                            cam.cameraControl.setZoomRatio(nextZoom.coerceIn(zs.minZoomRatio, zs.maxZoomRatio))
                            zoomRatio = nextZoom
                        }
                ) {
                    Text(
                        text = "%.1fx".format(zoomRatio),
                        style = MonoBadgeStyle,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Primary Capture Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Left Control: Mic Toggle (before recording) or Pause/Resume (during recording)
                if (isRecording) {
                    IconButton(
                        onClick = {
                            if (isPaused) activeRecording?.resume() else activeRecording?.pause()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2636))
                            .border(1.dp, Color(0xFF475569), CircleShape)
                            .testTag("camera_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (isPaused) "Resume recording" else "Pause recording",
                            tint = Color.White
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (!hasAudioPermission) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            } else {
                                micEnabled = !micEnabled
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2636))
                            .border(1.dp, Color(0xFF475569), CircleShape)
                            .testTag("camera_mic_toggle")
                    ) {
                        Icon(
                            imageVector = if (micEnabled && hasAudioPermission) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = "Toggle microphone",
                            tint = if (micEnabled && hasAudioPermission) AudioEmerald else Color(0xFF94A3B8)
                        )
                    }
                }

                // Center Shutter Button (Start / Stop Recording -> Instant Edit)
                val innerColor by animateColorAsState(
                    targetValue = if (isRecording) AudioCrimson else CinemaCoral,
                    label = "shutterColor"
                )
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .clickable {
                            if (isRecording) {
                                activeRecording?.stop()
                            } else {
                                startRecording()
                            }
                        }
                        .padding(8.dp)
                        .testTag("camera_record_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isRecording) 30.dp else 56.dp)
                            .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                            .background(innerColor)
                    )
                }

                // Right Control: Flip Lens (disabled during active recording)
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    enabled = !isRecording,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2636))
                        .border(1.dp, Color(0xFF475569), CircleShape)
                        .testTag("camera_flip_lens_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cameraswitch,
                        contentDescription = "Switch camera lens",
                        tint = if (isRecording) Color(0xFF475569) else Color.White
                    )
                }
            }

            Text(
                text = if (isRecording) "Tap stop to send recording straight to the timeline"
                else "Tap shutter to record • Pinch to zoom • Tap frame to focus",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    onRequestPermissions: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(CinemaCoral.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Videocam,
                contentDescription = null,
                tint = CinemaCoral,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Camera & Microphone Access",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Grant camera and microphone permissions to record 4K or 1080p video directly inside FrameCut and open it immediately on your timeline.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaCoral, contentColor = Color(0xFF1A0703)),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("grant_camera_permission_button")
        ) {
            Text("Enable CameraX Studio", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Go Back")
        }
    }
}
