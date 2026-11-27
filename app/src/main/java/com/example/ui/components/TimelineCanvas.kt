package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioRole
import com.example.data.model.AudioTrack
import com.example.data.model.CaptionCue
import com.example.data.model.TimelineClip
import com.example.data.model.VideoProject
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CaptionViolet
import com.example.ui.theme.CinemaCoral
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.PlayheadCyan
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TimelineCanvas(
    project: VideoProject,
    playheadMs: Long,
    zoomDpPerSec: Float,
    selectedClipId: String?,
    clipThumbnails: Map<String, List<Bitmap>>,
    onSeek: (Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    onSelectClip: (String) -> Unit,
    onTrimSelectedClip: (Long, Long) -> Unit,
    onSelectTrack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clips = remember(project.clips) { project.typedClips() }
    val captions = remember(project.captions) { project.typedCaptions() }
    val audioTracks = remember(project.audioTracks) { project.typedAudioTracks() }
    val totalMs = project.totalDurationMs.coerceAtLeast(4000L)

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    val dpPerMs = zoomDpPerSec / 1000f
    val pxPerMs = with(density) { zoomDpPerSec.dp.toPx() / 1000f }
    val timelineWidthDp = ((totalMs * dpPerMs) + 120f).coerceAtLeast(360f).dp
    val playheadOffsetPx = (playheadMs * pxPerMs).roundToInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        // Timeline Mini Header (Pinch hint + Zoom buttons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MULTI-TRACK TIMELINE • PINCH TO ZOOM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onZoomChange(zoomDpPerSec - 12f) },
                    modifier = Modifier.size(32.dp).testTag("timeline_zoom_out")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomOut,
                        contentDescription = "Zoom out timeline",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${zoomDpPerSec.toInt()}%",
                    style = MonoBadgeStyle,
                    color = PlayheadCyan
                )
                IconButton(
                    onClick = { onZoomChange(zoomDpPerSec + 12f) },
                    modifier = Modifier.size(32.dp).testTag("timeline_zoom_in")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomIn,
                        contentDescription = "Zoom in timeline",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Track Labels Column
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TrackHeaderBadge("V1", CinemaCoral, 52.dp)
                Spacer(modifier = Modifier.height(6.dp))
                TrackHeaderBadge("T1", CaptionViolet, 28.dp)
                Spacer(modifier = Modifier.height(6.dp))
                audioTracks.take(3).forEachIndexed { idx, track ->
                    val roleColor = when (AudioRole.fromRaw(track.role)) {
                        AudioRole.ORIGINAL -> PlayheadCyan
                        AudioRole.VOICEOVER -> AudioAmber
                        AudioRole.MUSIC -> AudioEmerald
                        AudioRole.SFX -> CaptionViolet
                    }
                    TrackHeaderBadge("A${idx + 1}", roleColor, 30.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Scrollable Multi-Track Canvas with Pinch-to-Zoom & Playhead Scrubbing
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    .pointerInput(zoomDpPerSec) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (abs(zoom - 1f) > 0.015f) {
                                onZoomChange(zoomDpPerSec * zoom)
                            }
                        }
                    }
                    .testTag("timeline_scroll_surface")
            ) {
                Box(modifier = Modifier.width(timelineWidthDp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 1. Timecode Scrub Ruler
                        TimecodeRuler(
                            totalMs = totalMs,
                            dpPerMs = dpPerMs,
                            pxPerMs = pxPerMs,
                            onSeek = { targetMs ->
                                // Snap gently to clip boundaries within 160ms
                                val snapClip = clips.firstOrNull { abs(it.startMs - targetMs) < 160L }
                                if (snapClip != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSeek(snapClip.startMs)
                                } else {
                                    onSeek(targetMs)
                                }
                            }
                        )

                        // 2. V1 Video Clip Track with Draggable Trim Handles
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        ) {
                            clips.forEach { clip ->
                                val xDp = (clip.startMs * dpPerMs).dp
                                val widthDp = ((clip.endMs - clip.startMs).coerceAtLeast(300L) * dpPerMs).dp
                                val isSelected = clip.clipId == selectedClipId
                                VideoClipBlock(
                                    clip = clip,
                                    thumbnails = clipThumbnails[clip.clipId].orEmpty(),
                                    isSelected = isSelected,
                                    pxPerMs = pxPerMs,
                                    onSelect = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectClip(clip.clipId)
                                        onSeek(clip.startMs)
                                    },
                                    onTrimDelta = onTrimSelectedClip,
                                    modifier = Modifier
                                        .offset(x = xDp)
                                        .width(widthDp.coerceAtLeast(36.dp))
                                        .fillMaxHeight()
                                        .padding(vertical = 2.dp, horizontal = 1.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 3. T1 Captions Track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        ) {
                            if (captions.isEmpty()) {
                                Text(
                                    text = "Tap AI Tools to auto-generate synced captions",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                                )
                            } else {
                                captions.forEach { cue ->
                                    val xDp = (cue.startMs * dpPerMs).dp
                                    val wDp = ((cue.endMs - cue.startMs).coerceAtLeast(400L) * dpPerMs).dp
                                    CaptionTrackPill(
                                        cue = cue,
                                        onClick = { onSeek(cue.startMs) },
                                        modifier = Modifier
                                            .offset(x = xDp)
                                            .width(wDp.coerceAtLeast(42.dp))
                                            .fillMaxHeight()
                                            .padding(vertical = 2.dp, horizontal = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 4. A1..A3 Audio Waveform Tracks
                        audioTracks.take(3).forEach { track ->
                            AudioWaveformStrip(
                                track = track,
                                dpPerMs = dpPerMs,
                                totalMs = totalMs,
                                onClick = { onSelectTrack(track.trackId) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Cyan Playhead Needle Overlay
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(playheadOffsetPx, 0) }
                            .matchParentSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(PlayheadCyan)
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = (-5).dp, y = 0.dp)
                                .size(width = 12.dp, height = 10.dp)
                                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(PlayheadCyan)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackHeaderBadge(label: String, color: Color, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .height(height)
            .width(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MonoBadgeStyle,
            color = color
        )
    }
}

@Composable
private fun TimecodeRuler(
    totalMs: Long,
    dpPerMs: Float,
    pxPerMs: Float,
    onSeek: (Long) -> Unit
) {
    val rulerColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(pxPerMs, totalMs) {
                detectTapGestures { offset ->
                    val ms = (offset.x / pxPerMs).toLong().coerceIn(0L, totalMs)
                    onSeek(ms)
                }
            }
            .pointerInput(pxPerMs, totalMs) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val ms = (change.position.x / pxPerMs).toLong().coerceIn(0L, totalMs)
                    onSeek(ms)
                }
            }
            .testTag("timeline_ruler")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalSeconds = ((totalMs / 1000L) + 2).toInt()
            for (sec in 0..totalSeconds) {
                val x = sec * 1000f * pxPerMs
                if (x > size.width) break
                drawLine(
                    color = rulerColor.copy(alpha = 0.65f),
                    start = Offset(x, size.height - 10f),
                    end = Offset(x, size.height),
                    strokeWidth = 2f
                )
                val halfX = x + 500f * pxPerMs
                if (halfX <= size.width) {
                    drawLine(
                        color = rulerColor.copy(alpha = 0.32f),
                        start = Offset(halfX, size.height - 5f),
                        end = Offset(halfX, size.height),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
        val stepSec = if (dpPerMs * 1000f < 28f) 4 else if (dpPerMs * 1000f < 48f) 2 else 1
        val totalSeconds = ((totalMs / 1000L) + 1).toInt()
        for (sec in 0..totalSeconds step stepSec) {
            Text(
                text = "${sec}s",
                style = MaterialTheme.typography.labelSmall,
                color = rulerColor,
                modifier = Modifier
                    .offset(x = (sec * 1000f * dpPerMs + 3f).dp, y = 2.dp)
            )
        }
    }
}

@Composable
private fun VideoClipBlock(
    clip: TimelineClip,
    thumbnails: List<Bitmap>,
    isSelected: Boolean,
    pxPerMs: Float,
    onSelect: () -> Unit,
    onTrimDelta: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) CinemaCoral else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF151C29))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .testTag("timeline_clip_${clip.clipId}")
    ) {
        // Filmstrip preview
        if (thumbnails.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                thumbnails.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        } else {
            val resId = if (clip.sampleScene == "NEON_CITY") {
                R.drawable.img_studio_hero_1795777011469
            } else {
                R.drawable.img_sample_cinema_1795777012583
            }
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.72f,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Scrim & Label
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x6607090E))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = clip.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart)
            )
            if (abs(clip.speed - 1.0) > 0.05) {
                Surface(
                    color = PlayheadCyan.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = "${clip.speed}x",
                        style = MonoBadgeStyle,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Draggable Trim Handles when clip is selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(12.dp)
                    .fillMaxHeight()
                    .background(CinemaCoral)
                    .pointerInput(clip.clipId, pxPerMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaMs = (dragAmount / pxPerMs).toLong()
                            if (abs(deltaMs) >= 30L) onTrimDelta(deltaMs, 0L)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(2.dp).height(18.dp).background(Color.Black))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(12.dp)
                    .fillMaxHeight()
                    .background(CinemaCoral)
                    .pointerInput(clip.clipId, pxPerMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaMs = (dragAmount / pxPerMs).toLong()
                            if (abs(deltaMs) >= 30L) onTrimDelta(0L, deltaMs)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(2.dp).height(18.dp).background(Color.Black))
            }
        }
    }
}

@Composable
private fun CaptionTrackPill(
    cue: CaptionCue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CaptionViolet.copy(alpha = 0.24f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .border(1.dp, CaptionViolet.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = cue.text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AudioWaveformStrip(
    track: AudioTrack,
    dpPerMs: Float,
    totalMs: Long,
    onClick: () -> Unit
) {
    val roleColor = when (AudioRole.fromRaw(track.role)) {
        AudioRole.ORIGINAL -> PlayheadCyan
        AudioRole.VOICEOVER -> AudioAmber
        AudioRole.MUSIC -> AudioEmerald
        AudioRole.SFX -> CaptionViolet
    }
    val effectiveColor = if (track.muted) roleColor.copy(alpha = 0.22f) else roleColor
    val widthDp = ((track.durationMs.coerceAtMost(totalMs).coerceAtLeast(2000L)) * dpPerMs).dp
    val seed = remember(track.trackId) { track.trackId.hashCode() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .offset(x = (track.offsetMs * dpPerMs).dp)
                .width(widthDp)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(effectiveColor.copy(alpha = 0.14f))
                .border(1.dp, effectiveColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp)) {
                val barSpacing = 5f
                val count = (size.width / barSpacing).toInt().coerceIn(8, 220)
                val volScale = if (track.muted) 0.12f else (track.volume.toFloat() / 1.4f).coerceIn(0.15f, 1f)
                val midY = size.height / 2f
                for (i in 0 until count) {
                    val wave = abs(sin((i + seed) * 0.47) * 0.65 + sin((i + seed) * 1.13) * 0.35).toFloat()
                    val h = (size.height * 0.45f * wave * volScale).coerceAtLeast(2f)
                    val x = i * barSpacing
                    drawLine(
                        color = effectiveColor,
                        start = Offset(x, midY - h),
                        end = Offset(x, midY + h),
                        strokeWidth = 2.4f
                    )
                }
            }
            Text(
                text = "${track.name} ${(track.volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 6.dp)
            )
        }
    }
}
