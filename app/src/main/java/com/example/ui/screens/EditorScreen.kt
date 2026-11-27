package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AspectRatioOption
import com.example.data.model.AudioRole
import com.example.ui.components.AiStudioPanel
import com.example.ui.components.AudioMixerPanel
import com.example.ui.components.CaptionsPanel
import com.example.ui.components.ColorGradingPanel
import com.example.ui.components.ExportBackupPanel
import com.example.ui.components.TimelineCanvas
import com.example.ui.components.TimelineToolsPanel
import com.example.ui.components.VideoPreviewStage
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.PlayheadCyan
import com.example.ui.viewmodel.EditorToolTab
import com.example.ui.viewmodel.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: StudioViewModel,
    onNavigateBack: () -> Unit,
    onOpenCameraForClip: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.editorState.collectAsStateWithLifecycle()
    val settings by viewModel.localSettings.collectAsStateWithLifecycle()
    val project = state.project

    BackHandler {
        viewModel.stopPlayback()
        onNavigateBack()
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingOfflineJson by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    val clipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.importVideoClipToTimeline(uri)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Audio Track"
            viewModel.addAudioTrack(fileName, AudioRole.MUSIC, uri.toString())
        }
    }

    val offlineBackupCreator = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingOfflineJson
        pendingOfflineJson = null
        if (uri != null && json != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }.onSuccess {
                viewModel.showMessage("Saved portable .fcproj.json backup.")
            }.onFailure {
                viewModel.showMessage("Could not save .fcproj.json file.")
            }
        }
    }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onNavigateBack) { Text("Return to Projects") }
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopPlayback()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to projects")
                    }
                },
                title = {
                    Column(modifier = Modifier.clickable { showRenameDialog = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = project.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Rename project",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = state.syncStatusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isSyncing) PlayheadCyan else AudioEmerald
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = state.canUndo,
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = state.canRedo,
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(
                        onClick = { viewModel.toggleBatterySaver(!settings.batterySaverEnabled) },
                        modifier = Modifier.testTag("editor_battery_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BatterySaver,
                            contentDescription = "Toggle Battery Saver",
                            tint = if (settings.batterySaverEnabled) AudioEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.forceCloudSyncNow() },
                        modifier = Modifier.testTag("force_cloud_sync_button")
                    ) {
                        Icon(
                            imageVector = if (state.isSyncing) Icons.Filled.CloudSync else Icons.Filled.CloudDone,
                            contentDescription = "Sync project to cloud",
                            tint = if (state.isSyncing) PlayheadCyan else AudioEmerald
                        )
                    }
                }
            )
        },
        bottomBar = {
            EditorToolDeckBar(
                activeTab = state.activeToolTab,
                onSelectTab = { viewModel.selectToolTab(it) }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpanded = maxWidth >= 760.dp

            if (isExpanded) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1.15f).fillMaxHeight()) {
                        VideoPreviewStage(
                            project = project,
                            state = state,
                            batterySaverEnabled = settings.batterySaverEnabled,
                            onTogglePlay = { viewModel.togglePlayback() },
                            onStepFrame = { viewModel.stepFrame(it) },
                            onCycleAspectRatio = {
                                val next = AspectRatioOption.entries[
                                    (AspectRatioOption.fromRaw(project.aspectRatio).ordinal + 1) % AspectRatioOption.entries.size
                                ]
                                viewModel.setAspectRatio(next)
                            },
                            onCompareToggle = { viewModel.setCompareOriginalColor(it) }
                        )
                        TimelineCanvas(
                            project = project,
                            playheadMs = state.playheadMs,
                            zoomDpPerSec = state.timelineZoomDpPerSec,
                            selectedClipId = state.selectedClipId,
                            clipThumbnails = state.clipThumbnails,
                            onSeek = { viewModel.seekTo(it) },
                            onZoomChange = { viewModel.updateTimelineZoom(it) },
                            onSelectClip = { viewModel.selectClip(it) },
                            onTrimSelectedClip = { dStart, dEnd -> viewModel.trimSelectedClip(dStart, dEnd) },
                            onSelectTrack = {
                                viewModel.selectAudioTrack(it)
                                viewModel.selectToolTab(EditorToolTab.AUDIO)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        ActiveToolPanelHost(
                            activeTab = state.activeToolTab,
                            project = project,
                            state = state,
                            batterySaverEnabled = settings.batterySaverEnabled,
                            viewModel = viewModel,
                            onRecordClip = {
                                viewModel.stopPlayback()
                                onOpenCameraForClip()
                            },
                            onImportClip = {
                                clipPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            onImportAudio = { audioPicker.launch(arrayOf("audio/*")) },
                            onExportOfflineJson = { json ->
                                pendingOfflineJson = json
                                val safe = project.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(28)
                                offlineBackupCreator.launch("${safe}.fcproj.json")
                            }
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    VideoPreviewStage(
                        project = project,
                        state = state,
                        batterySaverEnabled = settings.batterySaverEnabled,
                        onTogglePlay = { viewModel.togglePlayback() },
                        onStepFrame = { viewModel.stepFrame(it) },
                        onCycleAspectRatio = {
                            val next = AspectRatioOption.entries[
                                (AspectRatioOption.fromRaw(project.aspectRatio).ordinal + 1) % AspectRatioOption.entries.size
                            ]
                            viewModel.setAspectRatio(next)
                        },
                        onCompareToggle = { viewModel.setCompareOriginalColor(it) }
                    )

                    TimelineCanvas(
                        project = project,
                        playheadMs = state.playheadMs,
                        zoomDpPerSec = state.timelineZoomDpPerSec,
                        selectedClipId = state.selectedClipId,
                        clipThumbnails = state.clipThumbnails,
                        onSeek = { viewModel.seekTo(it) },
                        onZoomChange = { viewModel.updateTimelineZoom(it) },
                        onSelectClip = { viewModel.selectClip(it) },
                        onTrimSelectedClip = { dStart, dEnd -> viewModel.trimSelectedClip(dStart, dEnd) },
                        onSelectTrack = {
                            viewModel.selectAudioTrack(it)
                            viewModel.selectToolTab(EditorToolTab.AUDIO)
                        }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        ActiveToolPanelHost(
                            activeTab = state.activeToolTab,
                            project = project,
                            state = state,
                            batterySaverEnabled = settings.batterySaverEnabled,
                            viewModel = viewModel,
                            onRecordClip = {
                                viewModel.stopPlayback()
                                onOpenCameraForClip()
                            },
                            onImportClip = {
                                clipPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            onImportAudio = { audioPicker.launch(arrayOf("audio/*")) },
                            onExportOfflineJson = { json ->
                                pendingOfflineJson = json
                                val safe = project.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(28)
                                offlineBackupCreator.launch("${safe}.fcproj.json")
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var titleDraft by remember(project.title) { mutableStateOf(project.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { if (it.length <= 100) titleDraft = it },
                    singleLine = true,
                    label = { Text("Project Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameProject(titleDraft)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActiveToolPanelHost(
    activeTab: EditorToolTab,
    project: com.example.data.model.VideoProject,
    state: com.example.ui.viewmodel.EditorUiState,
    batterySaverEnabled: Boolean,
    viewModel: StudioViewModel,
    onRecordClip: () -> Unit,
    onImportClip: () -> Unit,
    onImportAudio: () -> Unit,
    onExportOfflineJson: (String) -> Unit
) {
    when (activeTab) {
        EditorToolTab.TIMELINE -> TimelineToolsPanel(project, state, viewModel, onRecordClip, onImportClip)
        EditorToolTab.AI_STUDIO -> AiStudioPanel(project, state, viewModel)
        EditorToolTab.COLOR -> ColorGradingPanel(project, viewModel)
        EditorToolTab.AUDIO -> AudioMixerPanel(project, state, viewModel, onImportAudio)
        EditorToolTab.CAPTIONS -> CaptionsPanel(project, viewModel)
        EditorToolTab.EXPORT -> ExportBackupPanel(project, state, batterySaverEnabled, viewModel, onExportOfflineJson)
    }
}

@Composable
private fun EditorToolDeckBar(
    activeTab: EditorToolTab,
    onSelectTab: (EditorToolTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EditorToolTab.entries.forEach { tab ->
                val selected = tab == activeTab
                val icon = when (tab) {
                    EditorToolTab.TIMELINE -> Icons.Filled.ViewTimeline
                    EditorToolTab.AI_STUDIO -> Icons.Filled.AutoAwesome
                    EditorToolTab.COLOR -> Icons.Filled.ColorLens
                    EditorToolTab.AUDIO -> Icons.Filled.GraphicEq
                    EditorToolTab.CAPTIONS -> Icons.Filled.Subtitles
                    EditorToolTab.EXPORT -> Icons.Filled.HighQuality
                }
                val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("editor_tab_${tab.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(18.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = tint
                    )
                }
            }
        }
    }
}
