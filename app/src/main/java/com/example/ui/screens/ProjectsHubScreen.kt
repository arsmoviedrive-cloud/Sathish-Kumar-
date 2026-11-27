package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ProjectBackupEntity
import com.example.data.model.AspectRatioOption
import com.example.data.model.ColorPreset
import com.example.data.model.ExportDestination
import com.example.data.model.ExportRecord
import com.example.data.model.VideoProject
import com.example.data.model.formatDurationShort
import com.example.firebase.signOut
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CaptionViolet
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.PlayheadCyan
import com.example.ui.theme.StudioThemeMode
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HubSection(val label: String) {
    PROJECTS("Projects"),
    OFFLINE_VAULT("Offline Vault"),
    EXPORTS("Exports")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsHubScreen(
    viewModel: StudioViewModel,
    userEmail: String,
    onOpenEditor: (String) -> Unit,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val exportsState by viewModel.exportsState.collectAsStateWithLifecycle()
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
    val settings by viewModel.localSettings.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()

    var selectedSection by remember { mutableStateOf(HubSection.PROJECTS) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var pendingBackupExportJson by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(editorState.toastMessage) {
        val msg = editorState.toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // Zero-permission Android Photo Picker for Video import (Google Play compliant)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.createProjectFromVideoUri(uri, onOpenEditor)
        }
    }

    // SAF CreateDocument for portable .fcproj.json offline backup to internal storage / SD / USB-C
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val payload = pendingBackupExportJson
        pendingBackupExportJson = null
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                viewModel.showMessage("Exported offline .fcproj.json backup file.")
            }.onFailure {
                viewModel.showMessage("Failed to write backup file.")
            }
        }
    }

    // SAF OpenDocument to restore a portable .fcproj.json backup file
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { text ->
                if (!text.isNullOrBlank()) {
                    viewModel.importBackupJsonString(text, onOpenEditor)
                }
            }.onFailure {
                viewModel.showMessage("Could not read selected backup file.")
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "FrameCut",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = PlayheadCyan.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "4K AI",
                                        style = MonoBadgeStyle,
                                        color = PlayheadCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = userEmail.ifBlank { "Cloud Sync Active" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        // Battery Saver quick toggle
                        IconButton(
                            onClick = { viewModel.toggleBatterySaver(!settings.batterySaverEnabled) },
                            modifier = Modifier.testTag("battery_saver_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BatterySaver,
                                contentDescription = "Toggle Battery Saver Mode",
                                tint = if (settings.batterySaverEnabled) AudioEmerald
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Cycle OLED Dark -> Studio Dark -> Daylight
                        IconButton(
                            onClick = {
                                val next = when (settings.themeMode) {
                                    StudioThemeMode.OLED_DARK -> StudioThemeMode.STUDIO_DARK
                                    StudioThemeMode.STUDIO_DARK -> StudioThemeMode.DAYLIGHT
                                    StudioThemeMode.DAYLIGHT -> StudioThemeMode.OLED_DARK
                                }
                                viewModel.setThemeMode(next)
                                viewModel.showMessage("Theme: ${next.label}")
                            },
                            modifier = Modifier.testTag("theme_mode_toggle")
                        ) {
                            Icon(
                                imageVector = if (settings.themeMode == StudioThemeMode.DAYLIGHT)
                                    Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Switch Theme Mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Sign Out
                        IconButton(
                            onClick = { signOut(credentialManager, scope) },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        HubSection.entries.forEach { section ->
                            val selected = selectedSection == section
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedSection = section },
                                icon = {
                                    Icon(
                                        imageVector = when (section) {
                                            HubSection.PROJECTS -> if (selected) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary
                                            HubSection.OFFLINE_VAULT -> if (selected) Icons.Filled.FolderZip else Icons.Outlined.FolderZip
                                            HubSection.EXPORTS -> if (selected) Icons.Filled.HighQuality else Icons.Outlined.HighQuality
                                        },
                                        contentDescription = section.label
                                    )
                                },
                                label = { Text(section.label) },
                                modifier = Modifier.testTag("hub_tab_${section.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (selectedSection == HubSection.PROJECTS) {
                    FloatingActionButton(
                        onClick = { showNewProjectDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("create_project_fab")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New video project")
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        HubSection.entries.forEach { section ->
                            val selected = selectedSection == section
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedSection = section },
                                icon = {
                                    Icon(
                                        imageVector = when (section) {
                                            HubSection.PROJECTS -> if (selected) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary
                                            HubSection.OFFLINE_VAULT -> if (selected) Icons.Filled.FolderZip else Icons.Outlined.FolderZip
                                            HubSection.EXPORTS -> if (selected) Icons.Filled.HighQuality else Icons.Outlined.HighQuality
                                        },
                                        contentDescription = section.label
                                    )
                                },
                                label = { Text(section.label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (selectedSection) {
                        HubSection.PROJECTS -> ProjectsTabContent(
                            projectsState = projectsState,
                            batterySaver = settings.batterySaverEnabled,
                            batteryPercent = editorState.batteryTelemetry.levelPercent,
                            themeLabel = settings.themeMode.label,
                            onOpenCamera = onOpenCamera,
                            onImportVideo = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            onCreateDemoReel = { viewModel.createDemoProjectAndOpen(onOpenEditor) },
                            onOpenProject = { project ->
                                viewModel.openProject(project)
                                onOpenEditor(project.id)
                            },
                            onDeleteProject = { viewModel.deleteProject(it) }
                        )

                        HubSection.OFFLINE_VAULT -> OfflineVaultTabContent(
                            backups = localBackups,
                            onRestoreBackup = { backup ->
                                viewModel.restoreBackupAsCloudProject(backup, onOpenEditor)
                            },
                            onExportBackupFile = { backup ->
                                pendingBackupExportJson = backup.payloadJson
                                val safeName = backup.projectTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(28)
                                exportBackupLauncher.launch("${safeName}_backup.fcproj.json")
                            },
                            onImportBackupFile = {
                                importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            onDeleteBackup = { viewModel.deleteOfflineBackup(it) }
                        )

                        HubSection.EXPORTS -> ExportsHistoryTabContent(
                            exportsState = exportsState,
                            onShareExport = { record ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (record.localOutputUri.contains("video")) "video/mp4" else "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "${record.projectTitle} (${record.resolution} ${record.frameRate}fps)")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Exported '${record.projectTitle}' in ${record.resolution} ${record.frameRate}fps (${record.codec}) via FrameCut."
                                    )
                                    if (record.localOutputUri.startsWith("content://")) {
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(record.localOutputUri))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share ${record.projectTitle}"))
                            },
                            onDeleteExport = { viewModel.deleteExportRecord(it) }
                        )
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { title, ratio ->
                showNewProjectDialog = false
                viewModel.createBlankProjectAndOpen(title, ratio, onOpenEditor)
            }
        )
    }
}

@Composable
private fun ProjectsTabContent(
    projectsState: UiState<List<VideoProject>>,
    batterySaver: Boolean,
    batteryPercent: Int,
    themeLabel: String,
    onOpenCamera: () -> Unit,
    onImportVideo: () -> Unit,
    onCreateDemoReel: () -> Unit,
    onOpenProject: (VideoProject) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            StudioQuickLaunchBanner(
                batterySaver = batterySaver,
                batteryPercent = batteryPercent,
                themeLabel = themeLabel,
                onOpenCamera = onOpenCamera,
                onImportVideo = onImportVideo,
                onCreateDemoReel = onCreateDemoReel
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cloud-Synced Projects",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = AudioEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Instant Multi-Device",
                        style = MaterialTheme.typography.labelMedium,
                        color = AudioEmerald
                    )
                }
            }
        }

        when (projectsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = projectsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val projects = projectsState.data
                if (projects.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Start your first 4K edit",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Import footage from your gallery or load our Cinema Demo Reel to test AI Quick Cuts, Auto Captions, Color LUTs & Audio Mixing.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onCreateDemoReel,
                                    modifier = Modifier.testTag("empty_state_demo_button")
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Load Demo 4K Cinema Reel")
                                }
                            }
                        }
                    }
                } else {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onOpenProject(project) },
                            onDelete = { onDeleteProject(project.id) }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun StudioQuickLaunchBanner(
    batterySaver: Boolean,
    batteryPercent: Int,
    themeLabel: String,
    onOpenCamera: () -> Unit,
    onImportVideo: () -> Unit,
    onCreateDemoReel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_sample_cinema_1795777012583),
            contentDescription = "Studio reel hero banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xEE07090E),
                            Color(0xCC0E121B),
                            Color(0x8807090E)
                        )
                    )
                )
        )
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    label = if (batterySaver) "ECO 30FPS • $batteryPercent%" else "PRO 60FPS • $batteryPercent%",
                    color = if (batterySaver) AudioEmerald else PlayheadCyan
                )
                StatusChip(label = themeLabel.uppercase(), color = CaptionViolet)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Create & Edit with AI Precision",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Record directly in 4K with CameraX for instant editing, import footage, or open a multi-track cinema reel.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenCamera,
                    modifier = Modifier.weight(1f).testTag("record_video_button")
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Record", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onImportVideo,
                    modifier = Modifier.weight(1f).testTag("import_video_button")
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCreateDemoReel,
                modifier = Modifier.fillMaxWidth().testTag("launch_demo_reel_button")
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Launch Demo 4K Cinema Reel", color = Color.White)
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: VideoProject,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val preset = ColorPreset.fromRaw(project.colorPreset)
    val clips = project.typedClips()
    val captions = project.typedCaptions()
    val tracks = project.typedAudioTracks()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("project_card_${project.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 92.dp, height = 68.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                val resId = if (clips.firstOrNull()?.sampleScene == "NEON_CITY") {
                    R.drawable.img_studio_hero_1795777011469
                } else {
                    R.drawable.img_sample_cinema_1795777012583
                }
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = project.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC07090E))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDurationShort(project.totalDurationMs),
                        style = MonoBadgeStyle,
                        color = Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0xAA07090E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Open editor",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip("${project.resolution} ${project.frameRate}fps", PlayheadCyan)
                    StatusChip(preset.label, MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${clips.size} clips • ${tracks.size} audio • ${captions.size} captions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_project_${project.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete project",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OfflineVaultTabContent(
    backups: List<ProjectBackupEntity>,
    onRestoreBackup: (ProjectBackupEntity) -> Unit,
    onExportBackupFile: (ProjectBackupEntity) -> Unit,
    onImportBackupFile: () -> Unit,
    onDeleteBackup: (Long) -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SdStorage,
                            contentDescription = null,
                            tint = AudioEmerald
                        )
                        Text(
                            text = "Recommended Offline Backup Strategy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Firestore Offline Disk Cache (Active, 100MB): Keeps recent cloud edits readable & writable without internet.\n" +
                            "2. Room Local SQLite Vault (Built-in below): Immutable point-in-time timeline snapshots stored on-device.\n" +
                            "3. Portable .fcproj.json + USB-C SSD / microSD: Export snapshots via Android Storage Access Framework to external NVMe/SD storage alongside raw 4K footage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onImportBackupFile,
                        modifier = Modifier.fillMaxWidth().testTag("import_backup_json_button")
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import .fcproj.json from Device / SD / USB-C")
                    }
                }
            }
        }

        item {
            Text(
                text = "Local Room Snapshots (${backups.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (backups.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No offline snapshots yet. Open any project and tap 'Save Offline Snapshot' in the Export tab, or create a new project with auto-backup enabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(backups, key = { it.backupId }) { backup ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = backup.projectTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${backup.storageNote} • ${formatter.format(Date(backup.createdAtMillis))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusChip(backup.resolution, AudioEmerald)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${backup.clipCount} clips • ${backup.captionCount} captions • ${formatDurationShort(backup.durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onRestoreBackup(backup) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore")
                            }
                            OutlinedButton(
                                onClick = { onExportBackupFile(backup) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save File")
                            }
                            IconButton(onClick = { onDeleteBackup(backup.backupId) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete backup")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportsHistoryTabContent(
    exportsState: UiState<List<ExportRecord>>,
    onShareExport: (ExportRecord) -> Unit,
    onDeleteExport: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Cloud Export & Social Share Log",
                style = MaterialTheme.typography.titleLarge
            )
        }

        when (exportsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Text(
                        text = exportsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is UiState.Success -> {
                val list = exportsState.data
                if (list.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No exports yet. Open a project and render in 4K HEVC or 1080p to save to your device gallery and share directly to social media.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(list, key = { it.id }) { record ->
                        val dest = ExportDestination.fromRaw(record.destination)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.projectTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StatusChip("${record.resolution} ${record.frameRate}fps", PlayheadCyan)
                                        StatusChip(dest.label, CaptionViolet)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${record.codec} • ${record.estimatedSizeMb} MB • ${formatDurationShort(record.durationMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onShareExport(record) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Share export",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onDeleteExport(record.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete export log",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, AspectRatioOption) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedRatio by remember { mutableStateOf(AspectRatioOption.WIDE_16_9) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Video Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 100) title = it },
                    label = { Text("Project Title") },
                    placeholder = { Text("e.g. Kyoto Night Walk 4K") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_title_input")
                )
                Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AspectRatioOption.entries.forEach { ratio ->
                        FilterChip(
                            selected = selectedRatio == ratio,
                            onClick = { selectedRatio = ratio },
                            label = { Text(ratio.raw) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, selectedRatio) },
                modifier = Modifier.testTag("confirm_new_project_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun StatusChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MonoBadgeStyle,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
