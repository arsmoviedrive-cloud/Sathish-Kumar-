package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalStudioSettings
import com.example.data.local.ProjectBackupEntity
import com.example.data.local.StudioPreferences
import com.example.data.model.AiColorAudioSuggestion
import com.example.data.model.AiQuickCutPlan
import com.example.data.model.AspectRatioOption
import com.example.data.model.AudioRole
import com.example.data.model.AudioTrack
import com.example.data.model.CaptionCue
import com.example.data.model.CaptionStyle
import com.example.data.model.CodecOption
import com.example.data.model.ColorPreset
import com.example.data.model.ExportDestination
import com.example.data.model.ExportRecord
import com.example.data.model.ResolutionOption
import com.example.data.model.TimelineClip
import com.example.data.model.TransitionType
import com.example.data.model.VideoProject
import com.example.data.repository.AiVideoAssistantRepository
import com.example.data.repository.ProjectRepository
import com.example.media.BatteryTelemetry
import com.example.media.ExportProgressState
import com.example.media.VideoMediaEngine
import com.example.ui.theme.StudioThemeMode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "StudioViewModel"
private const val STOP_TIMEOUT_MS = 5000L

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data class Loading(val label: String) : AiTaskState
    data class QuickCutReady(val plan: AiQuickCutPlan) : AiTaskState
    data class CaptionsReady(val summary: String, val count: Int) : AiTaskState
    data class ColorMixReady(val suggestion: AiColorAudioSuggestion) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

enum class EditorToolTab(val label: String) {
    TIMELINE("Timeline"),
    AI_STUDIO("AI Tools"),
    COLOR("Color"),
    AUDIO("Audio"),
    CAPTIONS("Captions"),
    EXPORT("Export")
}

data class EditorUiState(
    val project: VideoProject? = null,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val timelineZoomDpPerSec: Float = 44f,
    val selectedClipId: String? = null,
    val selectedTrackId: String? = null,
    val activeToolTab: EditorToolTab = EditorToolTab.TIMELINE,
    val compareOriginalColor: Boolean = false,
    val autoDuckMusic: Boolean = true,
    val syncStatusLabel: String = "Cloud Synced",
    val isSyncing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val clipThumbnails: Map<String, List<Bitmap>> = emptyMap(),
    val aiTaskState: AiTaskState = AiTaskState.Idle,
    val exportState: ExportProgressState? = null,
    val isExporting: Boolean = false,
    val batteryTelemetry: BatteryTelemetry = BatteryTelemetry(85, false, 60, 4),
    val toastMessage: String? = null
)

class StudioViewModel(
    private val projectRepository: ProjectRepository,
    private val aiRepository: AiVideoAssistantRepository,
    private val preferences: StudioPreferences,
    private val mediaEngine: VideoMediaEngine,
    val currentUserId: String,
    private val userDisplayName: String
) : ViewModel() {

    val localSettings: StateFlow<LocalStudioSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LocalStudioSettings())

    val projectsState: StateFlow<UiState<List<VideoProject>>> = projectRepository.observeProjects(currentUserId)
        .map<List<VideoProject>, UiState<List<VideoProject>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing projects", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to sync cloud projects."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val exportsState: StateFlow<UiState<List<ExportRecord>>> = projectRepository.observeExports(currentUserId)
        .map<List<ExportRecord>, UiState<List<ExportRecord>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing exports", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load export history."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val localBackups: StateFlow<List<ProjectBackupEntity>> = projectRepository.observeLocalBackups(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val undoStack = ArrayDeque<VideoProject>()
    private val redoStack = ArrayDeque<VideoProject>()
    private var playbackJob: Job? = null
    private var autoSyncJob: Job? = null

    init {
        viewModelScope.launch {
            val prefs = localSettings.value
            projectRepository.ensureUserProfile(
                displayName = userDisplayName,
                themeMode = prefs.themeMode,
                batterySaverEnabled = prefs.batterySaverEnabled,
                defaultResolution = prefs.defaultResolution
            )
            refreshBatteryTelemetry()
        }
    }

    fun clearToast() {
        _editorState.update { it.copy(toastMessage = null) }
    }

    fun showMessage(message: String) {
        _editorState.update { it.copy(toastMessage = message) }
    }

    fun setThemeMode(mode: StudioThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
            projectRepository.updateUserPreferences(themeMode = mode)
        }
    }

    fun toggleBatterySaver(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBatterySaver(enabled)
            projectRepository.updateUserPreferences(batterySaverEnabled = enabled)
            refreshBatteryTelemetry()
            showMessage(
                if (enabled) "Battery Saver ON: 30fps preview & reduced decode load"
                else "Performance Mode: 60fps preview active"
            )
        }
    }

    fun refreshBatteryTelemetry() {
        val project = _editorState.value.project
        val settings = localSettings.value
        val telemetry = mediaEngine.readBatteryTelemetry(
            batterySaverEnabled = settings.batterySaverEnabled,
            resolution = ResolutionOption.fromRaw(project?.resolution ?: settings.defaultResolution.raw),
            durationMs = project?.totalDurationMs ?: 18000L
        )
        _editorState.update { it.copy(batteryTelemetry = telemetry) }
    }

    // --- Project Lifecycle ---
    fun createDemoProjectAndOpen(onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val demo = VideoProject.createDemoCinemaProject(currentUserId)
            val result = projectRepository.createProject(demo)
            result.onSuccess { created ->
                openProject(created)
                if (localSettings.value.autoOfflineBackup) {
                    projectRepository.saveLocalOfflineBackup(currentUserId, created, "Auto-Backup on Create")
                }
                onOpened(created.id)
            }.onFailure { err ->
                showMessage("Cloud create failed: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun createBlankProjectAndOpen(title: String, aspectRatio: AspectRatioOption, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val defaultClip = TimelineClip(
                label = "Scene 01",
                startMs = 0L,
                endMs = 8000L,
                trimStartMs = 0L,
                trimEndMs = 8000L,
                sampleScene = "NEON_CITY"
            )
            val defaultAudio = AudioTrack(
                name = "Original Audio",
                role = AudioRole.ORIGINAL.raw,
                volume = 0.9,
                durationMs = 8000L
            )
            val project = VideoProject(
                userId = currentUserId,
                title = title.trim().ifBlank { "Untitled 4K Edit" }.take(100),
                aspectRatio = aspectRatio.raw,
                resolution = localSettings.value.defaultResolution.raw,
                frameRate = if (localSettings.value.batterySaverEnabled) 30L else 60L,
                cloudBackupStatus = "SYNCED"
            ).withTypedContent(listOf(defaultClip), listOf(defaultAudio), emptyList())

            projectRepository.createProject(project).onSuccess { created ->
                openProject(created)
                onOpened(created.id)
            }.onFailure { err ->
                showMessage("Could not create project: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun createProjectFromVideoUri(uri: Uri, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val meta = mediaEngine.inspectVideoUri(uri)
            val clip = TimelineClip(
                label = meta.displayName,
                sourceUri = meta.uriString,
                startMs = 0L,
                endMs = meta.durationMs,
                trimStartMs = 0L,
                trimEndMs = meta.durationMs,
                sampleScene = "IMPORTED"
            )
            val ratio = if (meta.height > meta.width) AspectRatioOption.VERTICAL_9_16 else AspectRatioOption.WIDE_16_9
            val origTrack = AudioTrack(
                name = "${meta.displayName} Audio",
                role = AudioRole.ORIGINAL.raw,
                sourceUri = meta.uriString,
                volume = 1.0,
                durationMs = meta.durationMs
            )
            val musicTrack = AudioTrack(
                name = "Ambient Music Bed",
                role = AudioRole.MUSIC.raw,
                volume = 0.35,
                fadeInMs = 500L,
                fadeOutMs = 1000L,
                durationMs = meta.durationMs
            )
            val project = VideoProject(
                userId = currentUserId,
                title = meta.displayName.take(80),
                aspectRatio = ratio.raw,
                resolution = if (meta.width >= 3840 || meta.height >= 3840) ResolutionOption.UHD_4K.raw else localSettings.value.defaultResolution.raw,
                frameRate = 60L,
                cloudBackupStatus = "SYNCED"
            ).withTypedContent(listOf(clip), listOf(origTrack, musicTrack), emptyList())

            projectRepository.createProject(project).onSuccess { created ->
                openProject(created)
                onOpened(created.id)
            }.onFailure { err ->
                showMessage("Failed to import video project: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun openProject(project: VideoProject) {
        stopPlayback()
        undoStack.clear()
        redoStack.clear()
        val clips = project.typedClips()
        val tracks = project.typedAudioTracks()
        _editorState.update {
            it.copy(
                project = project,
                playheadMs = 0L,
                isPlaying = false,
                selectedClipId = clips.firstOrNull()?.clipId,
                selectedTrackId = tracks.firstOrNull()?.trackId,
                activeToolTab = EditorToolTab.TIMELINE,
                aiTaskState = AiTaskState.Idle,
                exportState = null,
                isExporting = false,
                syncStatusLabel = "Cloud Synced",
                canUndo = false,
                canRedo = false,
                clipThumbnails = emptyMap()
            )
        }
        refreshBatteryTelemetry()
        extractThumbnailsForClips(clips)
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId).onSuccess {
                if (_editorState.value.project?.id == projectId) {
                    _editorState.update { it.copy(project = null) }
                }
                showMessage("Project deleted from cloud.")
            }.onFailure { err ->
                showMessage("Delete failed: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun deleteExportRecord(exportId: String) {
        viewModelScope.launch {
            projectRepository.deleteExport(exportId).onFailure { err ->
                showMessage("Could not remove export log: ${err.localizedMessage ?: "Error"}")
            }
        }
    }

    private fun extractThumbnailsForClips(clips: List<TimelineClip>) {
        val batterySaver = localSettings.value.batterySaverEnabled
        clips.filter { it.sourceUri.isNotBlank() }.forEach { clip ->
            viewModelScope.launch {
                val bitmaps = mediaEngine.extractKeyframes(
                    uriString = clip.sourceUri,
                    count = if (batterySaver) 3 else 5,
                    batterySaverEnabled = batterySaver
                )
                if (bitmaps.isNotEmpty()) {
                    _editorState.update { state ->
                        state.copy(clipThumbnails = state.clipThumbnails + (clip.clipId to bitmaps))
                    }
                }
            }
        }
    }

    // --- Playback & Timeline Navigation ---
    fun togglePlayback() {
        if (_editorState.value.isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val totalMs = _editorState.value.project?.totalDurationMs ?: 0L
        if (totalMs <= 0L) return
        if (_editorState.value.playheadMs >= totalMs - 60L) {
            _editorState.update { it.copy(playheadMs = 0L) }
        }
        _editorState.update { it.copy(isPlaying = true) }
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val tickMs = if (localSettings.value.batterySaverEnabled) 33L else 16L
            while (true) {
                delay(tickMs)
                val current = _editorState.value
                val duration = current.project?.totalDurationMs ?: 0L
                val nextMs = current.playheadMs + tickMs
                if (nextMs >= duration) {
                    _editorState.update { it.copy(playheadMs = 0L, isPlaying = false) }
                    break
                } else {
                    _editorState.update { it.copy(playheadMs = nextMs) }
                }
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _editorState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(ms: Long) {
        val maxMs = (_editorState.value.project?.totalDurationMs ?: 0L).coerceAtLeast(0L)
        _editorState.update { it.copy(playheadMs = ms.coerceIn(0L, maxMs)) }
    }

    fun stepFrame(forward: Boolean) {
        stopPlayback()
        val fps = (_editorState.value.project?.frameRate ?: 30L).coerceIn(24L, 60L)
        val frameMs = (1000L / fps).coerceAtLeast(16L)
        val delta = if (forward) frameMs else -frameMs
        seekTo(_editorState.value.playheadMs + delta)
    }

    fun updateTimelineZoom(newZoomDpPerSec: Float) {
        _editorState.update {
            it.copy(timelineZoomDpPerSec = newZoomDpPerSec.coerceIn(18f, 120f))
        }
    }

    fun selectToolTab(tab: EditorToolTab) {
        _editorState.update { it.copy(activeToolTab = tab) }
    }

    fun selectClip(clipId: String?) {
        _editorState.update { it.copy(selectedClipId = clipId) }
    }

    fun selectAudioTrack(trackId: String?) {
        _editorState.update { it.copy(selectedTrackId = trackId) }
    }

    fun setCompareOriginalColor(comparing: Boolean) {
        _editorState.update { it.copy(compareOriginalColor = comparing) }
    }

    // --- Non-Linear Edit Mutations with Undo/Redo & Auto-Cloud-Sync ---
    private fun applyProjectEdit(
        recordUndo: Boolean = true,
        transform: (VideoProject) -> VideoProject
    ) {
        val current = _editorState.value.project ?: return
        if (recordUndo) {
            if (undoStack.size >= 25) undoStack.removeFirst()
            undoStack.addLast(current)
            redoStack.clear()
        }
        val updated = transform(current)
        _editorState.update {
            it.copy(
                project = updated,
                playheadMs = it.playheadMs.coerceIn(0L, updated.totalDurationMs.coerceAtLeast(0L)),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                syncStatusLabel = "Syncing..."
            )
        }
        scheduleCloudSync(updated)
    }

    private fun scheduleCloudSync(project: VideoProject) {
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            val debounceMs = if (localSettings.value.batterySaverEnabled) 1400L else 650L
            delay(debounceMs)
            if (project.id.isBlank()) return@launch
            _editorState.update { it.copy(isSyncing = true) }
            val result = projectRepository.updateProject(project)
            _editorState.update {
                it.copy(
                    isSyncing = false,
                    syncStatusLabel = if (result.isSuccess) "Cloud Synced" else "Offline Cached"
                )
            }
        }
    }

    fun forceCloudSyncNow() {
        val project = _editorState.value.project ?: return
        if (project.id.isBlank()) return
        viewModelScope.launch {
            _editorState.update { it.copy(isSyncing = true, syncStatusLabel = "Syncing...") }
            val result = projectRepository.updateProject(project)
            _editorState.update {
                it.copy(
                    isSyncing = false,
                    syncStatusLabel = if (result.isSuccess) "Cloud Synced" else "Sync Pending"
                )
            }
            if (result.isSuccess) {
                showMessage("Project synced to cloud across all devices.")
            } else {
                showMessage("Sync queued offline: ${result.exceptionOrNull()?.localizedMessage ?: "Check connection"}")
            }
        }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        val current = _editorState.value.project
        if (current != null) redoStack.addLast(current)
        _editorState.update {
            it.copy(
                project = prev,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                syncStatusLabel = "Syncing..."
            )
        }
        scheduleCloudSync(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        val current = _editorState.value.project
        if (current != null) undoStack.addLast(current)
        _editorState.update {
            it.copy(
                project = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                syncStatusLabel = "Syncing..."
            )
        }
        scheduleCloudSync(next)
    }

    fun renameProject(newTitle: String) {
        val cleaned = newTitle.trim().take(100)
        if (cleaned.isBlank()) return
        applyProjectEdit { it.copy(title = cleaned) }
    }

    fun setAspectRatio(ratio: AspectRatioOption) {
        applyProjectEdit { it.copy(aspectRatio = ratio.raw) }
    }

    fun setRenderSpec(resolution: ResolutionOption, fps: Long) {
        applyProjectEdit { it.copy(resolution = resolution.raw, frameRate = fps) }
        refreshBatteryTelemetry()
    }

    // --- Timeline Clip Editing ---
    fun importVideoClipToTimeline(uri: Uri) {
        viewModelScope.launch {
            val meta = mediaEngine.inspectVideoUri(uri)
            val newClip = TimelineClip(
                label = meta.displayName,
                sourceUri = meta.uriString,
                startMs = 0L,
                endMs = meta.durationMs,
                trimStartMs = 0L,
                trimEndMs = meta.durationMs,
                sampleScene = "IMPORTED"
            )
            applyProjectEdit { proj ->
                val updatedClips = proj.typedClips() + newClip
                proj.withTypedContent(newClips = updatedClips)
            }
            _editorState.update { it.copy(selectedClipId = newClip.clipId) }
            extractThumbnailsForClips(listOf(newClip))
            showMessage("Added '${meta.displayName}' to timeline.")
        }
    }

    fun addSampleBRollClip() {
        val scenes = listOf("NEON_CITY" to "Tokyo Rain B-Roll", "COASTAL_HIGHWAY" to "Cliffside Drone Shot")
        val idx = (_editorState.value.project?.typedClips()?.size ?: 0) % scenes.size
        val (sceneKey, label) = scenes[idx]
        val clip = TimelineClip(
            label = label,
            startMs = 0L,
            endMs = 5000L,
            trimStartMs = 0L,
            trimEndMs = 5000L,
            transition = TransitionType.CROSS_DISSOLVE.raw,
            sampleScene = sceneKey
        )
        applyProjectEdit { proj -> proj.withTypedContent(newClips = proj.typedClips() + clip) }
        _editorState.update { it.copy(selectedClipId = clip.clipId) }
    }

    fun splitClipAtPlayhead() {
        val proj = _editorState.value.project ?: return
        val playhead = _editorState.value.playheadMs
        val clips = proj.typedClips()
        val targetIndex = clips.indexOfFirst { playhead > it.startMs + 350L && playhead < it.endMs - 350L }
        if (targetIndex == -1) {
            showMessage("Move playhead inside a clip (at least 0.35s from edge) to split.")
            return
        }
        val target = clips[targetIndex]
        val localOffsetSourceMs = ((playhead - target.startMs) * target.speed).toLong()
        val splitPointSourceMs = (target.trimStartMs + localOffsetSourceMs)
            .coerceIn(target.trimStartMs + 350L, target.trimEndMs - 350L)

        val firstHalf = target.copy(
            trimEndMs = splitPointSourceMs,
            label = "${target.label.substringBefore(" (")} (A)"
        )
        val secondHalf = target.copy(
            clipId = "clip_${UUID.randomUUID().toString().take(8)}",
            trimStartMs = splitPointSourceMs,
            label = "${target.label.substringBefore(" (")} (B)"
        )

        val updated = clips.toMutableList().apply {
            set(targetIndex, firstHalf)
            add(targetIndex + 1, secondHalf)
        }
        applyProjectEdit { it.withTypedContent(newClips = updated) }
        _editorState.update { it.copy(selectedClipId = secondHalf.clipId) }
        showMessage("Split clip at ${playhead}ms.")
    }

    fun trimSelectedClip(deltaStartMs: Long, deltaEndMs: Long) {
        val selectedId = _editorState.value.selectedClipId ?: return
        applyProjectEdit(recordUndo = false) { proj ->
            val updated = proj.typedClips().map { clip ->
                if (clip.clipId != selectedId) clip
                else {
                    val newStart = (clip.trimStartMs + deltaStartMs).coerceIn(0L, clip.trimEndMs - 500L)
                    val newEnd = (clip.trimEndMs + deltaEndMs).coerceAtLeast(newStart + 500L)
                    clip.copy(trimStartMs = newStart, trimEndMs = newEnd)
                }
            }
            proj.withTypedContent(newClips = updated)
        }
    }

    fun updateSelectedClipSpeed(speed: Double) {
        val selectedId = _editorState.value.selectedClipId ?: return
        applyProjectEdit { proj ->
            val updated = proj.typedClips().map { clip ->
                if (clip.clipId == selectedId) clip.copy(speed = speed.coerceIn(0.25, 4.0)) else clip
            }
            proj.withTypedContent(newClips = updated)
        }
    }

    fun updateSelectedClipTransition(transition: TransitionType) {
        val selectedId = _editorState.value.selectedClipId ?: return
        applyProjectEdit { proj ->
            val updated = proj.typedClips().map { clip ->
                if (clip.clipId == selectedId) clip.copy(transition = transition.raw) else clip
            }
            proj.withTypedContent(newClips = updated)
        }
    }

    fun duplicateSelectedClip() {
        val selectedId = _editorState.value.selectedClipId ?: return
        applyProjectEdit { proj ->
            val clips = proj.typedClips()
            val idx = clips.indexOfFirst { it.clipId == selectedId }
            if (idx == -1) return@applyProjectEdit proj
            val copyClip = clips[idx].copy(
                clipId = "clip_${UUID.randomUUID().toString().take(8)}",
                label = "${clips[idx].label} Copy"
            )
            val list = clips.toMutableList().apply { add(idx + 1, copyClip) }
            proj.withTypedContent(newClips = list)
        }
    }

    fun deleteSelectedClip() {
        val selectedId = _editorState.value.selectedClipId ?: return
        val currentClips = _editorState.value.project?.typedClips().orEmpty()
        if (currentClips.size <= 1) {
            showMessage("Timeline requires at least one clip.")
            return
        }
        applyProjectEdit { proj ->
            proj.withTypedContent(newClips = proj.typedClips().filterNot { it.clipId == selectedId })
        }
        _editorState.update { state ->
            state.copy(selectedClipId = state.project?.typedClips()?.firstOrNull()?.clipId)
        }
    }

    // --- Real-Time Color Grading ---
    fun applyColorPreset(preset: ColorPreset) {
        applyProjectEdit {
            it.copy(
                colorPreset = preset.raw,
                exposure = preset.exposure.toDouble(),
                contrast = preset.contrast.toDouble(),
                saturation = preset.saturation.toDouble(),
                temperature = preset.temperature.toDouble(),
                tint = preset.tint.toDouble(),
                vignette = preset.vignette.toDouble()
            )
        }
    }

    fun updateColorGrading(
        exposure: Double? = null,
        contrast: Double? = null,
        saturation: Double? = null,
        temperature: Double? = null,
        tint: Double? = null,
        vignette: Double? = null
    ) {
        applyProjectEdit(recordUndo = false) { proj ->
            proj.copy(
                exposure = (exposure ?: proj.exposure).coerceIn(-2.0, 2.0),
                contrast = (contrast ?: proj.contrast).coerceIn(0.5, 2.0),
                saturation = (saturation ?: proj.saturation).coerceIn(0.0, 2.0),
                temperature = (temperature ?: proj.temperature).coerceIn(-1.0, 1.0),
                tint = (tint ?: proj.tint).coerceIn(-1.0, 1.0),
                vignette = (vignette ?: proj.vignette).coerceIn(0.0, 1.0)
            )
        }
    }

    // --- Multi-Track Audio Mixing ---
    fun addAudioTrack(name: String, role: AudioRole, sourceUri: String = "") {
        val duration = (_editorState.value.project?.totalDurationMs ?: 12000L).coerceAtLeast(4000L)
        val track = AudioTrack(
            name = name.take(50).ifBlank { role.label },
            role = role.raw,
            sourceUri = sourceUri,
            volume = if (role == AudioRole.MUSIC) 0.45 else 0.9,
            fadeInMs = if (role == AudioRole.MUSIC) 600L else 0L,
            fadeOutMs = 800L,
            durationMs = duration
        )
        applyProjectEdit { proj -> proj.withTypedContent(newAudioTracks = proj.typedAudioTracks() + track) }
        _editorState.update { it.copy(selectedTrackId = track.trackId) }
        showMessage("Added ${track.name} to mixer.")
    }

    fun updateAudioTrack(trackId: String, updater: (AudioTrack) -> AudioTrack) {
        applyProjectEdit(recordUndo = false) { proj ->
            val updated = proj.typedAudioTracks().map { if (it.trackId == trackId) updater(it) else it }
            proj.withTypedContent(newAudioTracks = updated)
        }
    }

    fun deleteAudioTrack(trackId: String) {
        applyProjectEdit { proj ->
            proj.withTypedContent(newAudioTracks = proj.typedAudioTracks().filterNot { it.trackId == trackId })
        }
    }

    fun toggleAutoDuckMusic(enabled: Boolean) {
        _editorState.update { it.copy(autoDuckMusic = enabled) }
        if (enabled) {
            applyProjectEdit { proj ->
                val updated = proj.typedAudioTracks().map { track ->
                    when (AudioRole.fromRaw(track.role)) {
                        AudioRole.VOICEOVER -> track.copy(volume = 1.15)
                        AudioRole.MUSIC -> track.copy(volume = 0.32)
                        else -> track
                    }
                }
                proj.withTypedContent(newAudioTracks = updated)
            }
            showMessage("Auto-Duck applied: Voiceover boosted & music bed ducked.")
        }
    }

    // --- Captions Editing ---
    fun setCaptionStyle(style: CaptionStyle) {
        applyProjectEdit { it.copy(captionStyle = style.raw) }
    }

    fun addCaptionAtPlayhead(text: String, emphasisWord: String = "") {
        val clean = text.trim().take(180)
        if (clean.isBlank()) return
        val start = _editorState.value.playheadMs
        val maxDur = (_editorState.value.project?.totalDurationMs ?: 10000L).coerceAtLeast(start + 2500L)
        val end = (start + 2600L).coerceAtMost(maxDur)
        val cue = CaptionCue(
            startMs = start,
            endMs = end,
            text = clean,
            emphasisWord = emphasisWord.trim().take(40)
        )
        applyProjectEdit { proj -> proj.withTypedContent(newCaptions = proj.typedCaptions() + cue) }
    }

    fun updateCaptionText(captionId: String, newText: String, emphasisWord: String) {
        applyProjectEdit { proj ->
            val updated = proj.typedCaptions().map { cue ->
                if (cue.captionId == captionId) {
                    cue.copy(text = newText.take(180), emphasisWord = emphasisWord.take(40))
                } else cue
            }
            proj.withTypedContent(newCaptions = updated)
        }
    }

    fun deleteCaption(captionId: String) {
        applyProjectEdit { proj ->
            proj.withTypedContent(newCaptions = proj.typedCaptions().filterNot { it.captionId == captionId })
        }
    }

    fun createSrtShareUri(): Uri? {
        val project = _editorState.value.project ?: return null
        return mediaEngine.createSrtShareUri(project.title, project.typedCaptions())
    }

    // --- Firebase AI Logic Powered Tools (Gemini 3.8 Flash) ---
    fun runAiQuickCuts(pacingStyle: String, editorBrief: String) {
        val project = _editorState.value.project ?: return
        viewModelScope.launch {
            _editorState.update { it.copy(aiTaskState = AiTaskState.Loading("Analyzing clips for $pacingStyle quick cuts...")) }
            val bitmaps = _editorState.value.clipThumbnails.values.flatten().take(4)
            val result = aiRepository.generateQuickCuts(project, pacingStyle, editorBrief, bitmaps)
            result.onSuccess { plan ->
                _editorState.update { it.copy(aiTaskState = AiTaskState.QuickCutReady(plan)) }
            }.onFailure { err ->
                _editorState.update {
                    it.copy(aiTaskState = AiTaskState.Error(err.message ?: "AI Quick-Cut failed."))
                }
            }
        }
    }

    fun applyAiQuickCutPlan(plan: AiQuickCutPlan) {
        applyProjectEdit { proj ->
            val existingClips = proj.typedClips()
            if (existingClips.isEmpty()) return@applyProjectEdit proj
            val optimizedClips = plan.cuts.mapNotNull { cut ->
                val src = existingClips.getOrNull(cut.clipIndex) ?: existingClips.firstOrNull() ?: return@mapNotNull null
                val maxSourceEnd = src.trimEndMs.coerceAtLeast(src.trimStartMs + 1200L)
                val safeStart = cut.trimStartMs.coerceIn(0L, (maxSourceEnd - 800L).coerceAtLeast(0L))
                val safeEnd = cut.trimEndMs.coerceIn(safeStart + 800L, maxSourceEnd)
                src.copy(
                    clipId = "clip_${UUID.randomUUID().toString().take(8)}",
                    trimStartMs = safeStart,
                    trimEndMs = safeEnd,
                    speed = cut.speed.coerceIn(0.5, 2.5),
                    transition = cut.transition.raw
                )
            }.ifEmpty { existingClips }

            proj.copy(aiSummary = "AI Quick-Cut (${plan.pacingStyle}): ${plan.summary}".take(600))
                .withTypedContent(newClips = optimizedClips)
        }
        _editorState.update { it.copy(aiTaskState = AiTaskState.Idle, playheadMs = 0L) }
        showMessage("Applied ${plan.cuts.size} AI Quick Cuts to timeline!")
    }

    fun runAiAutomatedCaptioning(language: String, scriptOrContext: String) {
        val project = _editorState.value.project ?: return
        viewModelScope.launch {
            _editorState.update { it.copy(aiTaskState = AiTaskState.Loading("Generating frame-accurate $language captions...")) }
            val firstLocalMediaUri = project.typedAudioTracks().firstOrNull { it.sourceUri.isNotBlank() }?.sourceUri
                ?: project.typedClips().firstOrNull { it.sourceUri.isNotBlank() }?.sourceUri
            val inlineMedia = if (!firstLocalMediaUri.isNullOrBlank()) {
                mediaEngine.readInlineBytesForAi(firstLocalMediaUri)
            } else null
            val bitmaps = _editorState.value.clipThumbnails.values.flatten().take(4)

            val result = aiRepository.generateAutomatedCaptions(
                project = project,
                language = language,
                scriptOrContext = scriptOrContext,
                keyframeBitmaps = bitmaps,
                inlineAudioOrVideoBytes = inlineMedia?.first,
                inlineMimeType = inlineMedia?.second
            )
            result.onSuccess { (summary, cues) ->
                applyProjectEdit { proj ->
                    proj.copy(aiSummary = "AI Captions ($language): $summary".take(600))
                        .withTypedContent(newCaptions = cues)
                }
                _editorState.update { it.copy(aiTaskState = AiTaskState.CaptionsReady(summary, cues.size)) }
                showMessage("Added ${cues.size} synced AI captions to timeline.")
            }.onFailure { err ->
                _editorState.update {
                    it.copy(aiTaskState = AiTaskState.Error(err.message ?: "AI Captioning failed."))
                }
            }
        }
    }

    fun runAiColorAndMix(moodPrompt: String) {
        val project = _editorState.value.project ?: return
        viewModelScope.launch {
            _editorState.update { it.copy(aiTaskState = AiTaskState.Loading("Designing cinema grade & audio mix...")) }
            val sampleBitmap = _editorState.value.clipThumbnails.values.flatten().firstOrNull()
            val result = aiRepository.suggestColorAndAudioMix(project, moodPrompt, sampleBitmap)
            result.onSuccess { suggestion ->
                _editorState.update { it.copy(aiTaskState = AiTaskState.ColorMixReady(suggestion)) }
            }.onFailure { err ->
                _editorState.update {
                    it.copy(aiTaskState = AiTaskState.Error(err.message ?: "AI Color & Mix failed."))
                }
            }
        }
    }

    fun applyAiColorAndMix(suggestion: AiColorAudioSuggestion) {
        applyProjectEdit { proj ->
            val mixedAudio = proj.typedAudioTracks().map { track ->
                when (AudioRole.fromRaw(track.role)) {
                    AudioRole.VOICEOVER -> track.copy(volume = suggestion.voiceoverVolume)
                    AudioRole.MUSIC -> track.copy(volume = suggestion.musicVolume)
                    AudioRole.ORIGINAL -> track.copy(volume = suggestion.originalVolume)
                    AudioRole.SFX -> track
                }
            }
            proj.copy(
                colorPreset = suggestion.colorPreset.raw,
                exposure = suggestion.exposure,
                contrast = suggestion.contrast,
                saturation = suggestion.saturation,
                temperature = suggestion.temperature,
                tint = suggestion.tint,
                vignette = suggestion.vignette,
                aiSummary = suggestion.summary.take(600)
            ).withTypedContent(newAudioTracks = mixedAudio)
        }
        _editorState.update { it.copy(aiTaskState = AiTaskState.Idle) }
        showMessage("Applied ${suggestion.colorPreset.label} LUT & balanced audio mix!")
    }

    fun dismissAiState() {
        _editorState.update { it.copy(aiTaskState = AiTaskState.Idle) }
    }

    // --- 4K Export & Cloud Record ---
    fun startExport(
        resolution: ResolutionOption,
        codec: CodecOption,
        fps: Long,
        destination: ExportDestination
    ) {
        val project = _editorState.value.project ?: return
        if (_editorState.value.isExporting) return
        stopPlayback()
        viewModelScope.launch {
            _editorState.update { it.copy(isExporting = true, exportState = null) }
            // Ensure latest project edits exist in Firestore before logging export
            if (project.id.isNotBlank()) {
                projectRepository.updateProject(project.copy(cloudBackupStatus = "EXPORTED"))
            }
            mediaEngine.exportProjectPipeline(
                project = project,
                resolution = resolution,
                codec = codec,
                fps = fps,
                batterySaverEnabled = localSettings.value.batterySaverEnabled
            ).collect { progress ->
                _editorState.update { it.copy(exportState = progress) }
                if (progress.completedOutputUri != null && project.id.isNotBlank()) {
                    val record = ExportRecord(
                        userId = currentUserId,
                        projectId = project.id,
                        projectTitle = project.title,
                        resolution = resolution.raw,
                        codec = codec.raw,
                        frameRate = fps,
                        durationMs = project.totalDurationMs,
                        estimatedSizeMb = progress.estimatedSizeMb,
                        destination = destination.raw,
                        localOutputUri = progress.completedOutputUri,
                        status = "COMPLETED"
                    )
                    projectRepository.recordExport(record)
                    if (localSettings.value.autoOfflineBackup) {
                        projectRepository.saveLocalOfflineBackup(
                            currentUserId,
                            project,
                            "Pre-Export ${resolution.label} Snapshot"
                        )
                    }
                    _editorState.update { it.copy(isExporting = false) }
                    showMessage("Export complete: ${progress.stageLabel}")
                }
            }
            _editorState.update { it.copy(isExporting = false) }
        }
    }

    // --- Offline Backup Vault (Room SQLite + Portable .fcproj.json) ---
    fun createManualOfflineBackup(note: String = "Manual Local Snapshot") {
        val project = _editorState.value.project ?: return
        viewModelScope.launch {
            projectRepository.saveLocalOfflineBackup(currentUserId, project, note).onSuccess {
                showMessage("Saved offline snapshot to Room local vault.")
            }.onFailure { err ->
                showMessage("Backup failed: ${err.localizedMessage ?: "Storage error"}")
            }
        }
    }

    fun restoreBackupAsCloudProject(backup: ProjectBackupEntity, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val restored = VideoProject.fromBackupJson(backup.payloadJson, currentUserId)
                    .copy(title = "${backup.projectTitle} (Restored)".take(100))
                projectRepository.createProject(restored).onSuccess { created ->
                    openProject(created)
                    showMessage("Restored offline snapshot to cloud.")
                    onOpened(created.id)
                }.onFailure { err ->
                    showMessage("Restore failed: ${err.localizedMessage ?: "Error"}")
                }
            } catch (e: Exception) {
                showMessage("Corrupt backup snapshot: ${e.localizedMessage}")
            }
        }
    }

    fun importBackupJsonString(jsonString: String, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val parsed = VideoProject.fromBackupJson(jsonString, currentUserId)
                projectRepository.createProject(parsed).onSuccess { created ->
                    projectRepository.saveLocalOfflineBackup(currentUserId, created, "Imported .fcproj.json")
                    openProject(created)
                    showMessage("Imported offline project backup.")
                    onOpened(created.id)
                }.onFailure { err ->
                    showMessage("Import sync failed: ${err.localizedMessage}")
                }
            } catch (e: Exception) {
                showMessage("Invalid .fcproj.json backup file.")
            }
        }
    }

    fun deleteOfflineBackup(backupId: Long) {
        viewModelScope.launch {
            projectRepository.deleteLocalBackup(backupId, currentUserId)
            showMessage("Offline snapshot removed.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        autoSyncJob?.cancel()
    }
}
