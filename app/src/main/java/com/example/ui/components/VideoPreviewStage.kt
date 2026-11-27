package com.example.ui.components

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.AspectRatioOption
import com.example.data.model.AudioRole
import com.example.data.model.AudioTrack
import com.example.data.model.CaptionCue
import com.example.data.model.CaptionStyle
import com.example.data.model.ColorPreset
import com.example.data.model.TransitionEasing
import com.example.data.model.TransitionType
import com.example.data.model.VideoProject
import com.example.data.model.formatTimecode
import com.example.media.VideoMediaEngine
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioCrimson
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CaptionViolet
import com.example.ui.theme.CinemaCoral
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.PlayheadCyan
import com.example.ui.viewmodel.EditorUiState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun VideoPreviewStage(
    project: VideoProject,
    state: EditorUiState,
    batterySaverEnabled: Boolean,
    onTogglePlay: () -> Unit,
    onStepFrame: (Boolean) -> Unit,
    onCycleAspectRatio: () -> Unit,
    onCompareToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val clips = remember(project.clips) { project.typedClips() }
    val captions = remember(project.captions) { project.typedCaptions() }
    val audioTracks = remember(project.audioTracks) { project.typedAudioTracks() }
    val aspectOption = AspectRatioOption.fromRaw(project.aspectRatio)
    val activeClip = remember(clips, state.playheadMs) {
        clips.firstOrNull { state.playheadMs in it.startMs..it.endMs } ?: clips.firstOrNull()
    }
    val activeClipIndex = remember(clips, activeClip) {
        clips.indexOfFirst { it.clipId == activeClip?.clipId }.coerceAtLeast(0)
    }
    val nextClip = clips.getOrNull(activeClipIndex + 1)
    val activeTransition = activeClip?.let { TransitionType.fromRaw(it.transition) } ?: TransitionType.CUT
    val activeEasing = activeClip?.let { TransitionEasing.fromRaw(it.transitionEasing) } ?: TransitionEasing.EASE_IN_OUT
    val transitionDurationMs = (activeClip?.transitionDurationMs ?: 500L).coerceIn(150L, 1500L)
    val rawTransitionProgress = if (
        activeClip != null &&
        activeTransition != TransitionType.CUT &&
        state.playheadMs >= (activeClip.endMs - transitionDurationMs) &&
        state.playheadMs <= activeClip.endMs
    ) {
        ((state.playheadMs - (activeClip.endMs - transitionDurationMs)).toFloat() / transitionDurationMs.toFloat())
            .coerceIn(0f, 1f)
    } else 0f

    val transitionProgress = remember(rawTransitionProgress, activeEasing) {
        when (activeEasing) {
            TransitionEasing.LINEAR -> rawTransitionProgress
            TransitionEasing.EASE_IN_OUT ->
                ((1.0 - cos(rawTransitionProgress * PI)) * 0.5).toFloat().coerceIn(0f, 1f)
            TransitionEasing.SPRING ->
                (1f - (1f - rawTransitionProgress).pow(3) * cos(rawTransitionProgress * 4.5f)).coerceIn(0f, 1f)
        }
    }

    val activeCaption = remember(captions, state.playheadMs) {
        captions.firstOrNull { state.playheadMs in it.startMs..it.endMs }
    }

    val colorMatrix = remember(
        project.exposure,
        project.contrast,
        project.saturation,
        project.temperature,
        project.tint,
        state.compareOriginalColor
    ) {
        if (state.compareOriginalColor) {
            androidx.compose.ui.graphics.ColorMatrix()
        } else {
            VideoMediaEngine.buildComposeColorMatrix(
                exposure = project.exposure.toFloat(),
                contrast = project.contrast.toFloat(),
                saturation = project.saturation.toFloat(),
                temperature = project.temperature.toFloat(),
                tint = project.tint.toFloat()
            )
        }
    }

    val context = LocalContext.current
    val anySolo = remember(audioTracks) { audioTracks.any { it.solo } }
    val effectiveVolume = remember(audioTracks, anySolo) {
        val active = audioTracks.filter { !it.muted && (!anySolo || it.solo) }
        if (active.isEmpty()) 0f else active.map { it.volume.toFloat() }.average().toFloat().coerceIn(0f, 1.6f)
    }

    // Multi-track synchronized playback for Background Music, SFX, AI Music, and imported audio files
    val activePlayers = remember { mutableMapOf<String, MediaPlayer>() }
    val activePlayerUris = remember { mutableMapOf<String, String>() }

    DisposableEffect(Unit) {
        onDispose {
            activePlayers.values.forEach { mp ->
                runCatching {
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                }
            }
            activePlayers.clear()
            activePlayerUris.clear()
        }
    }

    LaunchedEffect(state.isPlaying, state.playheadMs, audioTracks, anySolo) {
        if (!state.isPlaying) {
            activePlayers.values.forEach { mp ->
                runCatching {
                    if (mp.isPlaying) mp.pause()
                }
            }
            return@LaunchedEffect
        }

        val currentPlayhead = state.playheadMs
        val trackById = audioTracks.associateBy { it.trackId }

        // Release players for removed tracks or changed URIs
        val staleIds = activePlayers.keys.filter { id ->
            val t = trackById[id]
            t == null || t.sourceUri.isBlank() || activePlayerUris[id] != t.sourceUri
        }
        staleIds.forEach { id ->
            runCatching {
                activePlayers.remove(id)?.apply {
                    if (isPlaying) stop()
                    release()
                }
            }
            activePlayerUris.remove(id)
        }

        audioTracks.forEach { track ->
            val uri = track.sourceUri
            if (uri.isBlank() || AudioRole.fromRaw(track.role) == AudioRole.ORIGINAL) return@forEach

            val isPlayableSource = uri.startsWith("ai_audio://") ||
                uri.startsWith("local_audio://") ||
                uri.startsWith("content://") ||
                uri.startsWith("file://")
            if (!isPlayableSource) return@forEach

            val trackStart = track.offsetMs
            val isMusic = AudioRole.fromRaw(track.role) == AudioRole.MUSIC
            val trackEnd = if (isMusic) {
                maxOf(trackStart + track.durationMs, project.totalDurationMs)
            } else {
                trackStart + track.durationMs
            }
            val inWindow = currentPlayhead in trackStart..trackEnd
            val audible = !track.muted && (!anySolo || track.solo) && inWindow

            val existing = activePlayers[track.trackId]
            if (!audible) {
                if (existing != null && runCatching { existing.isPlaying }.getOrDefault(false)) {
                    runCatching { existing.pause() }
                }
                return@forEach
            }

            val player = existing ?: runCatching {
                MediaPlayer().apply {
                    when {
                        uri.startsWith("ai_audio://") -> setDataSource(uri.removePrefix("ai_audio://"))
                        uri.startsWith("local_audio://") -> setDataSource(uri.removePrefix("local_audio://"))
                        else -> setDataSource(context, Uri.parse(uri))
                    }
                    isLooping = isMusic
                    prepare()
                }.also { created ->
                    activePlayers[track.trackId] = created
                    activePlayerUris[track.trackId] = uri
                }
            }.getOrNull() ?: return@forEach

            // Compute live gain with fade-in / fade-out envelope & stereo pan
            val localMs = (currentPlayhead - trackStart).coerceAtLeast(0L)
            val envIn = if (track.fadeInMs > 0L && localMs < track.fadeInMs) {
                (localMs.toFloat() / track.fadeInMs.toFloat()).coerceIn(0f, 1f)
            } else 1f
            val remainMs = (trackEnd - currentPlayhead).coerceAtLeast(0L)
            val envOut = if (track.fadeOutMs > 0L && remainMs < track.fadeOutMs) {
                (remainMs.toFloat() / track.fadeOutMs.toFloat()).coerceIn(0f, 1f)
            } else 1f
            val gain = (track.volume.toFloat() * envIn * envOut).coerceIn(0f, 1f)
            val pan = track.pan.toFloat().coerceIn(-1f, 1f)
            val leftVol = (gain * if (pan > 0f) 1f - pan else 1f).coerceIn(0f, 1f)
            val rightVol = (gain * if (pan < 0f) 1f + pan else 1f).coerceIn(0f, 1f)

            runCatching {
                player.setVolume(leftVol, rightVol)
                val mediaDur = player.duration.coerceAtLeast(1)
                val desiredPos = if (isMusic) (localMs % mediaDur).toInt() else localMs.toInt().coerceIn(0, mediaDur - 1)
                if (!player.isPlaying) {
                    player.seekTo(desiredPos)
                    player.start()
                } else if (abs(player.currentPosition - desiredPos) > 450) {
                    player.seekTo(desiredPos)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF05070B))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Video Monitor Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(216.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF090C12))
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            val previewModifier = Modifier
                .fillMaxHeight()
                .aspectRatio(aspectOption.ratio.coerceIn(0.56f, 1.85f), matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)

            Box(modifier = previewModifier) {
                val clipUri = activeClip?.sourceUri.orEmpty()
                val isAiGeneratedClip = clipUri.startsWith("ai_file://")
                val clipDur = ((activeClip?.endMs ?: 6000L) - (activeClip?.startMs ?: 0L)).coerceAtLeast(1L)
                val clipProgress = ((state.playheadMs - (activeClip?.startMs ?: 0L)).toFloat() / clipDur).coerceIn(0f, 1f)

                val extractedFrame = activeClip?.let { state.clipThumbnails[it.clipId] }?.let { list ->
                    if (list.isEmpty()) null
                    else {
                        val localFrac = clipProgress.coerceIn(0f, 0.99f)
                        list[(localFrac * list.size).toInt().coerceIn(0, list.lastIndex)]
                    }
                }

                val transScale = when (activeTransition) {
                    TransitionType.ZOOM_IN -> 1f + (transitionProgress * 0.36f)
                    TransitionType.WHIP_PAN -> 1f + (sin(transitionProgress * PI).toFloat() * 0.14f)
                    else -> 1f
                }
                val transShiftX = when (activeTransition) {
                    TransitionType.WHIP_PAN -> -transitionProgress * 220f
                    TransitionType.SLIDE_LEFT -> -transitionProgress * 260f
                    TransitionType.GLITCH -> if (transitionProgress > 0.05f) sin(transitionProgress * 55f) * 18f else 0f
                    else -> 0f
                }
                val transAlpha = when (activeTransition) {
                    TransitionType.CROSS_FADE, TransitionType.CROSS_DISSOLVE, TransitionType.BLUR_DISSOLVE ->
                        (1f - transitionProgress * 0.85f).coerceIn(0.15f, 1f)
                    else -> 1f
                }
                val blurRadiusDp = if (
                    activeTransition == TransitionType.BLUR_DISSOLVE &&
                    transitionProgress > 0.02f &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    (transitionProgress * 12f).dp
                } else 0.dp

                if (clipUri.isNotBlank() && !isAiGeneratedClip && state.isPlaying) {
                    // Live hardware VideoView playback for imported device footage with GPU RenderEffect LUT
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.parse(clipUri))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    val vol = effectiveVolume.coerceIn(0f, 1f)
                                    mp.setVolume(vol, vol)
                                    val seekPos = ((state.playheadMs - (activeClip?.startMs ?: 0L)) +
                                        (activeClip?.trimStartMs ?: 0L)).toInt().coerceAtLeast(0)
                                    mp.seekTo(seekPos)
                                    start()
                                }
                            }
                        },
                        update = { view ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val androidMatrix = android.graphics.ColorMatrix(colorMatrix.values)
                                view.setRenderEffect(
                                    if (state.compareOriginalColor) null
                                    else RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(androidMatrix))
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (extractedFrame != null) {
                    val aiMotionScale = when (activeClip?.sampleScene) {
                        "AI_ZOOM_IN" -> 1.0f + (clipProgress * 0.16f)
                        "AI_DRAMATIC_PUSH" -> 1.0f + (clipProgress * 0.22f)
                        "AI_PAN_LEFT", "AI_PAN_RIGHT" -> 1.14f
                        else -> 1.0f
                    }
                    val aiMotionShiftX = when (activeClip?.sampleScene) {
                        "AI_PAN_LEFT" -> (0.5f - clipProgress) * 56f
                        "AI_PAN_RIGHT" -> (clipProgress - 0.5f) * 56f
                        else -> 0f
                    }
                    val aiMotionRot = if (activeClip?.sampleScene == "AI_DRAMATIC_PUSH") {
                        (clipProgress - 0.5f) * 2.2f
                    } else 0f

                    Image(
                        bitmap = extractedFrame.asImageBitmap(),
                        contentDescription = activeClip?.label ?: "Video frame preview",
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(colorMatrix),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (blurRadiusDp > 0.dp) Modifier.blur(blurRadiusDp) else Modifier)
                            .graphicsLayer {
                                scaleX = aiMotionScale * transScale
                                scaleY = aiMotionScale * transScale
                                translationX = aiMotionShiftX + transShiftX
                                rotationZ = aiMotionRot
                                alpha = transAlpha
                            }
                    )
                } else {
                    // Studio Cinema Canvas Preview with subtle animated Ken Burns motion synced to playhead
                    val sceneRes = if (activeClip?.sampleScene == "NEON_CITY") {
                        R.drawable.img_studio_hero_1795777011469
                    } else {
                        R.drawable.img_sample_cinema_1795777012583
                    }
                    val kenBurnsScale = 1.0f + (clipProgress * 0.08f)

                    Image(
                        painter = painterResource(id = sceneRes),
                        contentDescription = activeClip?.label ?: "Cinema canvas preview",
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(colorMatrix),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (blurRadiusDp > 0.dp) Modifier.blur(blurRadiusDp) else Modifier)
                            .graphicsLayer {
                                scaleX = kenBurnsScale * transScale
                                scaleY = kenBurnsScale * transScale
                                translationX = transShiftX
                                alpha = transAlpha
                            }
                    )
                }

                // Incoming Next Clip Cross-Blend Layer for seamless transition previews
                if (transitionProgress > 0.01f && nextClip != null &&
                    activeTransition in setOf(
                        TransitionType.CROSS_FADE,
                        TransitionType.CROSS_DISSOLVE,
                        TransitionType.BLUR_DISSOLVE,
                        TransitionType.SLIDE_LEFT,
                        TransitionType.WHIP_PAN
                    )
                ) {
                    val nextBmp = state.clipThumbnails[nextClip.clipId]?.firstOrNull()
                    val incomingModifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            when (activeTransition) {
                                TransitionType.SLIDE_LEFT, TransitionType.WHIP_PAN -> {
                                    translationX = (1f - transitionProgress) * 260f
                                    alpha = 1f
                                }
                                else -> {
                                    alpha = transitionProgress
                                }
                            }
                        }
                    if (nextBmp != null) {
                        Image(
                            bitmap = nextBmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(colorMatrix),
                            modifier = incomingModifier
                        )
                    } else {
                        val nextRes = if (nextClip.sampleScene == "NEON_CITY") {
                            R.drawable.img_studio_hero_1795777011469
                        } else {
                            R.drawable.img_sample_cinema_1795777012583
                        }
                        Image(
                            painter = painterResource(id = nextRes),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(colorMatrix),
                            modifier = incomingModifier
                        )
                    }
                }

                // Live Transition Special-FX Overlays (Dip Black, Flash White, RGB Glitch, Iris Wipe)
                if (transitionProgress > 0.01f) {
                    when (activeTransition) {
                        TransitionType.DIP_BLACK -> {
                            val dipAlpha = sin(transitionProgress * PI).toFloat().coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = dipAlpha))
                            )
                        }

                        TransitionType.DIP_WHITE -> {
                            val flashAlpha = sin(transitionProgress * PI).toFloat().coerceIn(0f, 0.96f)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = flashAlpha))
                            )
                        }

                        TransitionType.GLITCH -> {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val intensity = sin(transitionProgress * PI).toFloat().coerceIn(0f, 1f)
                                val sliceCount = 7
                                val sliceH = size.height / sliceCount
                                for (i in 0 until sliceCount) {
                                    val shift = sin((i * 1.9 + transitionProgress * 22.0)).toFloat() * 28f * intensity
                                    val color = when (i % 3) {
                                        0 -> PlayheadCyan.copy(alpha = 0.28f * intensity)
                                        1 -> CinemaCoral.copy(alpha = 0.26f * intensity)
                                        else -> CaptionViolet.copy(alpha = 0.25f * intensity)
                                    }
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(shift, i * sliceH),
                                        size = Size(size.width, sliceH * 0.55f)
                                    )
                                }
                            }
                        }

                        TransitionType.IRIS_WIPE -> {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val irisClosure = sin(transitionProgress * PI).toFloat().coerceIn(0f, 0.98f)
                                val clearRadius = (size.maxDimension * 0.65f * (1f - irisClosure)).coerceAtLeast(4f)
                                drawRect(
                                    brush = Brush.radialGradient(
                                        0.0f to Color.Transparent,
                                        0.68f to Color.Transparent,
                                        1.0f to Color.Black,
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = clearRadius
                                    )
                                )
                            }
                        }

                        else -> Unit
                    }
                }

                // Real-time Vignette Overlay
                val vignetteAlpha = if (state.compareOriginalColor) 0f else project.vignette.toFloat().coerceIn(0f, 0.85f)
                if (vignetteAlpha > 0.02f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = vignetteAlpha)),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.maxDimension * 0.72f
                            )
                        )
                    }
                }

                // Live Synced Caption Overlay
                if (activeCaption != null) {
                    CaptionOverlayRenderer(
                        cue = activeCaption,
                        style = CaptionStyle.fromRaw(project.captionStyle),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }

            // Top-left LUT, Spec & Live Transition FX HUD
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = Color(0xBB07090E),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (state.compareOriginalColor) "ORIGINAL" else ColorPreset.fromRaw(project.colorPreset).label.uppercase(),
                        style = MonoBadgeStyle,
                        color = if (state.compareOriginalColor) Color.White else CinemaCoral,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    color = Color(0xBB07090E),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${project.resolution} • ${if (batterySaverEnabled) 30 else project.frameRate}FPS",
                        style = MonoBadgeStyle,
                        color = PlayheadCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                if (transitionProgress > 0.02f) {
                    Surface(
                        color = CaptionViolet.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "FX • ${activeTransition.shortCode}",
                            style = MonoBadgeStyle,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Top-right Before/After Hold Compare Button
            Surface(
                color = if (state.compareOriginalColor) CinemaCoral else Color(0xBB07090E),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onCompareToggle(true)
                                tryAwaitRelease()
                                onCompareToggle(false)
                            }
                        )
                    }
                    .testTag("compare_color_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Compare,
                        contentDescription = "Hold to compare original grade",
                        tint = if (state.compareOriginalColor) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "A/B",
                        style = MonoBadgeStyle,
                        color = if (state.compareOriginalColor) Color.Black else Color.White
                    )
                }
            }

            // Right-edge Live Stereo Audio VU Meter
            LiveStereoVuMeter(
                isPlaying = state.isPlaying,
                playheadMs = state.playheadMs,
                volumeLevel = effectiveVolume,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .width(12.dp)
                    .height(110.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Transport & Timecode Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Frame-accurate JetBrains Mono timecode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimecode(state.playheadMs, project.frameRate),
                    style = MonoTimecodeStyle,
                    color = PlayheadCyan
                )
                Text(
                    text = " / ${formatTimecode(project.totalDurationMs, project.frameRate)}",
                    style = MonoTimecodeStyle,
                    color = Color(0xFF64748B)
                )
            }

            // Transport buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onStepFrame(false) },
                    modifier = Modifier.size(44.dp).testTag("step_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous frame",
                        tint = Color(0xFFCBD5E1)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CinemaCoral)
                        .clickable(onClick = onTogglePlay)
                        .testTag("play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause preview" else "Play preview",
                        tint = Color(0xFF1A0703),
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(
                    onClick = { onStepFrame(true) },
                    modifier = Modifier.size(44.dp).testTag("step_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next frame",
                        tint = Color(0xFFCBD5E1)
                    )
                }
            }

            // Aspect Ratio Quick Switcher
            Surface(
                color = Color(0xFF151B27),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .border(1.dp, Color(0xFF2B3548), RoundedCornerShape(8.dp))
                    .clickable(onClick = onCycleAspectRatio)
                    .testTag("aspect_ratio_chip")
            ) {
                Text(
                    text = aspectOption.raw,
                    style = MonoBadgeStyle,
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CaptionOverlayRenderer(
    cue: CaptionCue,
    style: CaptionStyle,
    modifier: Modifier = Modifier
) {
    val highlightColor = when (style) {
        CaptionStyle.KINETIC_BOLD -> CinemaCoral
        CaptionStyle.MINIMAL_SUB -> PlayheadCyan
        CaptionStyle.BROADCAST_BAR -> AudioAmber
        CaptionStyle.NEON_POP -> CaptionViolet
    }

    val annotated = remember(cue.text, cue.emphasisWord, highlightColor) {
        buildAnnotatedString {
            val emp = cue.emphasisWord.trim()
            if (emp.isNotBlank() && cue.text.contains(emp, ignoreCase = true)) {
                val idx = cue.text.indexOf(emp, ignoreCase = true)
                append(cue.text.substring(0, idx))
                withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.ExtraBold)) {
                    append(cue.text.substring(idx, idx + emp.length))
                }
                append(cue.text.substring(idx + emp.length))
            } else {
                append(cue.text)
            }
        }
    }

    val bgColor = when (style) {
        CaptionStyle.KINETIC_BOLD -> Color(0xCC07090E)
        CaptionStyle.MINIMAL_SUB -> Color(0x88000000)
        CaptionStyle.BROADCAST_BAR -> Color(0xE60F172A)
        CaptionStyle.NEON_POP -> Color(0xCC1E1038)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(if (style == CaptionStyle.BROADCAST_BAR) 4.dp else 10.dp),
        modifier = modifier
            .then(
                if (style == CaptionStyle.NEON_POP) {
                    Modifier.border(1.dp, CaptionViolet, RoundedCornerShape(10.dp))
                } else Modifier
            )
    ) {
        Text(
            text = annotated,
            color = Color.White,
            fontSize = if (style == CaptionStyle.KINETIC_BOLD) 14.sp else 12.sp,
            fontWeight = if (style == CaptionStyle.MINIMAL_SUB) FontWeight.Medium else FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun LiveStereoVuMeter(
    isPlaying: Boolean,
    playheadMs: Long,
    volumeLevel: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barWidth = (size.width - 3f) / 2f
        val leftLevel = if (isPlaying) {
            (volumeLevel * (0.45f + 0.42f * abs(sin(playheadMs * 0.012)).toFloat())).coerceIn(0.04f, 1f)
        } else (volumeLevel * 0.14f).coerceIn(0.02f, 0.25f)
        val rightLevel = if (isPlaying) {
            (volumeLevel * (0.42f + 0.45f * abs(sin(playheadMs * 0.015 + 1.1)).toFloat())).coerceIn(0.04f, 1f)
        } else (volumeLevel * 0.12f).coerceIn(0.02f, 0.25f)

        val meterBrush = Brush.verticalGradient(
            listOf(AudioCrimson, AudioAmber, AudioEmerald)
        )

        // Track backgrounds
        drawRoundRect(
            color = Color(0x661E293B),
            topLeft = Offset(0f, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = Color(0x661E293B),
            topLeft = Offset(barWidth + 3f, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // Active levels
        val lH = size.height * leftLevel
        val rH = size.height * rightLevel
        drawRoundRect(
            brush = meterBrush,
            topLeft = Offset(0f, size.height - lH),
            size = Size(barWidth, lH),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            brush = meterBrush,
            topLeft = Offset(barWidth + 3f, size.height - rH),
            size = Size(barWidth, rH),
            cornerRadius = CornerRadius(3f, 3f)
        )
    }
}
