package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.model.AudioRole
import com.example.data.model.AudioTrack
import com.example.data.model.CaptionCue
import com.example.data.model.TimelineClip
import com.example.data.model.TransitionType
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
internal fun TrackHeaderBadge(
    label: String,
    color: Color,
    height: Dp,
    subLabel: String? = null,
    isMuted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val activeColor = if (isMuted) color.copy(alpha = 0.35f) else color
    Box(
        modifier = Modifier
            .height(height)
            .width(40.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(activeColor.copy(alpha = if (isMuted) 0.08f else 0.16f))
            .border(1.dp, activeColor.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MonoBadgeStyle,
                color = activeColor,
                fontWeight = FontWeight.Bold
            )
            if (onClick != null) {
                Icon(
                    imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute $label" else "Mute $label",
                    tint = activeColor,
                    modifier = Modifier.size(11.dp)
                )
            } else if (!subLabel.isNullOrBlank()) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = activeColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
internal fun TimecodeRuler(
    totalMs: Long,
    clips: List<TimelineClip>,
    dpPerMs: Float,
    pxPerMs: Float,
    onSeek: (Long) -> Unit
) {
    val rulerColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
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
                        color = rulerColor.copy(alpha = 0.30f),
                        start = Offset(halfX, size.height - 5f),
                        end = Offset(halfX, size.height),
                        strokeWidth = 1.4f
                    )
                }
            }
            // Subtle clip cut-boundary notches along the ruler
            clips.forEach { clip ->
                val bx = clip.startMs * pxPerMs
                if (bx > 0f && bx < size.width) {
                    drawCircle(
                        color = CinemaCoral.copy(alpha = 0.85f),
                        radius = 3.2f,
                        center = Offset(bx, size.height - 3.5f)
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
internal fun VideoClipBlock(
    clip: TimelineClip,
    clipIndex: Int,
    thumbnails: List<Bitmap>,
    isSelected: Boolean,
    isDragging: Boolean,
    dragOffsetPx: Float,
    pxPerMs: Float,
    onSelect: () -> Unit,
    onTrimDelta: (Long, Long) -> Unit,
    onDragStart: () -> Unit,
    onDragDeltaPx: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDeltaPx)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)

    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "clip_drag_scale"
    )

    val borderColor = when {
        isDragging -> PlayheadCyan
        isSelected -> CinemaCoral
        else -> MaterialTheme.colorScheme.outline
    }
    val durationSec = ((clip.endMs - clip.startMs).coerceAtLeast(300L) / 1000f)

    Box(
        modifier = modifier
            .zIndex(if (isDragging) 12f else if (isSelected) 2f else 1f)
            .graphicsLayer {
                translationX = if (isDragging) dragOffsetPx else 0f
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = if (isDragging) 0.94f else 1f
            }
            .then(
                if (isDragging) Modifier.shadow(10.dp, RoundedCornerShape(9.dp))
                else Modifier
            )
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF151C29))
            .border(if (isDragging || isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(9.dp))
            .clickable(onClick = onSelect)
            .pointerInput(clip.clipId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { latestOnDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        latestOnDragDelta(dragAmount.x)
                    },
                    onDragEnd = { latestOnDragEnd() },
                    onDragCancel = { latestOnDragCancel() }
                )
            }
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
                alpha = 0.74f,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gradient scrim for crisp readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xA607090E),
                            Color(0x3307090E),
                            Color(0x9907090E)
                        )
                    )
                )
                .padding(horizontal = if (isSelected) 15.dp else 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Surface(
                    color = if (isDragging) PlayheadCyan else CinemaCoral.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "#${clipIndex + 1}",
                        style = MonoBadgeStyle,
                        color = if (isDragging) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = clip.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = "%.1fs".format(durationSec),
                    style = MonoBadgeStyle,
                    color = Color(0xFFE2E8F0)
                )
                if (clip.sourceUri.startsWith("ai_file://")) {
                    Surface(
                        color = CaptionViolet.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "AI",
                            style = MonoBadgeStyle,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                if (abs(clip.speed - 1.0) > 0.05) {
                    Surface(
                        color = PlayheadCyan.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(4.dp)
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

            Icon(
                imageVector = Icons.Filled.DragIndicator,
                contentDescription = "Hold and drag to reorder clip",
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
            )
        }

        // Draggable Trim Handles when clip is selected and not being reordered
        if (isSelected && !isDragging) {
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
internal fun TransitionSeamBadge(
    clipId: String,
    transitionRaw: String,
    transitionDurationMs: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = TransitionType.fromRaw(transitionRaw)
    val badgeText = if (transition == TransitionType.CUT) "+" else transition.shortCode
    val bgColor = when {
        isSelected -> CinemaCoral
        transition == TransitionType.CUT -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> PlayheadCyan
    }
    val contentColor = when {
        isSelected -> Color.White
        transition == TransitionType.CUT -> MaterialTheme.colorScheme.onSurface
        else -> Color.Black
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .testTag("timeline_transition_seam_$clipId"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = bgColor,
            shape = CircleShape,
            modifier = Modifier
                .size(24.dp)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = badgeText,
                    style = MonoBadgeStyle,
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Dedicated Storyboard Clip Sequence Reorder Deck allowing instant drag-to-reorder
 * (no long-press delay) and accessible 1-tap Move Left/Right controls.
 */
@Composable
internal fun ClipReorderStoryboardStrip(
    clips: List<TimelineClip>,
    selectedClipId: String?,
    clipThumbnails: Map<String, List<Bitmap>>,
    onSelectClip: (String) -> Unit,
    onReorderClip: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cardWidthDp = 152.dp
    val cardSpacingDp = 10.dp
    val slotStridePx = with(density) { (cardWidthDp + cardSpacingDp).toPx() }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragTranslationPx by remember { mutableFloatStateOf(0f) }
    var hoverTargetIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CLIP SEQUENCE (${clips.size} CLIPS) • DRAG CARDS OR TAP ARROWS",
                style = MaterialTheme.typography.labelSmall,
                color = PlayheadCyan,
                fontWeight = FontWeight.SemiBold
            )
            if (draggingIndex != null && hoverTargetIndex != null) {
                Surface(
                    color = PlayheadCyan.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "DROP AT #${hoverTargetIndex!! + 1}",
                        style = MonoBadgeStyle,
                        color = PlayheadCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(cardSpacingDp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            clips.forEachIndexed { index, clip ->
                val isDragging = draggingIndex == index
                val isTarget = hoverTargetIndex == index && draggingIndex != null && draggingIndex != index
                val isSelected = clip.clipId == selectedClipId
                val durationSec = ((clip.endMs - clip.startMs).coerceAtLeast(300L) / 1000f)
                val transition = TransitionType.fromRaw(clip.transition)

                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.04f else 1f,
                    animationSpec = spring(stiffness = 480f),
                    label = "storyboard_card_scale"
                )

                val latestIndex by rememberUpdatedState(index)
                val latestClipCount by rememberUpdatedState(clips.size)

                Box(
                    modifier = Modifier
                        .width(cardWidthDp)
                        .zIndex(if (isDragging) 10f else 1f)
                        .graphicsLayer {
                            translationX = if (isDragging) dragTranslationPx else 0f
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            width = if (isDragging || isTarget || isSelected) 2.dp else 1.dp,
                            color = when {
                                isDragging -> PlayheadCyan
                                isTarget -> AudioEmerald
                                isSelected -> CinemaCoral
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectClip(clip.clipId) }
                        .pointerInput(clip.clipId, slotStridePx) {
                            detectDragGestures(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggingIndex = latestIndex
                                    dragTranslationPx = 0f
                                    hoverTargetIndex = latestIndex
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragTranslationPx += dragAmount.x
                                    val shift = (dragTranslationPx / slotStridePx).roundToInt()
                                    val candidate = (latestIndex + shift).coerceIn(0, (latestClipCount - 1).coerceAtLeast(0))
                                    if (candidate != hoverTargetIndex) {
                                        hoverTargetIndex = candidate
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    val from = draggingIndex
                                    val to = hoverTargetIndex
                                    draggingIndex = null
                                    dragTranslationPx = 0f
                                    hoverTargetIndex = null
                                    if (from != null && to != null && from != to) {
                                        onReorderClip(from, to)
                                    }
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragTranslationPx = 0f
                                    hoverTargetIndex = null
                                }
                            )
                        }
                        .testTag("reorder_card_${clip.clipId}")
                ) {
                    Column {
                        // Thumbnail preview header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            val bmp = clipThumbnails[clip.clipId]?.firstOrNull()
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = clip.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val resId = if (clip.sampleScene == "NEON_CITY") {
                                    R.drawable.img_studio_hero_1795777011469
                                } else {
                                    R.drawable.img_sample_cinema_1795777012583
                                }
                                Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = clip.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x6607090E))
                                    .padding(6.dp)
                            ) {
                                Surface(
                                    color = if (isSelected) CinemaCoral else Color(0xCC0D1118),
                                    shape = RoundedCornerShape(5.dp),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MonoBadgeStyle,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = Color(0xCC0D1118),
                                    shape = RoundedCornerShape(5.dp),
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                ) {
                                    Text(
                                        text = "%.1fs".format(durationSec),
                                        style = MonoBadgeStyle,
                                        color = PlayheadCyan,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.DragIndicator,
                                    contentDescription = "Drag to reorder",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                )
                            }
                        }

                        // Metadata & accessible 1-tap reorder arrows
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(
                                text = clip.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = transition.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onReorderClip(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("move_clip_left_${clip.clipId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Move ${clip.label} earlier"
                                    )
                                }
                                Text(
                                    text = "${index + 1}/${clips.size}",
                                    style = MonoBadgeStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = { onReorderClip(index, index + 1) },
                                    enabled = index < clips.lastIndex,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("move_clip_right_${clip.clipId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Move ${clip.label} later"
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
internal fun CaptionTrackPill(
    cue: CaptionCue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CaptionViolet.copy(alpha = 0.24f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .border(1.dp, CaptionViolet.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
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
internal fun AudioWaveformStrip(
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
    val effectiveDuration = track.durationMs.coerceAtMost(totalMs).coerceAtLeast(2000L)
    val widthDp = (effectiveDuration * dpPerMs).dp
    val seed = remember(track.trackId) { track.trackId.hashCode() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
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
                val fadeInFraction = (track.fadeInMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 0.35f)
                val fadeOutFraction = (track.fadeOutMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 0.35f)
                val midY = size.height / 2f
                for (i in 0 until count) {
                    val posFrac = i.toFloat() / count.coerceAtLeast(1)
                    val envIn = if (fadeInFraction > 0.001f && posFrac < fadeInFraction) posFrac / fadeInFraction else 1f
                    val envOut = if (fadeOutFraction > 0.001f && posFrac > 1f - fadeOutFraction) {
                        ((1f - posFrac) / fadeOutFraction).coerceAtLeast(0.05f)
                    } else 1f
                    val wave = abs(sin((i + seed) * 0.47) * 0.65 + sin((i + seed) * 1.13) * 0.35).toFloat()
                    val h = (size.height * 0.45f * wave * volScale * envIn * envOut).coerceAtLeast(2f)
                    val x = i * barSpacing
                    drawLine(
                        color = effectiveColor,
                        start = Offset(x, midY - h),
                        end = Offset(x, midY + h),
                        strokeWidth = 2.4f
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "${track.name} ${(track.volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.muted) {
                    Surface(color = CinemaCoral.copy(alpha = 0.85f), shape = RoundedCornerShape(4.dp)) {
                        Text("M", style = MonoBadgeStyle, color = Color.Black, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                } else if (track.solo) {
                    Surface(color = AudioAmber.copy(alpha = 0.9f), shape = RoundedCornerShape(4.dp)) {
                        Text("S", style = MonoBadgeStyle, color = Color.Black, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}
