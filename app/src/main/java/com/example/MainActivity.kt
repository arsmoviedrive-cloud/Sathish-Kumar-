package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.BackupDatabase
import com.example.data.local.StudioPreferences
import com.example.data.repository.AiVideoAssistantRepository
import com.example.data.repository.ProjectRepository
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.media.VideoMediaEngine
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CameraRecordScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ProjectsHubScreen
import com.example.ui.theme.FrameCutTheme
import com.example.ui.theme.StudioThemeMode
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Full-bleed edge-to-edge layout
        enableEdgeToEdge()

        setContent {
            AppRoot()
        }
    }
}

private sealed interface StudioRoute {
    data object Hub : StudioRoute
    data class Editor(val projectId: String) : StudioRoute
    data class Camera(val appendToActiveProject: Boolean, val returnProjectId: String? = null) : StudioRoute
}

@Composable
private fun AppRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        FrameCutTheme(themeMode = StudioThemeMode.OLED_DARK) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
            }
        }
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedStudioApp(
                currentUserId = uid,
                userDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "Studio Editor",
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@Composable
private fun AuthenticatedStudioApp(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String
) {
    val studioViewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val firestore = ProjectRepository.createConfiguredFirestore(app)
                val backupDao = BackupDatabase.getInstance(app).projectBackupDao()
                val projectRepository = ProjectRepository(firestore, backupDao)
                val aiRepository = AiVideoAssistantRepository()
                val preferences = StudioPreferences(app)
                val mediaEngine = VideoMediaEngine(app)
                StudioViewModel(
                    projectRepository = projectRepository,
                    aiRepository = aiRepository,
                    preferences = preferences,
                    mediaEngine = mediaEngine,
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName
                )
            }
        }
    )

    val settings by studioViewModel.localSettings.collectAsStateWithLifecycle()
    var route by remember(currentUserId) { mutableStateOf<StudioRoute>(StudioRoute.Hub) }

    FrameCutTheme(themeMode = settings.themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val current = route) {
                StudioRoute.Hub -> ProjectsHubScreen(
                    viewModel = studioViewModel,
                    userEmail = userEmail,
                    onOpenEditor = { projectId ->
                        route = StudioRoute.Editor(projectId)
                    },
                    onOpenCamera = {
                        route = StudioRoute.Camera(appendToActiveProject = false)
                    }
                )

                is StudioRoute.Editor -> EditorScreen(
                    viewModel = studioViewModel,
                    onNavigateBack = {
                        route = StudioRoute.Hub
                    },
                    onOpenCameraForClip = {
                        studioViewModel.stopPlayback()
                        route = StudioRoute.Camera(
                            appendToActiveProject = true,
                            returnProjectId = current.projectId
                        )
                    }
                )

                is StudioRoute.Camera -> CameraRecordScreen(
                    batterySaverEnabled = settings.batterySaverEnabled,
                    appendToActiveProject = current.appendToActiveProject,
                    onVideoRecorded = { uri ->
                        if (current.appendToActiveProject && current.returnProjectId != null) {
                            studioViewModel.importVideoClipToTimeline(uri)
                            route = StudioRoute.Editor(current.returnProjectId)
                        } else {
                            studioViewModel.createProjectFromVideoUri(uri) { newProjectId ->
                                route = StudioRoute.Editor(newProjectId)
                            }
                        }
                    },
                    onNavigateBack = {
                        route = if (current.appendToActiveProject && current.returnProjectId != null) {
                            StudioRoute.Editor(current.returnProjectId)
                        } else {
                            StudioRoute.Hub
                        }
                    }
                )
            }
        }
    }
}
