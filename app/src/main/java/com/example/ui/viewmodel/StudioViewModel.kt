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
import com.example.data.model.ExportFormatOption
import com.example.data.model.ExportRecord
import com.example.data.model.ResolutionOption
import com.example.data.model.TimelineClip
import com.example.data.model.TransitionEasing
import com.example.data.model.TransitionType
import com.example.data.model.VideoProject
import com.example.data.repository.AiVideoAssistantRepository
import com.example.data.repository.MusicModelOption
import com.example.data.repository.ProjectRepository
import com.example.data.repository.VideoGenAspectRatio
import com.example.media.BatteryTelemetry
import com.example.media.ExportProgressState
import com.example.media.StudioAudioPreset
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

sealed interface AiClipGenState {
    data object Idle : AiClipGenState
    data class Loading(val stage: String) : AiClipGenState
    data class Success(val clipId: String, val title: String, val bitmap: Bitmap) : AiClipGenState
    data class Error(val message: String) : AiClipGenState
}

sealed interface AiVideoGenState {
    data object Idle : AiVideoGenState
    data class Loading(val stage: String) : AiVideoGenState
    data class Success(
        val clipId: String,
        val title: String,
        val videoUri: String?,
        val previewBitmap: Bitmap?,
        val durationMs: Long,
        val aspectRatio: VideoGenAspectRatio,
        val notes: String,
        val modelUsed: String
    ) : AiVideoGenState
    data class Error(val message: String) : AiVideoGenState
}

sealed interface AiImageStudioState {
    data object Idle : AiImageStudioState
    data class Loading(val stage: String) : AiImageStudioState
    data class Success(
        val bitmap: Bitmap,
        val promptUsed: String,
        val isEdit: Boolean
    ) : AiImageStudioState
    data class Error(val message: String) : AiImageStudioState
}

sealed interface AiMusicGenState {
    data object Idle : AiMusicGenState
    data class Loading(val stage: String) : AiMusicGenState
    data class Success(
        val trackId: String,
        val title: String,
        val localAudioPath: String,
        val durationMs: Long,
        val lyricsOrNotes: String,
        val modelUsed: String
    ) : AiMusicGenState
    data class Error(val message: String) : AiMusicGenState
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
    val aiClipGenState: AiClipGenState = AiClipGenState.Idle,
    val aiVideoGenState: AiVideoGenState = AiVideoGenState.Idle,
    val aiImageStudioState: AiImageStudioState = AiImageStudioState.Idle,
    val aiMusicGenState: AiMusicGenState = AiMusicGenState.Idle,
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
    private val userDisplayName: String,
    val isGuestMode: Boolean = false
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

    private val _editorState = MutableStateFlow(
        EditorUiState(syncStatusLabel = if (isGuestMode) "Saved Locally" else "Cloud Synced")
    )
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
                aiClipGenState = AiClipGenState.Idle,
                aiVideoGenState = AiVideoGenState.Idle,
                aiImageStudioState = AiImageStudioState.Idle,
                aiMusicGenState = AiMusicGenState.Idle,
                exportState = null,
                isExporting = false,
                syncStatusLabel = if (isGuestMode) "Saved Locally" else "Cloud Synced",
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
                showMessage(if (isGuestMode) "Project deleted from device." else "Project deleted from cloud.")
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
                syncStatusLabel = if (isGuestMode) "Saving locally..." else "Syncing..."
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
                    syncStatusLabel = when {
                        isGuestMode -> if (result.isSuccess) "Saved Locally" else "Save Pending"
                        result.isSuccess -> "Cloud Synced"
                        else -> "Offline Cached"
                    }
                )
            }
        }
    }

    fun forceCloudSyncNow() {
        val project = _editorState.value.project ?: return
        if (project.id.isBlank()) return
        viewModelScope.launch {
            _editorState.update {
                it.copy(
                    isSyncing = true,
                    syncStatusLabel = if (isGuestMode) "Saving locally..." else "Syncing..."
                )
            }
            val result = projectRepository.updateProject(project)
            _editorState.update {
                it.copy(
                    isSyncing = false,
                    syncStatusLabel = when {
                        isGuestMode -> if (result.isSuccess) "Saved Locally" else "Save Pending"
                        result.isSuccess -> "Cloud Synced"
                        else -> "Sync Pending"
                    }
                )
            }
            if (isGuestMode) {
                showMessage("Saved locally on this device. Sign in with Google to sync across devices.")
            } else if (result.isSuccess) {
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
                    val newStart = (clip.trimStartMs + deltaStartMs).coerceIn(0L, (clip.trimEndMs - 500L).coerceAtLeast(0L))
                    val newEnd = (clip.trimEndMs + deltaEndMs).coerceAtLeast(newStart + 500L)
                    clip.copy(trimStartMs = newStart, trimEndMs = newEnd)
                }
            }
            proj.withTypedContent(newClips = updated)
        }
    }

    fun setSelectedClipTrimRange(trimStartMs: Long, trimEndMs: Long) {
        val selectedId = _editorState.value.selectedClipId ?: return
        applyProjectEdit(recordUndo = false) { proj ->
            val updated = proj.typedClips().map { clip ->
                if (clip.clipId != selectedId) clip
                else {
                    val safeStart = trimStartMs.coerceAtLeast(0L)
                    val safeEnd = trimEndMs.coerceAtLeast(safeStart + 500L)
                    clip.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
                }
            }
            proj.withTypedContent(newClips = updated)
        }
    }

    fun trimSelectedClipToPlayhead(trimStart: Boolean) {
        val proj = _editorState.value.project ?: return
        val selectedId = _editorState.value.selectedClipId ?: return
        val playhead = _editorState.value.playheadMs
        val target = proj.typedClips().firstOrNull { it.clipId == selectedId } ?: return
        if (playhead <= target.startMs + 100L || playhead >= target.endMs - 100L) {
            showMessage("Move the playhead inside '${target.label}' to trim at playhead.")
            return
        }
        val localSourceOffset = ((playhead - target.startMs) * target.speed).toLong()
        val cutSourceMs = target.trimStartMs + localSourceOffset
        if (trimStart) {
            if (target.trimEndMs - cutSourceMs < 500L) {
                showMessage("Clip must remain at least 0.5s long.")
                return
            }
            applyProjectEdit { currentProj ->
                val updated = currentProj.typedClips().map { clip ->
                    if (clip.clipId == selectedId) clip.copy(trimStartMs = cutSourceMs) else clip
                }
                currentProj.withTypedContent(newClips = updated)
            }
            val updatedClip = _editorState.value.project?.typedClips()?.firstOrNull { it.clipId == selectedId }
            if (updatedClip != null) seekTo(updatedClip.startMs)
            showMessage("Trimmed In point of '${target.label}' to playhead.")
        } else {
            if (cutSourceMs - target.trimStartMs < 500L) {
                showMessage("Clip must remain at least 0.5s long.")
                return
            }
            applyProjectEdit { currentProj ->
                val updated = currentProj.typedClips().map { clip ->
                    if (clip.clipId == selectedId) clip.copy(trimEndMs = cutSourceMs) else clip
                }
                currentProj.withTypedContent(newClips = updated)
            }
            showMessage("Trimmed Out point of '${target.label}' to playhead.")
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
        val existing = _editorState.value.project?.typedClips()?.firstOrNull { it.clipId == selectedId }
        updateClipTransition(
            clipId = selectedId,
            transition = transition,
            durationMs = existing?.transitionDurationMs ?: 500L,
            easing = TransitionEasing.fromRaw(existing?.transitionEasing)
        )
    }

    fun updateClipTransition(
        clipId: String,
        transition: TransitionType,
        durationMs: Long,
        easing: TransitionEasing
    ) {
        val clampedDur = durationMs.coerceIn(150L, 1500L)
        applyProjectEdit { proj ->
            val updated = proj.typedClips().map { clip ->
                if (clip.clipId == clipId) {
                    clip.copy(
                        transition = transition.raw,
                        transitionDurationMs = clampedDur,
                        transitionEasing = easing.raw
                    )
                } else clip
            }
            proj.withTypedContent(newClips = updated)
        }
    }

    fun applyTransitionToAllClips(
        transition: TransitionType,
        durationMs: Long,
        easing: TransitionEasing
    ) {
        val clampedDur = durationMs.coerceIn(150L, 1500L)
        applyProjectEdit { proj ->
            val updated = proj.typedClips().map { clip ->
                clip.copy(
                    transition = transition.raw,
                    transitionDurationMs = clampedDur,
                    transitionEasing = easing.raw
                )
            }
            proj.withTypedContent(newClips = updated)
        }
        showMessage("Applied ${transition.label} (${clampedDur}ms) to all clip seams.")
    }

    fun previewClipTransition(clipId: String) {
        val clip = _editorState.value.project?.typedClips()?.firstOrNull { it.clipId == clipId } ?: return
        val leadIn = (clip.transitionDurationMs + 550L).coerceAtMost((clip.endMs - clip.startMs).coerceAtLeast(300L))
        val seekTarget = (clip.endMs - leadIn).coerceAtLeast(0L)
        stopPlayback()
        seekTo(seekTarget)
        startPlayback()
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

    fun reorderClip(fromIndex: Int, toIndex: Int) {
        val currentClips = _editorState.value.project?.typedClips().orEmpty()
        if (fromIndex !in currentClips.indices || toIndex !in currentClips.indices || fromIndex == toIndex) {
            return
        }
        val movedClip = currentClips[fromIndex]
        applyProjectEdit { proj ->
            val mutable = proj.typedClips().toMutableList()
            if (fromIndex !in mutable.indices || toIndex !in mutable.indices) return@applyProjectEdit proj
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            proj.withTypedContent(newClips = mutable)
        }
        val updatedClip = _editorState.value.project?.typedClips()?.firstOrNull { it.clipId == movedClip.clipId }
        _editorState.update { state ->
            state.copy(
                selectedClipId = movedClip.clipId,
                playheadMs = updatedClip?.startMs ?: state.playheadMs
            )
        }
        showMessage("Moved '${movedClip.label}' to slot #${toIndex + 1}.")
    }

    fun moveSelectedClipBy(delta: Int) {
        val selectedId = _editorState.value.selectedClipId ?: return
        val clips = _editorState.value.project?.typedClips().orEmpty()
        val currentIndex = clips.indexOfFirst { it.clipId == selectedId }
        if (currentIndex == -1) return
        val targetIndex = (currentIndex + delta).coerceIn(0, clips.lastIndex)
        reorderClip(currentIndex, targetIndex)
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

    // --- Multi-Track Audio Mixing & Timeline Audio Layer ---
    fun addAudioTrack(name: String, role: AudioRole, sourceUri: String = "", offsetMs: Long = 0L, durationOverrideMs: Long? = null) {
        val currentCount = _editorState.value.project?.typedAudioTracks()?.size ?: 0
        if (currentCount >= 12) {
            showMessage("Audio layer supports up to 12 tracks per project.")
            return
        }
        val projectDuration = (_editorState.value.project?.totalDurationMs ?: 12000L).coerceAtLeast(4000L)
        val trackDuration = (durationOverrideMs ?: if (role == AudioRole.SFX) 1800L else projectDuration).coerceAtLeast(500L)
        val safeOffset = offsetMs.coerceIn(0L, (projectDuration - 300L).coerceAtLeast(0L))
        val track = AudioTrack(
            name = name.take(50).ifBlank { role.label },
            role = role.raw,
            sourceUri = sourceUri,
            volume = when (role) {
                AudioRole.MUSIC -> 0.55
                AudioRole.SFX -> 0.88
                else -> 0.90
            },
            fadeInMs = if (role == AudioRole.MUSIC) 600L else 0L,
            fadeOutMs = if (role == AudioRole.MUSIC) 800L else 150L,
            offsetMs = safeOffset,
            durationMs = trackDuration
        )
        applyProjectEdit { proj -> proj.withTypedContent(newAudioTracks = proj.typedAudioTracks() + track) }
        _editorState.update { it.copy(selectedTrackId = track.trackId) }
        showMessage("Added '${track.name}' to timeline audio layer.")
    }

    fun importAudioTrack(uri: Uri, role: AudioRole, atPlayhead: Boolean = role == AudioRole.SFX) {
        val currentCount = _editorState.value.project?.typedAudioTracks()?.size ?: 0
        if (currentCount >= 12) {
            showMessage("Audio layer supports up to 12 tracks per project.")
            return
        }
        viewModelScope.launch {
            val meta = mediaEngine.inspectAudioUri(uri)
            val startOffset = if (atPlayhead) _editorState.value.playheadMs else 0L
            addAudioTrack(
                name = meta.displayName,
                role = role,
                sourceUri = meta.uriString,
                offsetMs = startOffset,
                durationOverrideMs = meta.durationMs
            )
        }
    }

    fun addStudioAudioPreset(preset: StudioAudioPreset, atPlayhead: Boolean = preset.role == AudioRole.SFX) {
        val currentCount = _editorState.value.project?.typedAudioTracks()?.size ?: 0
        if (currentCount >= 12) {
            showMessage("Audio layer supports up to 12 tracks per project.")
            return
        }
        viewModelScope.launch {
            val filePath = mediaEngine.ensureStudioAudioPreset(preset)
            val startOffset = if (atPlayhead) _editorState.value.playheadMs else 0L
            addAudioTrack(
                name = preset.name,
                role = preset.role,
                sourceUri = "local_audio://$filePath",
                offsetMs = startOffset,
                durationOverrideMs = preset.durationMs
            )
        }
    }

    fun prepareStudioAudioPreviewPath(preset: StudioAudioPreset, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val path = mediaEngine.ensureStudioAudioPreset(preset)
            onReady(path)
        }
    }

    fun updateAudioTrack(trackId: String, updater: (AudioTrack) -> AudioTrack) {
        applyProjectEdit(recordUndo = false) { proj ->
            val updated = proj.typedAudioTracks().map { if (it.trackId == trackId) updater(it) else it }
            proj.withTypedContent(newAudioTracks = updated)
        }
    }

    fun updateAudioTrackVolume(trackId: String, volume: Double) {
        val clamped = volume.coerceIn(0.0, 2.0)
        updateAudioTrack(trackId) { it.copy(volume = clamped) }
    }

    fun moveAudioTrackOffset(trackId: String, offsetMs: Long) {
        val maxOffset = ((_editorState.value.project?.totalDurationMs ?: 18000L) - 300L).coerceAtLeast(0L)
        val clamped = offsetMs.coerceIn(0L, maxOffset)
        updateAudioTrack(trackId) { it.copy(offsetMs = clamped) }
    }

    fun snapAudioTrackToPlayhead(trackId: String) {
        val target = _editorState.value.playheadMs
        applyProjectEdit { proj ->
            val maxOffset = (proj.totalDurationMs - 300L).coerceAtLeast(0L)
            val updated = proj.typedAudioTracks().map {
                if (it.trackId == trackId) it.copy(offsetMs = target.coerceIn(0L, maxOffset)) else it
            }
            proj.withTypedContent(newAudioTracks = updated)
        }
        showMessage("Aligned audio track start to playhead.")
    }

    fun toggleAudioTrackMute(trackId: String) {
        applyProjectEdit { proj ->
            val updated = proj.typedAudioTracks().map {
                if (it.trackId == trackId) it.copy(muted = !it.muted) else it
            }
            proj.withTypedContent(newAudioTracks = updated)
        }
    }

    fun toggleAudioTrackSolo(trackId: String) {
        applyProjectEdit { proj ->
            val updated = proj.typedAudioTracks().map {
                if (it.trackId == trackId) it.copy(solo = !it.solo) else it
            }
            proj.withTypedContent(newAudioTracks = updated)
        }
    }

    fun deleteAudioTrack(trackId: String) {
        applyProjectEdit { proj ->
            proj.withTypedContent(newAudioTracks = proj.typedAudioTracks().filterNot { it.trackId == trackId })
        }
        _editorState.update { state ->
            val nextSelected = if (state.selectedTrackId == trackId) {
                state.project?.typedAudioTracks()?.firstOrNull()?.trackId
            } else state.selectedTrackId
            state.copy(selectedTrackId = nextSelected)
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

    fun resetAiClipGenState() {
        _editorState.update { it.copy(aiClipGenState = AiClipGenState.Idle) }
    }

    fun generateAiVideoClip(
        prompt: String,
        visualStyle: String,
        durationSec: Int,
        cameraMotion: String
    ) {
        val project = _editorState.value.project ?: return
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            _editorState.update {
                it.copy(aiClipGenState = AiClipGenState.Error("Describe the video scene you want Gemini AI to create."))
            }
            return
        }
        viewModelScope.launch {
            _editorState.update {
                it.copy(
                    aiClipGenState = AiClipGenState.Loading(
                        "Directing scene & rendering AI keyframe with Gemini (may take 30–60s)..."
                    )
                )
            }
            val result = aiRepository.generateAiVideoClip(
                userPrompt = cleanPrompt,
                visualStyle = visualStyle,
                aspectRatio = project.aspectRatio,
                cameraMotion = cameraMotion
            )
            result.onSuccess { generated ->
                val clipId = "clip_ai_${UUID.randomUUID().toString().take(8)}"
                val savedUri = mediaEngine.saveAiClipKeyframe(clipId, generated.keyframeBitmap)
                val safeDurationMs = (durationSec.coerceIn(2, 12) * 1000L)
                val newClip = TimelineClip(
                    clipId = clipId,
                    label = generated.title,
                    sourceUri = savedUri,
                    startMs = 0L,
                    endMs = safeDurationMs,
                    trimStartMs = 0L,
                    trimEndMs = safeDurationMs,
                    speed = 1.0,
                    transition = generated.suggestedTransition.raw,
                    transitionDurationMs = 550L,
                    transitionEasing = TransitionEasing.EASE_IN_OUT.raw,
                    sampleScene = cameraMotion
                )

                var insertedStartMs = 0L
                applyProjectEdit { proj ->
                    val updatedClips = proj.typedClips() + newClip
                    val updatedProject = proj.withTypedContent(newClips = updatedClips)
                    val placedClip = updatedProject.typedClips().firstOrNull { it.clipId == clipId }
                    insertedStartMs = placedClip?.startMs ?: 0L
                    if (generated.suggestedCaption.isNotBlank() && placedClip != null) {
                        val cue = CaptionCue(
                            captionId = "cap_ai_${UUID.randomUUID().toString().take(8)}",
                            startMs = placedClip.startMs + 250L,
                            endMs = (placedClip.endMs - 250L).coerceAtLeast(placedClip.startMs + 800L),
                            text = generated.suggestedCaption,
                            emphasisWord = generated.emphasisWord,
                            speaker = "AI Narrator"
                        )
                        updatedProject.withTypedContent(newCaptions = updatedProject.typedCaptions() + cue)
                    } else {
                        updatedProject
                    }
                }
                _editorState.update { state ->
                    state.copy(
                        selectedClipId = clipId,
                        playheadMs = insertedStartMs,
                        clipThumbnails = state.clipThumbnails + (clipId to listOf(generated.keyframeBitmap)),
                        aiClipGenState = AiClipGenState.Success(
                            clipId = clipId,
                            title = generated.title,
                            bitmap = generated.keyframeBitmap
                        )
                    )
                }
                showMessage("Added AI clip '${generated.title}' to timeline!")
            }.onFailure { err ->
                _editorState.update {
                    it.copy(
                        aiClipGenState = AiClipGenState.Error(
                            err.message ?: "Could not generate AI video clip. Please try again."
                        )
                    )
                }
            }
        }
    }

    // --- Generative Media Suite (Text->Video, Image->Video, Image Create/Edit, Lyria Music) ---
    fun decodePhotoBitmap(uri: Uri, onResult: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            onResult(mediaEngine.loadScaledBitmapFromUri(uri))
        }
    }

    fun resetAiVideoGenState() {
        _editorState.update { it.copy(aiVideoGenState = AiVideoGenState.Idle) }
    }

    fun resetAiImageStudioState() {
        _editorState.update { it.copy(aiImageStudioState = AiImageStudioState.Idle) }
    }

    fun resetAiMusicGenState() {
        _editorState.update { it.copy(aiMusicGenState = AiMusicGenState.Idle) }
    }

    fun generateVideoFromText(
        prompt: String,
        aspectRatio: VideoGenAspectRatio,
        visualStyle: String
    ) {
        viewModelScope.launch {
            _editorState.update {
                it.copy(
                    aiVideoGenState = AiVideoGenState.Loading(
                        "Generating ${aspectRatio.raw} video with gemini-omni-1.2-flash (may take 30–120s)..."
                    )
                )
            }
            val result = aiRepository.generateVideoFromText(prompt, aspectRatio, visualStyle)
            handleGeneratedVideoResult(result)
        }
    }

    fun animateBitmapToVideo(
        sourceBitmap: Bitmap,
        motionPrompt: String,
        aspectRatio: VideoGenAspectRatio
    ) {
        viewModelScope.launch {
            _editorState.update {
                it.copy(
                    aiVideoGenState = AiVideoGenState.Loading(
                        "Animating photo into ${aspectRatio.raw} video with gemini-omni-1.2-flash (may take 30–120s)..."
                    )
                )
            }
            val result = aiRepository.animateImageToVideo(sourceBitmap, motionPrompt, aspectRatio)
            handleGeneratedVideoResult(result)
        }
    }

    private suspend fun handleGeneratedVideoResult(
        result: Result<com.example.data.repository.AiGeneratedVideoResult>
    ) {
        result.onSuccess { generated ->
            val clipId = "clip_vid_${UUID.randomUUID().toString().take(8)}"
            var savedVideoUri: String? = generated.remoteFileUri
            var durationMs = 6000L
            var previewBmp = generated.previewBitmap

            if (generated.videoBytes != null && generated.videoBytes.isNotEmpty()) {
                val (uriStr, dur) = mediaEngine.saveAiVideoFile(clipId, generated.videoBytes, generated.mimeType)
                savedVideoUri = uriStr
                durationMs = dur
                if (previewBmp == null) {
                    previewBmp = mediaEngine.extractKeyframes(uriStr, count = 1, batterySaverEnabled = true).firstOrNull()
                }
            } else if (previewBmp != null && savedVideoUri.isNullOrBlank()) {
                savedVideoUri = mediaEngine.saveAiClipKeyframe(clipId, previewBmp)
            }

            _editorState.update {
                it.copy(
                    aiVideoGenState = AiVideoGenState.Success(
                        clipId = clipId,
                        title = generated.title,
                        videoUri = savedVideoUri,
                        previewBitmap = previewBmp,
                        durationMs = durationMs,
                        aspectRatio = generated.aspectRatio,
                        notes = generated.notes,
                        modelUsed = generated.modelUsed
                    )
                )
            }
        }.onFailure { err ->
            _editorState.update {
                it.copy(
                    aiVideoGenState = AiVideoGenState.Error(
                        err.message ?: "Video generation failed."
                    )
                )
            }
        }
    }

    fun addGeneratedVideoToTimeline(result: AiVideoGenState.Success) {
        val targetAspect = if (result.aspectRatio == VideoGenAspectRatio.PORTRAIT_9_16) {
            AspectRatioOption.VERTICAL_9_16
        } else {
            AspectRatioOption.WIDE_16_9
        }
        val newClip = TimelineClip(
            clipId = result.clipId,
            label = result.title,
            sourceUri = result.videoUri.orEmpty(),
            startMs = 0L,
            endMs = result.durationMs,
            trimStartMs = 0L,
            trimEndMs = result.durationMs,
            speed = 1.0,
            transition = TransitionType.CROSS_DISSOLVE.raw,
            sampleScene = "AI_ZOOM_IN"
        )
        var insertedStartMs = 0L
        applyProjectEdit { proj ->
            val updated = proj.copy(aspectRatio = targetAspect.raw)
                .withTypedContent(newClips = proj.typedClips() + newClip)
            insertedStartMs = updated.typedClips().firstOrNull { it.clipId == newClip.clipId }?.startMs ?: 0L
            updated
        }
        _editorState.update { state ->
            val updatedThumbs = if (result.previewBitmap != null) {
                state.clipThumbnails + (newClip.clipId to listOf(result.previewBitmap))
            } else state.clipThumbnails
            state.copy(
                selectedClipId = newClip.clipId,
                playheadMs = insertedStartMs,
                clipThumbnails = updatedThumbs
            )
        }
        if (!result.videoUri.isNullOrBlank() && !result.videoUri.startsWith("ai_file://")) {
            extractThumbnailsForClips(listOf(newClip))
        }
        showMessage("Added AI video '${result.title}' (${result.aspectRatio.raw}) to timeline!")
    }

    fun createOrEditAiImage(
        prompt: String,
        sourceBitmap: Bitmap?,
        aspectRatioHint: String
    ) {
        viewModelScope.launch {
            val actionLabel = if (sourceBitmap != null) "Editing image" else "Creating image"
            _editorState.update {
                it.copy(
                    aiImageStudioState = AiImageStudioState.Loading(
                        "$actionLabel with gemini-nano-banana-2.1 (may take 20–60s)..."
                    )
                )
            }
            val result = aiRepository.createOrEditImage(prompt, sourceBitmap, aspectRatioHint)
            result.onSuccess { generated ->
                _editorState.update {
                    it.copy(
                        aiImageStudioState = AiImageStudioState.Success(
                            bitmap = generated.bitmap,
                            promptUsed = prompt.trim(),
                            isEdit = generated.isEdit
                        )
                    )
                }
            }.onFailure { err ->
                _editorState.update {
                    it.copy(
                        aiImageStudioState = AiImageStudioState.Error(
                            err.message ?: "Image generation failed."
                        )
                    )
                }
            }
        }
    }

    fun addAiImageToTimeline(bitmap: Bitmap, label: String, durationSec: Int = 5) {
        viewModelScope.launch {
            val clipId = "clip_img_${UUID.randomUUID().toString().take(8)}"
            val savedUri = mediaEngine.saveAiClipKeyframe(clipId, bitmap)
            val durMs = (durationSec.coerceIn(2, 12) * 1000L)
            val newClip = TimelineClip(
                clipId = clipId,
                label = label.trim().take(42).ifBlank { "AI Visual" },
                sourceUri = savedUri,
                startMs = 0L,
                endMs = durMs,
                trimStartMs = 0L,
                trimEndMs = durMs,
                transition = TransitionType.CROSS_FADE.raw,
                sampleScene = "AI_ZOOM_IN"
            )
            var startMs = 0L
            applyProjectEdit { proj ->
                val updated = proj.withTypedContent(newClips = proj.typedClips() + newClip)
                startMs = updated.typedClips().firstOrNull { it.clipId == clipId }?.startMs ?: 0L
                updated
            }
            _editorState.update { state ->
                state.copy(
                    selectedClipId = clipId,
                    playheadMs = startMs,
                    clipThumbnails = state.clipThumbnails + (clipId to listOf(bitmap))
                )
            }
            showMessage("Added '${newClip.label}' to timeline!")
        }
    }

    fun saveAiImageToGallery(bitmap: Bitmap, title: String) {
        viewModelScope.launch {
            mediaEngine.saveBitmapToGallery(bitmap, title)
                .onSuccess { showMessage("Saved AI image to Pictures/FrameCut.") }
                .onFailure { err -> showMessage("Save failed: ${err.localizedMessage ?: "Storage error"}") }
        }
    }

    fun generateAiMusic(
        prompt: String,
        option: MusicModelOption,
        instrumentalOnly: Boolean
    ) {
        viewModelScope.launch {
            _editorState.update {
                it.copy(
                    aiMusicGenState = AiMusicGenState.Loading(
                        "Composing music with ${option.modelId} (may take 20–90s)..."
                    )
                )
            }
            val result = aiRepository.generateMusicTrack(prompt, option, instrumentalOnly)
            result.onSuccess { generated ->
                val trackId = "trk_ai_${UUID.randomUUID().toString().take(8)}"
                val (localPath, durationMs) = mediaEngine.saveAiMusicFile(
                    trackId = trackId,
                    audioBytes = generated.audioBytes,
                    mimeType = generated.mimeType
                )
                _editorState.update {
                    it.copy(
                        aiMusicGenState = AiMusicGenState.Success(
                            trackId = trackId,
                            title = generated.title,
                            localAudioPath = localPath,
                            durationMs = durationMs,
                            lyricsOrNotes = generated.lyricsOrNotes,
                            modelUsed = generated.modelUsed
                        )
                    )
                }
            }.onFailure { err ->
                _editorState.update {
                    it.copy(
                        aiMusicGenState = AiMusicGenState.Error(
                            err.message ?: "Music generation failed."
                        )
                    )
                }
            }
        }
    }

    fun addGeneratedMusicToTimeline(result: AiMusicGenState.Success) {
        val track = AudioTrack(
            trackId = result.trackId,
            name = result.title,
            role = AudioRole.MUSIC.raw,
            sourceUri = "ai_audio://${result.localAudioPath}",
            volume = 0.65,
            fadeInMs = 500L,
            fadeOutMs = 1200L,
            offsetMs = 0L,
            durationMs = result.durationMs
        )
        applyProjectEdit { proj ->
            proj.withTypedContent(newAudioTracks = proj.typedAudioTracks() + track)
        }
        _editorState.update { it.copy(selectedTrackId = track.trackId) }
        showMessage("Added AI music '${result.title}' to audio timeline!")
    }

    // --- 4K Export & Cloud Record ---
    fun estimateExportSizeMb(
        resolution: ResolutionOption,
        codec: CodecOption,
        fps: Long,
        format: ExportFormatOption
    ): Double {
        val durationMs = _editorState.value.project?.totalDurationMs ?: 12000L
        return mediaEngine.estimateExportSizeMb(durationMs, resolution, codec, fps, format)
    }

    fun startExport(
        resolution: ResolutionOption,
        codec: CodecOption,
        fps: Long,
        destination: ExportDestination,
        format: ExportFormatOption = ExportFormatOption.MP4
    ) {
        val project = _editorState.value.project ?: return
        if (_editorState.value.isExporting) return
        stopPlayback()
        applyProjectEdit(recordUndo = false) { it.copy(resolution = resolution.raw, frameRate = fps) }
        val updatedProject = _editorState.value.project ?: project
        viewModelScope.launch {
            _editorState.update { it.copy(isExporting = true, exportState = null) }
            // Ensure latest project edits exist in Firestore before logging export
            if (updatedProject.id.isNotBlank()) {
                projectRepository.updateProject(updatedProject.copy(cloudBackupStatus = "EXPORTED"))
            }
            mediaEngine.exportProjectPipeline(
                project = updatedProject,
                resolution = resolution,
                codec = codec,
                fps = fps,
                format = format,
                batterySaverEnabled = localSettings.value.batterySaverEnabled
            ).collect { progress ->
                _editorState.update { it.copy(exportState = progress) }
                if (progress.completedOutputUri != null && updatedProject.id.isNotBlank()) {
                    val record = ExportRecord(
                        userId = currentUserId,
                        projectId = updatedProject.id,
                        projectTitle = updatedProject.title,
                        resolution = resolution.raw,
                        codec = codec.raw,
                        frameRate = fps,
                        durationMs = updatedProject.totalDurationMs,
                        estimatedSizeMb = progress.estimatedSizeMb,
                        destination = destination.raw,
                        localOutputUri = progress.completedOutputUri,
                        status = "COMPLETED"
                    )
                    projectRepository.recordExport(record)
                    if (localSettings.value.autoOfflineBackup) {
                        projectRepository.saveLocalOfflineBackup(
                            currentUserId,
                            updatedProject,
                            "Pre-Export ${resolution.label} ${format.label} Snapshot"
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
