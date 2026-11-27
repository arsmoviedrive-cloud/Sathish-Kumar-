package com.example.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AudioRole
import com.example.data.model.CaptionStyle
import com.example.data.model.CodecOption
import com.example.data.model.ColorPreset
import com.example.data.model.ExportDestination
import com.example.data.model.ExportFormatOption
import com.example.data.model.ResolutionOption
import com.example.data.model.TransitionType
import com.example.data.model.VideoProject
import com.example.data.model.formatTimecode
import com.example.media.StudioAudioCatalog
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CaptionViolet
import com.example.ui.theme.CinemaCoral
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.PlayheadCyan
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.EditorUiState
import com.example.ui.viewmodel.StudioViewModel

@Composable
fun TimelineToolsPanel(
    project: VideoProject,
    state: EditorUiState,
    viewModel: StudioViewModel,
    onRecordClip: () -> Unit,
    onImportClip: () -> Unit,
    onOpenAiClipGenerator: () -> Unit
) {
    val clips = remember(project.clips) { project.typedClips() }
    val selectedClip = clips.firstOrNull { it.clipId == state.selectedClipId } ?: clips.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary Action Row
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRecordClip,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("editor_record_clip_button")
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Record Clip")
            }
            FilledTonalButton(
                onClick = onOpenAiClipGenerator,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("editor_ai_clip_button")
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AI Clip")
            }
            FilledTonalButton(
                onClick = onImportClip,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("editor_import_clip_button")
            ) {
                Icon(Icons.Filled.VideoCall, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Clip")
            }
            FilledTonalButton(
                onClick = { viewModel.splitClipAtPlayhead() },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("split_clip_button")
            ) {
                Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Split")
            }
            OutlinedButton(
                onClick = { viewModel.addSampleBRollClip() },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ B-Roll")
            }
            OutlinedButton(
                onClick = { viewModel.duplicateSelectedClip() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate clip", modifier = Modifier.size(18.dp))
            }
            OutlinedButton(
                onClick = { viewModel.deleteSelectedClip() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete clip", modifier = Modifier.size(18.dp))
            }
        }

        if (selectedClip != null) {
            val selectedIndex = clips.indexOfFirst { it.clipId == selectedClip.clipId }.coerceAtLeast(0)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${selectedIndex + 1} • ${selectedClip.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "In ${formatTimecode(selectedClip.trimStartMs)} • Out ${formatTimecode(selectedClip.trimEndMs)}",
                            style = MonoBadgeStyle,
                            color = PlayheadCyan
                        )
                    }

                    // Sequence Reorder Controls
                    if (clips.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.moveSelectedClipBy(-1) },
                                enabled = selectedIndex > 0,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("move_clip_earlier_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("Move Earlier", style = MaterialTheme.typography.labelMedium)
                            }
                            FilledTonalButton(
                                onClick = { viewModel.moveSelectedClipBy(1) },
                                enabled = selectedIndex < clips.lastIndex,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("move_clip_later_button")
                            ) {
                                Text("Move Later", style = MaterialTheme.typography.labelMedium)
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Precision Trim Controls (Playhead In/Out + 0.25s Nudges)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.trimSelectedClipToPlayhead(trimStart = true) },
                            modifier = Modifier.weight(1f).testTag("panel_trim_in_playhead")
                        ) { Text("Trim In @ Head", style = MaterialTheme.typography.labelMedium) }
                        FilledTonalButton(
                            onClick = { viewModel.trimSelectedClipToPlayhead(trimStart = false) },
                            modifier = Modifier.weight(1f).testTag("panel_trim_out_playhead")
                        ) { Text("Trim Out @ Head", style = MaterialTheme.typography.labelMedium) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.trimSelectedClip(250L, 0L) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Trim In +0.25s", style = MaterialTheme.typography.labelMedium) }
                        OutlinedButton(
                            onClick = { viewModel.trimSelectedClip(0L, -250L) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Trim Out -0.25s", style = MaterialTheme.typography.labelMedium) }
                    }

                    Text("Playback Speed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { spd ->
                            FilterChip(
                                selected = kotlin.math.abs(selectedClip.speed - spd) < 0.05,
                                onClick = { viewModel.updateSelectedClipSpeed(spd) },
                                label = { Text("${spd}x") }
                            )
                        }
                    }

                    Text("Outgoing Transition", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TransitionType.entries.forEach { tr ->
                            FilterChip(
                                selected = selectedClip.transition == tr.raw,
                                onClick = { viewModel.updateSelectedClipTransition(tr) },
                                label = { Text(tr.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiStudioPanel(
    project: VideoProject,
    state: EditorUiState,
    viewModel: StudioViewModel
) {
    var selectedPacing by remember { mutableStateOf("Viral Fast-Cut") }
    var cutBrief by remember { mutableStateOf("") }
    var captionLanguage by remember { mutableStateOf("English") }
    var captionContext by remember { mutableStateOf("") }
    var moodPrompt by remember { mutableStateOf("Moody Kodak cinema with crisp voiceover clarity") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 0. Generative Media Suite: Text->Video, Animate Photo, Create/Edit Image, Lyria Music
        AiGenerativeStudioPanel(
            state = state,
            viewModel = viewModel
        )

        // Active AI Status Banner
        when (val ai = state.aiTaskState) {
            is AiTaskState.Loading -> {
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
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(ai.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }

            is AiTaskState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ai.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissAiState() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Dismiss AI error")
                        }
                    }
                }
            }

            is AiTaskState.QuickCutReady -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "AI Quick-Cut Plan Ready (${ai.plan.cuts.size} cuts)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(ai.plan.summary, style = MaterialTheme.typography.bodySmall)
                        ai.plan.cuts.take(4).forEachIndexed { idx, cut ->
                            Text(
                                text = "• Cut ${idx + 1}: ${cut.trimStartMs}ms–${cut.trimEndMs}ms (${cut.speed}x, ${cut.transition.label}) — ${cut.reason}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.applyAiQuickCutPlan(ai.plan) },
                                modifier = Modifier.weight(1f).testTag("apply_ai_cuts_button")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply Cuts to Timeline")
                            }
                            OutlinedButton(onClick = { viewModel.dismissAiState() }) { Text("Dismiss") }
                        }
                    }
                }
            }

            is AiTaskState.CaptionsReady -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Synced ${ai.count} AI Captions to Track T1",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(ai.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            is AiTaskState.ColorMixReady -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "AI Look: ${ai.suggestion.colorPreset.label} + Audio Mix",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(ai.suggestion.summary, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { viewModel.applyAiColorAndMix(ai.suggestion) },
                            modifier = Modifier.fillMaxWidth().testTag("apply_ai_color_mix_button")
                        ) {
                            Text("Apply Grade & Audio Mix")
                        }
                    }
                }
            }

            AiTaskState.Idle -> Unit
        }

        // 1. AI Quick Cuts Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.ContentCut, contentDescription = null, tint = CinemaCoral)
                    Text("AI Quick Cuts & Silence Removal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Viral Fast-Cut", "Cinematic Flow", "Podcast Jump-Cut", "Highlight Reel").forEach { style ->
                        FilterChip(
                            selected = selectedPacing == style,
                            onClick = { selectedPacing = style },
                            label = { Text(style) }
                        )
                    }
                }
                OutlinedTextField(
                    value = cutBrief,
                    onValueChange = { cutBrief = it.take(180) },
                    label = { Text("Optional pacing notes (e.g. tighten intro, energetic beat cuts)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.runAiQuickCuts(selectedPacing, cutBrief) },
                    enabled = state.aiTaskState !is AiTaskState.Loading,
                    modifier = Modifier.fillMaxWidth().testTag("run_ai_quick_cuts_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate AI Quick Cuts")
                }
            }
        }

        // 2. AI Automated Captioning Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null, tint = CaptionViolet)
                    Text("AI Automated Captioning", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("English", "Spanish", "French", "Japanese", "Hindi").forEach { lang ->
                        FilterChip(
                            selected = captionLanguage == lang,
                            onClick = { captionLanguage = lang },
                            label = { Text(lang) }
                        )
                    }
                }
                OutlinedTextField(
                    value = captionContext,
                    onValueChange = { captionContext = it.take(240) },
                    label = { Text("Voiceover script or scene topic (auto-transcribes imported audio)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FilledTonalButton(
                    onClick = { viewModel.runAiAutomatedCaptioning(captionLanguage, captionContext) },
                    enabled = state.aiTaskState !is AiTaskState.Loading,
                    modifier = Modifier.fillMaxWidth().testTag("run_ai_captions_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Generate Synced Captions")
                }
            }
        }

        // 3. AI Smart Color & Audio Mix Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.ColorLens, contentDescription = null, tint = PlayheadCyan)
                    Text("AI Smart Color Grade & Mix", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = moodPrompt,
                    onValueChange = { moodPrompt = it.take(180) },
                    label = { Text("Describe look & audio mood") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { viewModel.runAiColorAndMix(moodPrompt) },
                    enabled = state.aiTaskState !is AiTaskState.Loading,
                    modifier = Modifier.fillMaxWidth().testTag("run_ai_color_mix_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Suggest LUT & Audio Balance")
                }
            }
        }
    }
}

@Composable
fun ColorGradingPanel(
    project: VideoProject,
    viewModel: StudioViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Real-Time Cinema LUT Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { viewModel.applyColorPreset(ColorPreset.NATURAL) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset", style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorPreset.entries.forEach { preset ->
                val selected = project.colorPreset == preset.raw
                val swatchColor = when (preset) {
                    ColorPreset.NATURAL -> Color(0xFF64748B)
                    ColorPreset.KODAK_2383 -> Color(0xFFD97706)
                    ColorPreset.TEAL_ORANGE -> Color(0xFF0891B2)
                    ColorPreset.FUJI_ETERNA -> Color(0xFF10B981)
                    ColorPreset.NOIR_400 -> Color(0xFF334155)
                    ColorPreset.CYBER_NEON -> Color(0xFF8B5CF6)
                    ColorPreset.GOLDEN_HOUR -> Color(0xFFF59E0B)
                    ColorPreset.FLAT_LOG -> Color(0xFF94A3B8)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) CinemaCoral.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer)
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) CinemaCoral else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { viewModel.applyColorPreset(preset) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("lut_preset_${preset.raw.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(swatchColor)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(preset.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        GradeSliderRow("Exposure", project.exposure.toFloat(), -2f..2f, "%+.2f") {
            viewModel.updateColorGrading(exposure = it.toDouble())
        }
        GradeSliderRow("Contrast", project.contrast.toFloat(), 0.5f..2.0f, "%.2fx") {
            viewModel.updateColorGrading(contrast = it.toDouble())
        }
        GradeSliderRow("Saturation", project.saturation.toFloat(), 0f..2.0f, "%.2fx") {
            viewModel.updateColorGrading(saturation = it.toDouble())
        }
        GradeSliderRow("Temperature", project.temperature.toFloat(), -1f..1f, "%+.2f") {
            viewModel.updateColorGrading(temperature = it.toDouble())
        }
        GradeSliderRow("Tint", project.tint.toFloat(), -1f..1f, "%+.2f") {
            viewModel.updateColorGrading(tint = it.toDouble())
        }
        GradeSliderRow("Vignette", project.vignette.toFloat(), 0f..1f, "%.2f") {
            viewModel.updateColorGrading(vignette = it.toDouble())
        }
    }
}

@Composable
private fun GradeSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(format.format(value), style = MonoBadgeStyle, color = PlayheadCyan)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
fun AudioMixerPanel(
    project: VideoProject,
    state: EditorUiState,
    viewModel: StudioViewModel,
    onImportAudioTrack: () -> Unit,
    onOpenAiMusic: () -> Unit
) {
    val tracks = remember(project.audioTracks) { project.typedAudioTracks() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenAiMusic,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("audio_mixer_generate_music_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("AI Music")
                }
                FilledTonalButton(
                    onClick = onImportAudioTrack,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("import_audio_button")
                ) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Audio")
                }
                OutlinedButton(
                    onClick = { viewModel.addStudioAudioPreset(StudioAudioCatalog.sfxPresets.first(), atPlayhead = true) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("mixer_add_sfx_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("SFX")
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Auto-Duck", style = MaterialTheme.typography.labelMedium)
                Switch(
                    checked = state.autoDuckMusic,
                    onCheckedChange = { viewModel.toggleAutoDuckMusic(it) }
                )
            }
        }

        // Quick-Add Studio Background Music & Sound Effects Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StudioAudioCatalog.musicPresets.take(2).forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.addStudioAudioPreset(preset, atPlayhead = false) },
                    label = { Text("+ ${preset.name}", style = MaterialTheme.typography.labelSmall) }
                )
            }
            StudioAudioCatalog.sfxPresets.take(4).forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.addStudioAudioPreset(preset, atPlayhead = true) },
                    label = { Text("FX: ${preset.name}", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        tracks.forEach { track ->
            val role = AudioRole.fromRaw(track.role)
            val accent = when (role) {
                AudioRole.ORIGINAL -> PlayheadCyan
                AudioRole.VOICEOVER -> AudioAmber
                AudioRole.MUSIC -> AudioEmerald
                AudioRole.SFX -> CaptionViolet
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Column {
                                Text(track.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(role.label, style = MaterialTheme.typography.labelSmall, color = accent)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            MixerToggleChip("M", track.muted, CinemaCoral) {
                                viewModel.updateAudioTrack(track.trackId) { it.copy(muted = !it.muted) }
                            }
                            MixerToggleChip("S", track.solo, AudioAmber) {
                                viewModel.updateAudioTrack(track.trackId) { it.copy(solo = !it.solo) }
                            }
                            if (tracks.size > 1) {
                                IconButton(
                                    onClick = { viewModel.deleteAudioTrack(track.trackId) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove track", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Gain ${(track.volume * 100).toInt()}%", style = MonoBadgeStyle, color = accent, modifier = Modifier.width(84.dp))
                        Slider(
                            value = track.volume.toFloat().coerceIn(0f, 2f),
                            onValueChange = { v -> viewModel.updateAudioTrack(track.trackId) { it.copy(volume = v.toDouble()) } },
                            valueRange = 0f..2f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val panLabel = when {
                            track.pan < -0.15 -> "Pan L${(-track.pan * 100).toInt()}"
                            track.pan > 0.15 -> "Pan R${(track.pan * 100).toInt()}"
                            else -> "Pan Center"
                        }
                        Text(panLabel, style = MonoBadgeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(84.dp))
                        Slider(
                            value = track.pan.toFloat().coerceIn(-1f, 1f),
                            onValueChange = { p -> viewModel.updateAudioTrack(track.trackId) { it.copy(pan = p.toDouble()) } },
                            valueRange = -1f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MixerToggleChip(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Surface(
        color = if (active) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick)
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

@Composable
fun CaptionsPanel(
    project: VideoProject,
    viewModel: StudioViewModel
) {
    val context = LocalContext.current
    val captions = remember(project.captions) { project.typedCaptions() }
    var newText by remember { mutableStateOf("") }
    var newEmphasis by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Caption Style", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (captions.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val srtUri = viewModel.createSrtShareUri()
                        if (srtUri != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, srtUri)
                                putExtra(Intent.EXTRA_SUBJECT, "${project.title} Subtitles (.srt)")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share .SRT Captions"))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export .SRT", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CaptionStyle.entries.forEach { style ->
                FilterChip(
                    selected = project.captionStyle == style.raw,
                    onClick = { viewModel.setCaptionStyle(style) },
                    label = { Text(style.label) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it.take(180) },
                label = { Text("Add caption at playhead") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newText.isNotBlank()) {
                        viewModel.addCaptionAtPlayhead(newText, newEmphasis)
                        newText = ""
                        newEmphasis = ""
                    }
                },
                modifier = Modifier.testTag("add_caption_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add caption")
            }
        }

        captions.forEach { cue ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { viewModel.seekTo(cue.startMs) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${formatTimecode(cue.startMs)} → ${formatTimecode(cue.endMs)}",
                            style = MonoBadgeStyle,
                            color = CaptionViolet
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(cue.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { viewModel.deleteCaption(cue.captionId) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete caption")
                    }
                }
            }
        }
    }
}

@Composable
fun ExportBackupPanel(
    project: VideoProject,
    state: EditorUiState,
    batterySaverEnabled: Boolean,
    viewModel: StudioViewModel,
    onOpenRenderSheet: () -> Unit,
    onExportOfflineJson: (String) -> Unit
) {
    val context = LocalContext.current
    var resolution by remember(project.resolution) { mutableStateOf(ResolutionOption.fromRaw(project.resolution)) }
    var fps by remember(project.frameRate) { mutableStateOf(project.frameRate) }
    var format by remember { mutableStateOf(ExportFormatOption.MP4) }
    var codec by remember { mutableStateOf(CodecOption.HEVC_H265) }
    var destination by remember { mutableStateOf(ExportDestination.DEVICE_GALLERY) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 4K Render Spec Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.HighQuality, contentDescription = null, tint = PlayheadCyan)
                        Text("Render & Social Export", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.BatterySaver, contentDescription = null, tint = AudioEmerald, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${state.batteryTelemetry.levelPercent}% • ~${state.batteryTelemetry.estimatedRenderDrainPct}% est.",
                            style = MonoBadgeStyle,
                            color = AudioEmerald
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        ResolutionOption.HD_720P,
                        ResolutionOption.FHD_1080P,
                        ResolutionOption.UHD_4K
                    ).forEach { res ->
                        FilterChip(
                            selected = resolution == res,
                            onClick = {
                                resolution = res
                                viewModel.setRenderSpec(res, fps)
                            },
                            label = { Text(res.label) },
                            modifier = Modifier.testTag("export_tab_resolution_${res.raw.lowercase()}")
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30L, 60L).forEach { rate ->
                        FilterChip(
                            selected = fps == rate,
                            onClick = {
                                fps = rate
                                viewModel.setRenderSpec(resolution, rate)
                            },
                            label = { Text("${rate}fps") }
                        )
                    }
                    ExportFormatOption.entries.forEach { fmt ->
                        FilterChip(
                            selected = format == fmt,
                            onClick = { format = fmt },
                            label = { Text(fmt.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CodecOption.entries.forEach { c ->
                        FilterChip(
                            selected = codec == c,
                            onClick = { codec = c },
                            label = { Text(c.label) }
                        )
                    }
                }

                Text("Destination / Social Preset", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExportDestination.entries.forEach { dest ->
                        FilterChip(
                            selected = destination == dest,
                            onClick = { destination = dest },
                            label = { Text(dest.label) }
                        )
                    }
                }

                val progress = state.exportState
                if (state.isExporting || progress != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { progress?.progressFraction ?: 0.1f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = progress?.stageLabel ?: "Initializing render pipeline...",
                            style = MaterialTheme.typography.bodySmall,
                            color = PlayheadCyan
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenRenderSheet,
                        enabled = !state.isExporting,
                        modifier = Modifier.weight(1f).testTag("start_export_button")
                    ) {
                        Text(if (state.isExporting) "Rendering..." else "Configure & Render (${resolution.raw} ${fps}fps ${format.label})")
                    }

                    val shareUri: Uri? = progress?.completedShareUri
                    if (shareUri != null) {
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = progress.shareMimeType
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Edited '${project.title}' in ${resolution.raw} ${fps}fps with FrameCut"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share to ${destination.label}"))
                            },
                            modifier = Modifier.testTag("share_exported_video_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }

        // Offline Local Backup Vault Actions
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Offline Backup Protection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "Save an instant local SQLite Room snapshot or export a portable .fcproj.json file to internal storage, microSD, or USB-C SSD.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.createManualOfflineBackup("Manual Vault Snapshot") },
                        modifier = Modifier.weight(1f).testTag("save_room_backup_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Local Snapshot")
                    }
                    OutlinedButton(
                        onClick = { onExportOfflineJson(project.toBackupJson()) },
                        modifier = Modifier.weight(1f).testTag("export_fcproj_button")
                    ) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save .fcproj")
                    }
                }
            }
        }
    }
}
