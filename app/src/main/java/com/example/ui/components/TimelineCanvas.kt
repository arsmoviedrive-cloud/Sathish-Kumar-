package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.model.AudioRole
import com.example.data.model.TransitionEasing
import com.example.data.model.TransitionType
import com.example.data.model.VideoProject
import com.example.media.StudioAudioPreset
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CaptionViolet
import com.example.ui.theme.CinemaCoral
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.PlayheadCyan
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class TimelinePanelMode {
    NONE,
    REORDER,
    TRANSITIONS,
    TRIM
}

@Composable
fun TimelineCanvas(
    project: VideoProject,
    playheadMs: Long,
    zoomDpPerSec: Float,
    selectedClipId: String?,
    selectedTrackId: String?,
    clipThumbnails: Map<String, List<Bitmap>>,
    onSeek: (Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    onSelectClip: (String) -> Unit,
    onTrimSelectedClip: (Long, Long) -> Unit,
    onSetTrimRange: (Long, Long) -> Unit,
    onTrimToPlayhead: (Boolean) -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onReorderClip: (Int, Int) -> Unit,
    onUpdateClipTransition: (String, TransitionType, Long, TransitionEasing) -> Unit,
    onApplyTransitionToAll: (TransitionType, Long, TransitionEasing) -> Unit,
    onPreviewTransition: (String) -> Unit,
    onOpenAiClipGenerator: () -> Unit,
    onSelectTrack: (String) -> Unit,
    onAudioVolumeChange: (String, Double) -> Unit,
    onToggleTrackMute: (String) -> Unit,
    onToggleTrackSolo: (String) -> Unit,
    onMoveAudioTrackOffset: (String, Long) -> Unit,
    onSnapAudioTrackToPlayhead: (String) -> Unit,
    onDeleteAudioTrack: (String) -> Unit,
    onImportAudio: (AudioRole) -> Unit,
    onOpenAiMusic: () -> Unit,
    onAddAudioPreset: (StudioAudioPreset) -> Unit,
    onPrepareAudioPresetPreview: (StudioAudioPreset, (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val clips = remember(project.clips) { project.typedClips() }
    val captions = remember(project.captions) { project.typedCaptions() }
    val audioTracks = remember(project.audioTracks) { project.typedAudioTracks() }
    val totalMs = project.totalDurationMs.coerceAtLeast(4000L)
    val selectedClip = clips.firstOrNull { it.clipId == selectedClipId } ?: clips.firstOrNull()

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val audioScrollState = rememberScrollState()

    // Keep Visual (V1/T1) and Separate Audio Layer horizontal scroll positions in lockstep
    LaunchedEffect(scrollState.value) {
        if (!audioScrollState.isScrollInProgress && audioScrollState.value != scrollState.value) {
            audioScrollState.scrollTo(scrollState.value)
        }
    }
    LaunchedEffect(audioScrollState.value) {
        if (!scrollState.isScrollInProgress && scrollState.value != audioScrollState.value) {
            scrollState.scrollTo(audioScrollState.value)
        }
    }

    var panelMode by remember { mutableStateOf(TimelinePanelMode.NONE) }
    var selectedTransitionClipId by remember { mutableStateOf<String?>(null) }

    // Live drag-to-reorder state for direct V1 track manipulation
    var draggingClipIndex by remember { mutableStateOf<Int?>(null) }
    var dragDeltaPx by remember { mutableFloatStateOf(0f) }
    var dropTargetIndex by remember { mutableStateOf<Int?>(null) }

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
        // Timeline Studio Action Toolbar (Transitions, Trim, AI Clip, Reorder + Zoom)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimelineToolbarChip(
                    label = "Transitions",
                    icon = Icons.Filled.CompareArrows,
                    active = panelMode == TimelinePanelMode.TRANSITIONS,
                    accent = PlayheadCyan,
                    testTag = "timeline_transitions_menu_button",
                    onClick = {
                        if (panelMode == TimelinePanelMode.TRANSITIONS) {
                            panelMode = TimelinePanelMode.NONE
                        } else {
                            selectedTransitionClipId = selectedClipId ?: clips.firstOrNull()?.clipId
                            panelMode = TimelinePanelMode.TRANSITIONS
                        }
                    }
                )
                TimelineToolbarChip(
                    label = "Trim",
                    icon = Icons.Filled.ContentCut,
                    active = panelMode == TimelinePanelMode.TRIM,
                    accent = CinemaCoral,
                    testTag = "timeline_trim_button",
                    onClick = {
                        panelMode = if (panelMode == TimelinePanelMode.TRIM) {
                            TimelinePanelMode.NONE
                        } else {
                            TimelinePanelMode.TRIM
                        }
                    }
                )
                TimelineToolbarChip(
                    label = "AI Clip",
                    icon = Icons.Filled.AutoAwesome,
                    active = false,
                    accent = CaptionViolet,
                    testTag = "timeline_ai_clip_button",
                    onClick = onOpenAiClipGenerator
                )
                TimelineToolbarChip(
                    label = if (panelMode == TimelinePanelMode.REORDER) "Done" else "Reorder",
                    icon = if (panelMode == TimelinePanelMode.REORDER) Icons.Filled.Check else Icons.Filled.SwapHoriz,
                    active = panelMode == TimelinePanelMode.REORDER,
                    accent = AudioEmerald,
                    testTag = "timeline_reorder_mode_toggle",
                    onClick = {
                        panelMode = if (panelMode == TimelinePanelMode.REORDER) {
                            TimelinePanelMode.NONE
                        } else {
                            TimelinePanelMode.REORDER
                        }
                    }
                )
            }

            if (panelMode != TimelinePanelMode.REORDER) {
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
        }

        // Inline Transition Customizer Drawer
        AnimatedVisibility(visible = panelMode == TimelinePanelMode.TRANSITIONS && clips.isNotEmpty()) {
            TimelineTransitionMenu(
                clips = clips,
                selectedTransitionClipId = selectedTransitionClipId ?: selectedClip?.clipId,
                onSelectTransitionClipId = { clipId ->
                    selectedTransitionClipId = clipId
                    onSelectClip(clipId)
                },
                onUpdateTransition = onUpdateClipTransition,
                onApplyToAll = onApplyTransitionToAll,
                onPreviewTransition = onPreviewTransition,
                onClose = { panelMode = TimelinePanelMode.NONE }
            )
        }

        // Inline Precision Trim Drawer
        AnimatedVisibility(visible = panelMode == TimelinePanelMode.TRIM && selectedClip != null) {
            TimelinePrecisionTrimBar(
                clip = selectedClip,
                onSetTrimRange = onSetTrimRange,
                onTrimDelta = onTrimSelectedClip,
                onTrimToPlayhead = onTrimToPlayhead,
                onSplitAtPlayhead = onSplitAtPlayhead,
                onClose = { panelMode = TimelinePanelMode.NONE }
            )
        }

        AnimatedContent(
            targetState = panelMode == TimelinePanelMode.REORDER,
            label = "timeline_mode_switch"
        ) { isReorderView ->
            if (isReorderView) {
                ClipReorderStoryboardStrip(
                    clips = clips,
                    selectedClipId = selectedClipId,
                    clipThumbnails = clipThumbnails,
                    onSelectClip = { clipId ->
                        onSelectClip(clipId)
                        clips.firstOrNull { it.clipId == clipId }?.let { onSeek(it.startMs) }
                    },
                    onReorderClip = onReorderClip
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Left Visual Track Labels Rail (V1 Video & T1 Captions)
                    Column(
                        modifier = Modifier
                            .width(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(top = 26.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TrackHeaderBadge(
                            label = "V1",
                            color = CinemaCoral,
                            height = 58.dp,
                            subLabel = "${clips.size}"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TrackHeaderBadge(
                            label = "T1",
                            color = CaptionViolet,
                            height = 28.dp,
                            subLabel = if (captions.isEmpty()) null else "${captions.size}"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Scrollable Multi-Track Canvas with Pinch-to-Zoom, Drag-to-Reorder & Playhead Scrubbing
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState, enabled = draggingClipIndex == null)
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
                                // 1. Timecode Scrub Ruler with Cut-Point Notches
                                TimecodeRuler(
                                    totalMs = totalMs,
                                    clips = clips,
                                    dpPerMs = dpPerMs,
                                    pxPerMs = pxPerMs,
                                    onSeek = { targetMs ->
                                        val snapClip = clips.firstOrNull { abs(it.startMs - targetMs) < 160L }
                                        if (snapClip != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSeek(snapClip.startMs)
                                        } else {
                                            onSeek(targetMs)
                                        }
                                    }
                                )

                                // 2. V1 Video Clip Track with Direct Hold-and-Drag Reordering & Trim Handles
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(58.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                ) {
                                    clips.forEachIndexed { index, clip ->
                                        val xDp = (clip.startMs * dpPerMs).dp
                                        val widthDp = ((clip.endMs - clip.startMs).coerceAtLeast(300L) * dpPerMs).dp
                                        val isSelected = clip.clipId == selectedClipId
                                        val isBeingDragged = draggingClipIndex == index

                                        VideoClipBlock(
                                            clip = clip,
                                            clipIndex = index,
                                            thumbnails = clipThumbnails[clip.clipId].orEmpty(),
                                            isSelected = isSelected,
                                            isDragging = isBeingDragged,
                                            dragOffsetPx = if (isBeingDragged) dragDeltaPx else 0f,
                                            pxPerMs = pxPerMs,
                                            onSelect = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSelectClip(clip.clipId)
                                                onSeek(clip.startMs)
                                            },
                                            onTrimDelta = onTrimSelectedClip,
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onSelectClip(clip.clipId)
                                                draggingClipIndex = index
                                                dragDeltaPx = 0f
                                                dropTargetIndex = index
                                            },
                                            onDragDeltaPx = { dx ->
                                                dragDeltaPx += dx
                                                val draggedCenterMs =
                                                    ((clip.startMs + clip.endMs) / 2f) + (dragDeltaPx / pxPerMs)
                                                var targetIdx = clips.indexOfFirst { other ->
                                                    draggedCenterMs < ((other.startMs + other.endMs) / 2f)
                                                }
                                                if (targetIdx == -1) targetIdx = clips.lastIndex
                                                if (targetIdx != dropTargetIndex) {
                                                    dropTargetIndex = targetIdx
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            },
                                            onDragEnd = {
                                                val from = draggingClipIndex
                                                val to = dropTargetIndex
                                                draggingClipIndex = null
                                                dragDeltaPx = 0f
                                                dropTargetIndex = null
                                                if (from != null && to != null && from != to) {
                                                    onReorderClip(from, to)
                                                }
                                            },
                                            onDragCancel = {
                                                draggingClipIndex = null
                                                dragDeltaPx = 0f
                                                dropTargetIndex = null
                                            },
                                            modifier = Modifier
                                                .offset(x = xDp)
                                                .width(widthDp.coerceAtLeast(48.dp))
                                                .fillMaxHeight()
                                                .padding(vertical = 2.dp, horizontal = 1.dp)
                                        )
                                    }

                                    // Interactive transition junction markers between adjacent clips
                                    if (draggingClipIndex == null) {
                                        for (i in 0 until clips.lastIndex) {
                                            val clipItem = clips[i]
                                            val seamX = (clipItem.endMs * dpPerMs).dp
                                            val isSelectedSeam = panelMode == TimelinePanelMode.TRANSITIONS &&
                                                (selectedTransitionClipId == clipItem.clipId)
                                            TransitionSeamBadge(
                                                clipId = clipItem.clipId,
                                                transitionRaw = clipItem.transition,
                                                transitionDurationMs = clipItem.transitionDurationMs,
                                                isSelected = isSelectedSeam,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedTransitionClipId = clipItem.clipId
                                                    onSelectClip(clipItem.clipId)
                                                    panelMode = TimelinePanelMode.TRANSITIONS
                                                },
                                                modifier = Modifier
                                                    .offset(x = seamX - 24.dp)
                                                    .align(Alignment.CenterStart)
                                                    .zIndex(5f)
                                            )
                                        }
                                    }

                                    // Glowing magnetic insertion bar during direct drag-to-reorder
                                    val activeDrag = draggingClipIndex
                                    val activeDrop = dropTargetIndex
                                    if (activeDrag != null && activeDrop != null && activeDrop != activeDrag && clips.isNotEmpty()) {
                                        val insertionMs = if (activeDrop > activeDrag) {
                                            clips[activeDrop].endMs
                                        } else {
                                            clips[activeDrop].startMs
                                        }
                                        val markerX = (insertionMs * dpPerMs).dp
                                        Box(
                                            modifier = Modifier
                                                .offset(x = markerX - 2.dp)
                                                .width(4.dp)
                                                .fillMaxHeight()
                                                .zIndex(15f)
                                                .background(PlayheadCyan)
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

                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // Cyan Playhead Needle Overlay across Visual Tracks
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

        // Separate Dedicated Audio Track Layer (Background Music, Sound Effects & Inline Volume Sliders)
        if (panelMode != TimelinePanelMode.REORDER) {
            TimelineAudioLayerSection(
                audioTracks = audioTracks,
                selectedTrackId = selectedTrackId,
                playheadMs = playheadMs,
                totalMs = totalMs,
                dpPerMs = dpPerMs,
                pxPerMs = pxPerMs,
                timelineWidthDp = timelineWidthDp,
                playheadOffsetPx = playheadOffsetPx,
                audioScrollState = audioScrollState,
                onSelectTrack = onSelectTrack,
                onVolumeChange = onAudioVolumeChange,
                onToggleMute = onToggleTrackMute,
                onToggleSolo = onToggleTrackSolo,
                onMoveTrackOffset = onMoveAudioTrackOffset,
                onSnapTrackToPlayhead = onSnapAudioTrackToPlayhead,
                onDeleteTrack = onDeleteAudioTrack,
                onImportAudio = onImportAudio,
                onOpenAiMusic = onOpenAiMusic,
                onAddPreset = onAddAudioPreset,
                onPreparePresetPreview = onPrepareAudioPresetPreview
            )
        }
    }
}

@Composable
private fun TimelineToolbarChip(
    label: String,
    icon: ImageVector,
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
                color = if (active) accent else accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.Black else accent,
                modifier = Modifier.size(14.dp)
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
