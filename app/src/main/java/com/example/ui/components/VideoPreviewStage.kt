package com.example.ui.components

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.data.model.CaptionCue
import com.example.data.model.CaptionStyle
import com.example.data.model.ColorPreset
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
import kotlin.math.abs
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

    val anySolo = remember(audioTracks) { audioTracks.any { it.solo } }
    val effectiveVolume = remember(audioTracks, anySolo) {
        val active = audioTracks.filter { !it.muted && (!anySolo || it.solo) }
        if (active.isEmpty()) 0f else active.map { it.volume.toFloat() }.average().toFloat().coerceIn(0f, 1.6f)
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
                val extractedFrame = activeClip?.let { state.clipThumbnails[it.clipId] }?.let { list ->
                    if (list.isEmpty()) null
                    else {
                        val localFrac = ((state.playheadMs - activeClip.startMs).toFloat() /
                            (activeClip.endMs - activeClip.startMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 0.99f)
                        list[(localFrac * list.size).toInt().coerceIn(0, list.lastIndex)]
                    }
                }

                if (clipUri.isNotBlank() && state.isPlaying) {
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
                    Image(
                        bitmap = extractedFrame.asImageBitmap(),
                        contentDescription = activeClip?.label ?: "Video frame preview",
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(colorMatrix),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Studio Cinema Canvas Preview with subtle animated Ken Burns motion synced to playhead
                    val sceneRes = if (activeClip?.sampleScene == "NEON_CITY") {
                        R.drawable.img_studio_hero_1795777011469
                    } else {
                        R.drawable.img_sample_cinema_1795777012583
                    }
                    val clipDur = ((activeClip?.endMs ?: 6000L) - (activeClip?.startMs ?: 0L)).coerceAtLeast(1L)
                    val progress = ((state.playheadMs - (activeClip?.startMs ?: 0L)).toFloat() / clipDur).coerceIn(0f, 1f)
                    val kenBurnsScale = 1.0f + (progress * 0.08f)

                    Image(
                        painter = painterResource(id = sceneRes),
                        contentDescription = activeClip?.label ?: "Cinema canvas preview",
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(colorMatrix),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = kenBurnsScale
                                scaleY = kenBurnsScale
                            }
                    )
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

            // Top-left LUT & Spec HUD
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
