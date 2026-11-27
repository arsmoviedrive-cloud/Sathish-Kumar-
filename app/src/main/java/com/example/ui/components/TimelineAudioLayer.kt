package com.example.ui.components

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.model.AudioRole
import com.example.data.model.AudioTrack
import com.example.data.model.formatTimecode
import com.example.media.StudioAudioCatalog
import com.example.media.StudioAudioPreset
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CaptionViolet
import com.example.ui.theme.CinemaCoral
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.PlayheadCyan
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sin

private enum class AudioAddDrawerMode {
    NONE,
    MUSIC,
    SFX
}

@Composable
internal fun TimelineAudioLayerSection(
    audioTracks: List<AudioTrack>,
    selectedTrackId: String?,
    playheadMs: Long,
    totalMs: Long,
    dpPerMs: Float,
    pxPerMs: Float,
    timelineWidthDp: Dp,
    playheadOffsetPx: Int,
    audioScrollState: ScrollState,
    onSelectTrack: (String) -> Unit,
    onVolumeChange: (String, Double) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleSolo: (String) -> Unit,
    onMoveTrackOffset: (String, Long) -> Unit,
    onSnapTrackToPlayhead: (String) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onImportAudio: (AudioRole) -> Unit,
    onOpenAiMusic: () -> Unit,
    onAddPreset: (StudioAudioPreset) -> Unit,
    onPreparePresetPreview: (StudioAudioPreset, (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var addDrawerMode by remember { mutableStateOf(AudioAddDrawerMode.NONE) }
    var showAllSliders by remember { mutableStateOf(false) }

    val selectedTrack = audioTracks.firstOrNull { it.trackId == selectedTrackId }
        ?: audioTracks.lastOrNull {
            val r = AudioRole.fromRaw(it.role)
            r == AudioRole.MUSIC || r == AudioRole.SFX
        }
        ?: audioTracks.firstOrNull()

    val musicCount = remember(audioTracks) {
        audioTracks.count { AudioRole.fromRaw(it.role) == AudioRole.MUSIC }
    }
    val sfxCount = remember(audioTracks) {
        audioTracks.count { AudioRole.fromRaw(it.role) == AudioRole.SFX }
    }

    // Separate Audio Layer Container visually distinct from Visual (V1/T1) tracks
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
            )
            .testTag("timeline_audio_layer_section")
    ) {
        // Distinct Layer Divider & Audio Layer Control Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            AudioEmerald.copy(alpha = 0.7f),
                            CaptionViolet.copy(alpha = 0.6f),
                            PlayheadCyan.copy(alpha = 0.4f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = AudioEmerald,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "AUDIO LAYER",
                    style = MonoBadgeStyle,
                    fontWeight = FontWeight.ExtraBold,
                    color = AudioEmerald
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Text(
                        text = "${musicCount}M·${sfxCount}FX",
                        style = MonoBadgeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioLayerActionPill(
                    label = "+ Music",
                    icon = Icons.Filled.MusicNote,
                    active = addDrawerMode == AudioAddDrawerMode.MUSIC,
                    accent = AudioEmerald,
                    testTag = "timeline_add_music_button",
                    onClick = {
                        addDrawerMode = if (addDrawerMode == AudioAddDrawerMode.MUSIC) {
                            AudioAddDrawerMode.NONE
                        } else {
                            AudioAddDrawerMode.MUSIC
                        }
                    }
                )
                AudioLayerActionPill(
                    label = "+ SFX",
                    icon = Icons.Filled.Bolt,
                    active = addDrawerMode == AudioAddDrawerMode.SFX,
                    accent = CaptionViolet,
                    testTag = "timeline_add_sfx_button",
                    onClick = {
                        addDrawerMode = if (addDrawerMode == AudioAddDrawerMode.SFX) {
                            AudioAddDrawerMode.NONE
                        } else {
                            AudioAddDrawerMode.SFX
                        }
                    }
                )
                AudioLayerActionPill(
                    label = if (showAllSliders) "Hide Vols" else "Volumes",
                    icon = Icons.Filled.Tune,
                    active = showAllSliders,
                    accent = PlayheadCyan,
                    testTag = "timeline_audio_volumes_toggle",
                    onClick = { showAllSliders = !showAllSliders }
                )
            }
        }

        // Inline Add Background Music / Add Sound Effects Drawer
        AnimatedVisibility(visible = addDrawerMode != AudioAddDrawerMode.NONE) {
            TimelineAudioAddDrawer(
                mode = addDrawerMode,
                playheadMs = playheadMs,
                onSwitchMode = { addDrawerMode = it },
                onImportAudio = onImportAudio,
                onOpenAiMusic = {
                    addDrawerMode = AudioAddDrawerMode.NONE
                    onOpenAiMusic()
                },
                onAddPreset = { preset ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAddPreset(preset)
                },
                onPreparePresetPreview = onPreparePresetPreview,
                onClose = { addDrawerMode = AudioAddDrawerMode.NONE }
            )
        }

        // Synchronized Multi-Lane Audio Tracks Area
        if (audioTracks.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "No audio layers yet — add background music or sound effects",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = { addDrawerMode = AudioAddDrawerMode.MUSIC },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Audio", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            val laneVerticalScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 148.dp)
                    .verticalScroll(laneVerticalScroll)
            ) {
                // Left Sticky Audio Track Role & Mute Rail
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    var musicIdx = 0
                    var sfxIdx = 0
                    audioTracks.forEach { track ->
                        val role = AudioRole.fromRaw(track.role)
                        val badgeLabel = when (role) {
                            AudioRole.ORIGINAL -> "ORIG"
                            AudioRole.VOICEOVER -> "VO"
                            AudioRole.MUSIC -> {
                                musicIdx += 1
                                "M$musicIdx"
                            }
                            AudioRole.SFX -> {
                                sfxIdx += 1
                                "FX$sfxIdx"
                            }
                        }
                        val accent = audioRoleAccent(role)
                        val isSelected = track.trackId == selectedTrack?.trackId
                        AudioLaneHeaderPill(
                            label = badgeLabel,
                            volumePct = (track.volume * 100).toInt(),
                            accent = accent,
                            isSelected = isSelected,
                            isMuted = track.muted,
                            onSelect = { onSelectTrack(track.trackId) },
                            onToggleMute = { onToggleMute(track.trackId) },
                            testTag = "audio_lane_header_${track.trackId}"
                        )
                    }
                }

                // Horizontally Synced Audio Waveform Lanes + Playhead Needle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(audioScrollState)
                        .testTag("timeline_audio_scroll_surface")
                ) {
                    Box(modifier = Modifier.width(timelineWidthDp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            audioTracks.forEach { track ->
                                DraggableAudioWaveformLane(
                                    track = track,
                                    isSelected = track.trackId == selectedTrack?.trackId,
                                    dpPerMs = dpPerMs,
                                    pxPerMs = pxPerMs,
                                    totalMs = totalMs,
                                    onSelect = { onSelectTrack(track.trackId) },
                                    onOffsetChanged = { newOffset -> onMoveTrackOffset(track.trackId, newOffset) }
                                )
                            }
                        }

                        // Synchronized Playhead Needle continuing across the separate audio layer
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(playheadOffsetPx, 0) }
                                .matchParentSize()
                                .zIndex(20f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(PlayheadCyan)
                            )
                        }
                    }
                }
            }

            // Inline Volume Adjustment Sliders directly inside the Timeline Audio Layer
            TimelineAudioVolumeRack(
                tracks = audioTracks,
                selectedTrack = selectedTrack,
                showAllSliders = showAllSliders,
                onToggleShowAll = { showAllSliders = !showAllSliders },
                onSelectTrack = onSelectTrack,
                onVolumeChange = onVolumeChange,
                onToggleMute = onToggleMute,
                onToggleSolo = onToggleSolo,
                onSnapToPlayhead = onSnapTrackToPlayhead,
                onDeleteTrack = onDeleteTrack
            )
        }
    }
}

@Composable
private fun AudioLaneHeaderPill(
    label: String,
    volumePct: Int,
    accent: Color,
    isSelected: Boolean,
    isMuted: Boolean,
    onSelect: () -> Unit,
    onToggleMute: () -> Unit,
    testTag: String
) {
    val displayColor = if (isMuted) accent.copy(alpha = 0.35f) else accent
    Box(
        modifier = Modifier
            .height(34.dp)
            .width(42.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (isSelected) displayColor.copy(alpha = 0.28f)
                else displayColor.copy(alpha = if (isMuted) 0.07f else 0.14f)
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) displayColor else displayColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(7.dp)
            )
            .clickable {
                if (isSelected) onToggleMute() else onSelect()
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MonoBadgeStyle,
                color = displayColor,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Muted $label" else "$label volume $volumePct percent",
                    tint = displayColor,
                    modifier = Modifier.size(9.dp)
                )
                Text(
                    text = if (isMuted) "0" else "$volumePct",
                    style = MaterialTheme.typography.labelSmall,
                    color = displayColor.copy(alpha = 0.9f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DraggableAudioWaveformLane(
    track: AudioTrack,
    isSelected: Boolean,
    dpPerMs: Float,
    pxPerMs: Float,
    totalMs: Long,
    onSelect: () -> Unit,
    onOffsetChanged: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val role = AudioRole.fromRaw(track.role)
    val accent = audioRoleAccent(role)
    val effectiveColor = if (track.muted) accent.copy(alpha = 0.25f) else accent
    val isRepositionable = role == AudioRole.MUSIC || role == AudioRole.SFX || role == AudioRole.VOICEOVER

    val maxVisibleDur = (totalMs - track.offsetMs).coerceAtLeast(1200L)
    val clippedDurationMs = track.durationMs.coerceIn(500L, maxVisibleDur.coerceAtLeast(track.durationMs.coerceAtMost(totalMs)))
    val widthDp = (clippedDurationMs * dpPerMs).dp.coerceAtLeast(44.dp)
    val seed = remember(track.trackId) { track.trackId.hashCode() }

    val latestOffsetMs by rememberUpdatedState(track.offsetMs)
    val latestOnOffsetChanged by rememberUpdatedState(onOffsetChanged)
    var dragAccumPx by remember(track.trackId) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(
                if (isSelected) accent.copy(alpha = 0.06f)
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .clickable(onClick = onSelect)
            .testTag("timeline_audio_lane_${track.trackId}")
    ) {
        Box(
            modifier = Modifier
                .offset(x = (track.offsetMs * dpPerMs).dp)
                .width(widthDp)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            effectiveColor.copy(alpha = if (isSelected) 0.28f else 0.16f),
                            effectiveColor.copy(alpha = if (isSelected) 0.16f else 0.10f)
                        )
                    )
                )
                .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) effectiveColor else effectiveColor.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(7.dp)
                )
                .then(
                    if (isRepositionable) {
                        Modifier.pointerInput(track.trackId, pxPerMs) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    onSelect()
                                    dragAccumPx = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragAccumPx += dragAmount
                                    val deltaMs = (dragAccumPx / pxPerMs).roundToLong()
                                    if (abs(deltaMs) >= 60L) {
                                        dragAccumPx = 0f
                                        latestOnOffsetChanged((latestOffsetMs + deltaMs).coerceAtLeast(0L))
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            // Live Audio Waveform + Volume Envelope Line
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp)) {
                val barSpacing = if (role == AudioRole.SFX) 4f else 5f
                val count = (size.width / barSpacing).toInt().coerceIn(6, 220)
                val volScale = if (track.muted) 0.10f else (track.volume.toFloat() / 1.5f).coerceIn(0.10f, 1.0f)
                val fadeInFraction = (track.fadeInMs.toFloat() / clippedDurationMs.toFloat()).coerceIn(0f, 0.35f)
                val fadeOutFraction = (track.fadeOutMs.toFloat() / clippedDurationMs.toFloat()).coerceIn(0f, 0.35f)
                val midY = size.height / 2f

                for (i in 0 until count) {
                    val posFrac = i.toFloat() / count.coerceAtLeast(1)
                    val envIn = if (fadeInFraction > 0.001f && posFrac < fadeInFraction) posFrac / fadeInFraction else 1f
                    val envOut = if (fadeOutFraction > 0.001f && posFrac > 1f - fadeOutFraction) {
                        ((1f - posFrac) / fadeOutFraction).coerceAtLeast(0.05f)
                    } else 1f
                    val wave = if (role == AudioRole.SFX) {
                        // Punchy transient SFX waveform envelope
                        val burst = (1f - abs(posFrac - 0.32f) * 1.3f).coerceIn(0.18f, 1f)
                        (abs(sin((i + seed) * 0.92)) * 0.7 + abs(sin((i + seed) * 2.1)) * 0.3).toFloat() * burst
                    } else {
                        abs(sin((i + seed) * 0.44) * 0.62 + sin((i + seed) * 1.15) * 0.38).toFloat()
                    }
                    val h = (size.height * 0.44f * wave * volScale * envIn * envOut).coerceAtLeast(1.8f)
                    val x = i * barSpacing
                    drawLine(
                        color = effectiveColor,
                        start = Offset(x, midY - h),
                        end = Offset(x, midY + h),
                        strokeWidth = if (role == AudioRole.SFX) 2.0f else 2.4f,
                        cap = StrokeCap.Round
                    )
                }

                // Visual volume level line across the clip so slider movement visibly shifts gain line
                val gainY = size.height * (1f - (track.volume.toFloat() / 2.0f).coerceIn(0.05f, 0.95f))
                val envPath = Path().apply {
                    moveTo(0f, if (fadeInFraction > 0.02f) size.height else gainY)
                    if (fadeInFraction > 0.02f) lineTo(size.width * fadeInFraction, gainY)
                    lineTo(size.width * (1f - fadeOutFraction), gainY)
                    if (fadeOutFraction > 0.02f) lineTo(size.width, size.height)
                }
                drawPath(
                    path = envPath,
                    color = Color.White.copy(alpha = if (track.muted) 0.15f else 0.55f),
                    style = Stroke(width = 1.4f)
                )
            }

            // Track Label + Live Volume % Pill
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = when (role) {
                        AudioRole.MUSIC -> Icons.Filled.MusicNote
                        AudioRole.SFX -> Icons.Filled.Bolt
                        else -> Icons.Filled.GraphicEq
                    },
                    contentDescription = null,
                    tint = effectiveColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    color = Color(0xAA07090E),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (track.muted) "MUTE" else "${(track.volume * 100).toInt()}%",
                        style = MonoBadgeStyle,
                        color = if (track.muted) CinemaCoral else effectiveColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                if (track.solo) {
                    Surface(color = AudioAmber, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "S",
                            style = MonoBadgeStyle,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineAudioVolumeRack(
    tracks: List<AudioTrack>,
    selectedTrack: AudioTrack?,
    showAllSliders: Boolean,
    onToggleShowAll: () -> Unit,
    onSelectTrack: (String) -> Unit,
    onVolumeChange: (String, Double) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleSolo: (String) -> Unit,
    onSnapToPlayhead: (String) -> Unit,
    onDeleteTrack: (String) -> Unit
) {
    val tracksToShow = if (showAllSliders) tracks else listOfNotNull(selectedTrack)
    if (tracksToShow.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("timeline_audio_volume_rack"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showAllSliders) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AUDIO TRACK VOLUME SLIDERS (${tracks.size})",
                    style = MonoBadgeStyle,
                    color = PlayheadCyan,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleShowAll)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Compact", style = MaterialTheme.typography.labelSmall)
                        Icon(Icons.Filled.ExpandLess, contentDescription = "Collapse volume sliders", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Column(
            modifier = if (showAllSliders) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tracksToShow.forEach { track ->
                val role = AudioRole.fromRaw(track.role)
                val accent = audioRoleAccent(role)
                val isSelected = track.trackId == selectedTrack?.trackId
                val volumePct = (track.volume * 100).toInt()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected && showAllSliders) accent.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.5.dp,
                            color = if (isSelected) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectTrack(track.trackId) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = accent.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                text = when (role) {
                                    AudioRole.ORIGINAL -> "ORIG"
                                    AudioRole.VOICEOVER -> "VO"
                                    AudioRole.MUSIC -> "MUSIC"
                                    AudioRole.SFX -> "SFX"
                                },
                                style = MonoBadgeStyle,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = track.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Quick Mute, Solo, Snap-to-Playhead, Delete
                        AudioMiniBadgeButton(
                            label = "M",
                            active = track.muted,
                            activeColor = CinemaCoral,
                            testTag = "timeline_audio_mute_${track.trackId}",
                            onClick = { onToggleMute(track.trackId) }
                        )
                        AudioMiniBadgeButton(
                            label = "S",
                            active = track.solo,
                            activeColor = AudioAmber,
                            testTag = "timeline_audio_solo_${track.trackId}",
                            onClick = { onToggleSolo(track.trackId) }
                        )
                        if (role == AudioRole.SFX || role == AudioRole.MUSIC) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(5.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .clickable { onSnapToPlayhead(track.trackId) }
                                    .testTag("timeline_audio_snap_${track.trackId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MyLocation,
                                        contentDescription = "Align start to playhead",
                                        tint = PlayheadCyan,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text("@Head", style = MonoBadgeStyle, color = PlayheadCyan)
                                }
                            }
                        }
                        if (tracks.size > 1) {
                            IconButton(
                                onClick = { onDeleteTrack(track.trackId) },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("timeline_audio_delete_${track.trackId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Remove ${track.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        if (!showAllSliders && tracks.size > 1) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = onToggleShowAll)
                                    .testTag("timeline_audio_expand_all_sliders")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("All (${tracks.size})", style = MonoBadgeStyle, color = PlayheadCyan)
                                    Icon(
                                        imageVector = Icons.Filled.ExpandMore,
                                        contentDescription = "Show all track volume sliders",
                                        tint = PlayheadCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Inline Volume Adjustment Slider (0% - 200%)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (track.muted || volumePct == 0) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = "Volume for ${track.name}",
                            tint = if (track.muted) CinemaCoral else accent,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onToggleMute(track.trackId) }
                        )
                        Slider(
                            value = if (track.muted) 0f else track.volume.toFloat().coerceIn(0f, 2f),
                            onValueChange = { newVal ->
                                if (track.muted && newVal > 0.02f) {
                                    onToggleMute(track.trackId)
                                }
                                onVolumeChange(track.trackId, newVal.toDouble())
                            },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = accent.copy(alpha = 0.22f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .testTag("timeline_audio_volume_slider_${track.trackId}")
                        )
                        Text(
                            text = if (track.muted) "MUTE" else "$volumePct%",
                            style = MonoBadgeStyle,
                            color = if (track.muted) CinemaCoral else accent,
                            modifier = Modifier.width(42.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineAudioAddDrawer(
    mode: AudioAddDrawerMode,
    playheadMs: Long,
    onSwitchMode: (AudioAddDrawerMode) -> Unit,
    onImportAudio: (AudioRole) -> Unit,
    onOpenAiMusic: () -> Unit,
    onAddPreset: (StudioAudioPreset) -> Unit,
    onPreparePresetPreview: (StudioAudioPreset, (String) -> Unit) -> Unit,
    onClose: () -> Unit
) {
    val isMusic = mode == AudioAddDrawerMode.MUSIC
    val presets = if (isMusic) StudioAudioCatalog.musicPresets else StudioAudioCatalog.sfxPresets
    val accent = if (isMusic) AudioEmerald else CaptionViolet

    var playingPresetId by remember { mutableStateOf<String?>(null) }
    val playerHolder = remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPreview() {
        runCatching {
            playerHolder.value?.stop()
            playerHolder.value?.release()
        }
        playerHolder.value = null
        playingPresetId = null
    }

    DisposableEffect(Unit) {
        onDispose { stopPreview() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, accent.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("timeline_audio_add_drawer"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Drawer Header + Mode Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AudioLayerActionPill(
                    label = "Background Music",
                    icon = Icons.Filled.LibraryMusic,
                    active = isMusic,
                    accent = AudioEmerald,
                    testTag = "drawer_tab_music",
                    onClick = {
                        stopPreview()
                        onSwitchMode(AudioAddDrawerMode.MUSIC)
                    }
                )
                AudioLayerActionPill(
                    label = "Sound Effects",
                    icon = Icons.Filled.Bolt,
                    active = !isMusic,
                    accent = CaptionViolet,
                    testTag = "drawer_tab_sfx",
                    onClick = {
                        stopPreview()
                        onSwitchMode(AudioAddDrawerMode.SFX)
                    }
                )
            }
            IconButton(
                onClick = {
                    stopPreview()
                    onClose()
                },
                modifier = Modifier.size(36.dp).testTag("close_audio_add_drawer")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close add audio menu", modifier = Modifier.size(18.dp))
            }
        }

        // Import & AI Music Quick Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = {
                    stopPreview()
                    onImportAudio(if (isMusic) AudioRole.MUSIC else AudioRole.SFX)
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .testTag(if (isMusic) "import_music_file_button" else "import_sfx_file_button")
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isMusic) "Import Music File" else "Import SFX File",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isMusic) {
                OutlinedButton(
                    onClick = {
                        stopPreview()
                        onOpenAiMusic()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .testTag("timeline_open_ai_music_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CaptionViolet, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Music (Lyria)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    color = CaptionViolet.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, CaptionViolet.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "Drops @ ${formatTimecode(playheadMs)}",
                        style = MonoBadgeStyle,
                        color = CaptionViolet,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Auditionable Studio Music Beds / Sound Effects Shelf
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                val isPlayingThis = playingPresetId == preset.id
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .width(158.dp)
                        .border(
                            width = if (isPlayingThis) 1.5.dp else 1.dp,
                            color = if (isPlayingThis) accent else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .testTag("audio_preset_card_${preset.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "%.1fs".format(preset.durationMs / 1000f),
                                style = MonoBadgeStyle,
                                color = accent
                            )
                        }
                        Text(
                            text = preset.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (isPlayingThis) CinemaCoral else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (isPlayingThis) {
                                            stopPreview()
                                        } else {
                                            stopPreview()
                                            playingPresetId = preset.id
                                            onPreparePresetPreview(preset) { path ->
                                                runCatching {
                                                    val mp = MediaPlayer().apply {
                                                        setDataSource(path)
                                                        setOnCompletionListener {
                                                            stopPreview()
                                                        }
                                                        prepare()
                                                        start()
                                                    }
                                                    playerHolder.value = mp
                                                }.onFailure {
                                                    stopPreview()
                                                }
                                            }
                                        }
                                    }
                                    .testTag("audition_preset_${preset.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlayingThis) "Stop preview" else "Preview ${preset.name}",
                                        tint = if (isPlayingThis) Color.White else accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Surface(
                                color = accent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        stopPreview()
                                        onAddPreset(preset)
                                    }
                                    .testTag("add_preset_${preset.id}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Add ${preset.name}",
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Add",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLayerActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (active) accent else accent.copy(alpha = 0.40f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.Black else accent,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.Black else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AudioMiniBadgeButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(5.dp),
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MonoBadgeStyle,
                color = if (active) Color.Black else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun audioRoleAccent(role: AudioRole): Color = when (role) {
    AudioRole.ORIGINAL -> PlayheadCyan
    AudioRole.VOICEOVER -> AudioAmber
    AudioRole.MUSIC -> AudioEmerald
    AudioRole.SFX -> CaptionViolet
}
