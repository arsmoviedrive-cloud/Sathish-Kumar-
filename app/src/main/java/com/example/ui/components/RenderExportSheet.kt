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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CodecOption
import com.example.data.model.ExportDestination
import com.example.data.model.ExportFormatOption
import com.example.data.model.ResolutionOption
import com.example.data.model.VideoProject
import com.example.data.model.formatDurationShort
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CinemaCoral
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.PlayheadCyan
import com.example.ui.viewmodel.EditorUiState
import com.example.ui.viewmodel.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderExportSheet(
    project: VideoProject,
    state: EditorUiState,
    batterySaverEnabled: Boolean,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialRes = remember(project.resolution) {
        ResolutionOption.fromRaw(project.resolution)
    }
    val initialFps = remember(project.frameRate) {
        if (project.frameRate >= 60L) 60L else 30L
    }

    var selectedResolution by remember { mutableStateOf(initialRes) }
    var selectedFps by remember { mutableLongStateOf(initialFps) }
    var selectedFormat by remember { mutableStateOf(ExportFormatOption.MP4) }
    var selectedCodec by remember { mutableStateOf(CodecOption.HEVC_H265) }
    var selectedDestination by remember { mutableStateOf(ExportDestination.DEVICE_GALLERY) }

    val estimatedSizeMb = remember(
        project.totalDurationMs,
        selectedResolution,
        selectedCodec,
        selectedFps,
        selectedFormat
    ) {
        viewModel.estimateExportSizeMb(
            resolution = selectedResolution,
            codec = selectedCodec,
            fps = selectedFps,
            format = selectedFormat
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier.testTag("render_export_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Final Render Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${project.title} • ${formatDurationShort(project.totalDurationMs)} • ${project.aspectRatio}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_render_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close render settings"
                    )
                }
            }

            // Live Spec & Telemetry Summary Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                PlayheadCyan.copy(alpha = 0.14f),
                                CinemaCoral.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .border(1.dp, PlayheadCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "OUTPUT SPECIFICATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlayheadCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selectedResolution.raw} • ${selectedFps}fps • ${selectedFormat.label} • ~${"%.1f".format(estimatedSizeMb)} MB",
                            style = MonoBadgeStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = AudioEmerald.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BatterySaver,
                                contentDescription = null,
                                tint = AudioEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${state.batteryTelemetry.levelPercent}% (~${state.batteryTelemetry.estimatedRenderDrainPct}%)",
                                style = MonoBadgeStyle,
                                color = AudioEmerald
                            )
                        }
                    }
                }
            }

            // 1. Resolution (720p vs 1080p vs 4K)
            RenderSectionHeader(
                icon = Icons.Filled.HighQuality,
                title = "Resolution",
                accent = PlayheadCyan
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RenderOptionCard(
                    title = "720p",
                    subtitle = "Web & Compact",
                    badge = "HD",
                    selected = selectedResolution == ResolutionOption.HD_720P,
                    accent = PlayheadCyan,
                    onClick = {
                        selectedResolution = ResolutionOption.HD_720P
                        viewModel.setRenderSpec(ResolutionOption.HD_720P, selectedFps)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sheet_resolution_720p")
                )
                RenderOptionCard(
                    title = "1080p",
                    subtitle = "Full HD • Fast",
                    badge = "FHD",
                    selected = selectedResolution == ResolutionOption.FHD_1080P,
                    accent = PlayheadCyan,
                    onClick = {
                        selectedResolution = ResolutionOption.FHD_1080P
                        viewModel.setRenderSpec(ResolutionOption.FHD_1080P, selectedFps)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sheet_resolution_1080p")
                )
                RenderOptionCard(
                    title = "4K",
                    subtitle = "Cinema Master",
                    badge = "UHD",
                    selected = selectedResolution == ResolutionOption.UHD_4K,
                    accent = PlayheadCyan,
                    onClick = {
                        selectedResolution = ResolutionOption.UHD_4K
                        viewModel.setRenderSpec(ResolutionOption.UHD_4K, selectedFps)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sheet_resolution_4k")
                )
            }

            // 2. Frame Rate (30fps vs 60fps)
            RenderSectionHeader(
                icon = Icons.Filled.Speed,
                title = "Frame Rate",
                accent = CinemaCoral
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RenderOptionCard(
                    title = "30 fps",
                    subtitle = if (batterySaverEnabled) "Battery Saver Friendly" else "Standard Cinema Look",
                    badge = "BALANCED",
                    selected = selectedFps == 30L,
                    accent = CinemaCoral,
                    onClick = {
                        selectedFps = 30L
                        viewModel.setRenderSpec(selectedResolution, 30L)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sheet_fps_30")
                )
                RenderOptionCard(
                    title = "60 fps",
                    subtitle = "Fluid Motion & Action",
                    badge = "SMOOTH",
                    selected = selectedFps == 60L,
                    accent = CinemaCoral,
                    onClick = {
                        selectedFps = 60L
                        viewModel.setRenderSpec(selectedResolution, 60L)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sheet_fps_60")
                )
            }

            // 3. Container Format (MP4 vs MOV)
            RenderSectionHeader(
                icon = Icons.Filled.Movie,
                title = "Video Format",
                accent = AudioEmerald
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExportFormatOption.entries.forEach { fmt ->
                    RenderOptionCard(
                        title = fmt.label,
                        subtitle = fmt.subtitle,
                        badge = ".${fmt.extension.uppercase()}",
                        selected = selectedFormat == fmt,
                        accent = AudioEmerald,
                        onClick = { selectedFormat = fmt },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sheet_format_${fmt.raw.lowercase()}")
                    )
                }
            }

            // Secondary Codec & Social Destination Controls
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Codec & Destination",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CodecOption.entries.forEach { codecOption ->
                        FilterChip(
                            selected = selectedCodec == codecOption,
                            onClick = { selectedCodec = codecOption },
                            label = { Text(codecOption.label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExportDestination.entries.forEach { dest ->
                        FilterChip(
                            selected = selectedDestination == dest,
                            onClick = { selectedDestination = dest },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }

            // Live Render Progress inside the sheet
            val progress = state.exportState
            if (state.isExporting || progress != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = progress?.stageLabel ?: "Initializing render pipeline...",
                                style = MaterialTheme.typography.bodySmall,
                                color = PlayheadCyan,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${((progress?.progressFraction ?: 0.05f) * 100).toInt()}%",
                                style = MonoBadgeStyle,
                                color = PlayheadCyan
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress?.progressFraction ?: 0.08f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            // Primary Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        viewModel.startExport(
                            resolution = selectedResolution,
                            codec = selectedCodec,
                            fps = selectedFps,
                            destination = selectedDestination,
                            format = selectedFormat
                        )
                    },
                    enabled = !state.isExporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("sheet_confirm_render_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isExporting) {
                            "Rendering ${selectedFormat.label}..."
                        } else {
                            "Render ${selectedResolution.raw} ${selectedFps}fps ${selectedFormat.label}"
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val shareUri: Uri? = progress?.completedShareUri
                if (shareUri != null && !state.isExporting) {
                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = progress.shareMimeType
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Rendered '${project.title}' in ${selectedResolution.raw} ${selectedFps}fps (${selectedFormat.label}) with FrameCut"
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share ${selectedFormat.label}"))
                        },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("sheet_share_render_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share rendered video",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderSectionHeader(
    icon: ImageVector,
    title: String,
    accent: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RenderOptionCard(
    title: String,
    subtitle: String,
    badge: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        style = MonoBadgeStyle,
                        color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
