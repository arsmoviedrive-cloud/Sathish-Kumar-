package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TimelineClip
import com.example.data.model.TransitionEasing
import com.example.data.model.TransitionType
import com.example.data.model.formatTimecode
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CaptionViolet
import com.example.ui.theme.CinemaCoral
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.PlayheadCyan
import com.example.ui.viewmodel.AiClipGenState
import kotlin.math.roundToLong

@Composable
internal fun TimelineTransitionMenu(
    clips: List<TimelineClip>,
    selectedTransitionClipId: String?,
    onSelectTransitionClipId: (String) -> Unit,
    onUpdateTransition: (clipId: String, type: TransitionType, durationMs: Long, easing: TransitionEasing) -> Unit,
    onApplyToAll: (type: TransitionType, durationMs: Long, easing: TransitionEasing) -> Unit,
    onPreviewTransition: (clipId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeClip = clips.firstOrNull { it.clipId == selectedTransitionClipId }
        ?: clips.firstOrNull()
        ?: return
    val activeIndex = clips.indexOfFirst { it.clipId == activeClip.clipId }.coerceAtLeast(0)
    val nextClip = clips.getOrNull(activeIndex + 1)

    val currentType = TransitionType.fromRaw(activeClip.transition)
    val currentDuration = activeClip.transitionDurationMs.coerceIn(150L, 1500L)
    val currentEasing = TransitionEasing.fromRaw(activeClip.transitionEasing)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("timeline_transition_menu"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header row with seam label + close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (nextClip != null) {
                        "TRANSITION: #${activeIndex + 1} ${activeClip.label} → #${activeIndex + 2} ${nextClip.label}"
                    } else {
                        "OUTRO TRANSITION: #${activeIndex + 1} ${activeClip.label}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PlayheadCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${currentType.label} • ${currentDuration}ms • ${currentEasing.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("close_transition_menu_button")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close transition menu")
            }
        }

        // Clip seam selector pills
        if (clips.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                clips.forEachIndexed { idx, clip ->
                    val isSelectedSeam = clip.clipId == activeClip.clipId
                    val seamLabel = if (idx < clips.lastIndex) "#${idx + 1}→#${idx + 2}" else "#${idx + 1} End"
                    val seamCode = TransitionType.fromRaw(clip.transition).shortCode
                    FilterChip(
                        selected = isSelectedSeam,
                        onClick = { onSelectTransitionClipId(clip.clipId) },
                        label = { Text("$seamLabel ($seamCode)", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("transition_seam_chip_$idx")
                    )
                }
            }
        }

        // 11 Cinematic Transition Styles Picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransitionType.entries.forEach { style ->
                val isSelected = style == currentType
                Surface(
                    color = if (isSelected) CinemaCoral.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .width(108.dp)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) CinemaCoral else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            onUpdateTransition(activeClip.clipId, style, currentDuration, currentEasing)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("transition_style_${style.raw.lowercase()}")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Surface(
                                color = if (isSelected) CinemaCoral else PlayheadCyan.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = style.shortCode,
                                    style = MonoBadgeStyle,
                                    color = if (isSelected) Color.Black else PlayheadCyan,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = style.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = style.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Duration & Easing Customization
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Duration",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = currentDuration.toFloat(),
                onValueChange = { ms ->
                    val snapped = ((ms / 50f).roundToLong() * 50L).coerceIn(150L, 1500L)
                    onUpdateTransition(activeClip.clipId, currentType, snapped, currentEasing)
                },
                valueRange = 150f..1500f,
                modifier = Modifier
                    .weight(1f)
                    .testTag("transition_duration_slider")
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "${currentDuration}ms",
                    style = MonoBadgeStyle,
                    color = PlayheadCyan,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Curve:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TransitionEasing.entries.forEach { easing ->
                FilterChip(
                    selected = easing == currentEasing,
                    onClick = { onUpdateTransition(activeClip.clipId, currentType, currentDuration, easing) },
                    label = { Text(easing.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("transition_easing_${easing.raw.lowercase()}")
                )
            }
        }

        // Action bar: Preview Cut & Apply to All
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { onPreviewTransition(activeClip.clipId) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("preview_transition_button")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Preview Cut", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onApplyToAll(currentType, currentDuration, currentEasing) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("apply_all_transitions_button")
            ) {
                Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Apply to All", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun TimelinePrecisionTrimBar(
    clip: TimelineClip?,
    onSetTrimRange: (Long, Long) -> Unit,
    onTrimDelta: (Long, Long) -> Unit,
    onTrimToPlayhead: (Boolean) -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (clip == null) return

    val maxRangeMs = (clip.trimEndMs + 3000L).coerceAtLeast(8000L).toFloat()
    val safeStart = clip.trimStartMs.toFloat().coerceIn(0f, maxRangeMs - 500f)
    val safeEnd = clip.trimEndMs.toFloat().coerceIn(safeStart + 500f, maxRangeMs)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("timeline_precision_trim_bar"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TRIM CLIP • ${clip.label}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CinemaCoral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "In ${formatTimecode(clip.trimStartMs)}  •  Out ${formatTimecode(clip.trimEndMs)}  •  Dur %.2fs".format(
                        clip.effectiveDurationMs / 1000f
                    ),
                    style = MonoBadgeStyle,
                    color = PlayheadCyan
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("close_trim_bar_button")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close trim controls")
            }
        }

        // Playhead Trim Actions: Set In, Split, Set Out
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { onTrimToPlayhead(true) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("trim_set_in_button")
            ) {
                Text("Set In", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onSplitAtPlayhead,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("trim_split_button")
            ) {
                Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Split", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = { onTrimToPlayhead(false) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("trim_set_out_button")
            ) {
                Text("Set Out", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        // Dual-Handle Precision Range Slider
        RangeSlider(
            value = safeStart..safeEnd,
            onValueChange = { range ->
                val newStart = range.start.roundToLong().coerceAtLeast(0L)
                val newEnd = range.endInclusive.roundToLong().coerceAtLeast(newStart + 500L)
                onSetTrimRange(newStart, newEnd)
            },
            valueRange = 0f..maxRangeMs,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("trim_range_slider")
        )

        // Fine 0.25s In/Out Nudges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onTrimDelta(-250L, 0L) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("In -0.25s", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { onTrimDelta(250L, 0L) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("In +0.25s", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { onTrimDelta(0L, -250L) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("Out -0.25s", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { onTrimDelta(0L, 250L) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("Out +0.25s", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiClipGeneratorSheet(
    aiClipGenState: AiClipGenState,
    aspectRatioLabel: String,
    onGenerate: (prompt: String, visualStyle: String, durationSec: Int, cameraMotion: String) -> Unit,
    onResetState: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var promptText by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("Cinematic 35mm") }
    var selectedDurationSec by remember { mutableIntStateOf(5) }
    var selectedMotion by remember { mutableStateOf("AI_ZOOM_IN") }

    val styleOptions = remember {
        listOf(
            "Cinematic 35mm",
            "Cyberpunk Neon",
            "Golden Hour Drone",
            "Anime Cel",
            "Noir Monochrome",
            "Product Macro"
        )
    }
    val motionOptions = remember {
        listOf(
            "AI_ZOOM_IN" to "Slow Zoom In",
            "AI_PAN_LEFT" to "Pan Left",
            "AI_PAN_RIGHT" to "Pan Right",
            "AI_DRAMATIC_PUSH" to "Dramatic Push"
        )
    }
    val inspirationPrompts = remember {
        listOf(
            "Aerial drone sweeping over misty pine mountains at sunrise",
            "Rain-slicked Tokyo neon alleyway reflecting glowing cyan lanterns",
            "Sleek espresso pour in slow motion with warm tungsten rim light",
            "Futuristic glass skyline above clouds at golden dusk"
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            onResetState()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("ai_clip_generator_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = CaptionViolet
                    )
                    Column {
                        Text(
                            text = "Create AI Video Clip",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gemini AI generates cinema visuals + animated camera motion ($aspectRatioLabel)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = {
                        onResetState()
                        onDismiss()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close AI clip creator")
                }
            }

            // Live generation status / result cards
            when (aiClipGenState) {
                is AiClipGenState.Loading -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = aiClipGenState.stage,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                is AiClipGenState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = aiClipGenState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                is AiClipGenState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AudioEmerald.copy(alpha = 0.14f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AudioEmerald.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = AudioEmerald)
                                Text(
                                    text = "Added '${aiClipGenState.title}' to Timeline!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AudioEmerald
                                )
                            }
                            Image(
                                bitmap = aiClipGenState.bitmap.asImageBitmap(),
                                contentDescription = "Generated AI clip frame",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(148.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onResetState()
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text("Edit on Timeline")
                                }
                                OutlinedButton(
                                    onClick = { onResetState() },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text("Create Another")
                                }
                            }
                        }
                    }
                }

                AiClipGenState.Idle -> Unit
            }

            // Prompt input
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it.take(260) },
                label = { Text("Describe your video clip scene") },
                placeholder = { Text("e.g. Dramatic sunset drone sweep over ocean cliffs") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_clip_prompt_input")
            )

            // Quick inspiration prompt chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                inspirationPrompts.forEachIndexed { idx, idea ->
                    FilterChip(
                        selected = promptText == idea,
                        onClick = { promptText = idea },
                        label = {
                            Text(
                                text = idea.take(30) + "...",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("ai_prompt_idea_$idx")
                    )
                }
            }

            // Visual Style selector
            Text(
                text = "Visual Style",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                styleOptions.forEach { style ->
                    FilterChip(
                        selected = selectedStyle == style,
                        onClick = { selectedStyle = style },
                        label = { Text(style) }
                    )
                }
            }

            // Duration & Camera Motion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Clip Length",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3, 5, 8).forEach { sec ->
                            FilterChip(
                                selected = selectedDurationSec == sec,
                                onClick = { selectedDurationSec = sec },
                                label = { Text("${sec}s") }
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = PlayheadCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Camera Motion",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                motionOptions.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedMotion == key,
                        onClick = { selectedMotion = key },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    onGenerate(promptText, selectedStyle, selectedDurationSec, selectedMotion)
                },
                enabled = aiClipGenState !is AiClipGenState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("generate_ai_clip_confirm_button")
            ) {
                if (aiClipGenState is AiClipGenState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating AI Video Clip...")
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate & Add AI Clip (${selectedDurationSec}s)", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
