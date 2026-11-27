package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.BackupDatabase
import com.example.data.local.GuestExportDao
import com.example.data.local.GuestExportEntity
import com.example.data.local.GuestProjectDao
import com.example.data.local.GuestProjectEntity
import com.example.data.local.ProjectBackupDao
import com.example.data.local.ProjectBackupEntity
import com.example.data.model.ExportRecord
import com.example.data.model.ResolutionOption
import com.example.data.model.UserProfile
import com.example.data.model.VideoProject
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.ui.theme.StudioThemeMode
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

private const val TAG = "ProjectRepository"

class ProjectRepository(
    private val db: FirebaseFirestore,
    private val backupDao: ProjectBackupDao,
    private val guestProjectDao: GuestProjectDao,
    private val guestExportDao: GuestExportDao,
    private val isGuestMode: Boolean = false,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context, isGuestMode: Boolean = false) : this(
        db = createConfiguredFirestore(context.applicationContext),
        backupDao = BackupDatabase.getInstance(context.applicationContext).projectBackupDao(),
        guestProjectDao = BackupDatabase.getInstance(context.applicationContext).guestProjectDao(),
        guestExportDao = BackupDatabase.getInstance(context.applicationContext).guestExportDao(),
        isGuestMode = isGuestMode
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    fun observeProjects(userId: String): Flow<List<VideoProject>> {
        if (isGuestMode || userId == GUEST_USER_ID) {
            return guestProjectDao.observeGuestProjects().map { entities ->
                entities.mapNotNull { entity ->
                    runCatching {
                        VideoProject.fromBackupJson(entity.payloadJson, GUEST_USER_ID).copy(
                            id = entity.projectId,
                            userId = GUEST_USER_ID,
                            cloudBackupStatus = "LOCAL_ONLY",
                            updatedAt = Timestamp(Date(entity.updatedAtMillis))
                        )
                    }.getOrNull()
                }
            }
        }
        return flow {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }
            val path = "users/$uid/projects"
            emitAll(
                db.collection("users")
                    .document(uid)
                    .collection("projects")
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            VideoProject::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val toLog = firestoreError ?: (error as? Exception)
                        if (toLog != null && auth.currentUser?.uid == uid) {
                            handleFirestoreError(toLog, OperationType.LIST, path)
                        }
                        throw error
                    }
            )
        }
    }

    fun observeExports(userId: String): Flow<List<ExportRecord>> {
        if (isGuestMode || userId == GUEST_USER_ID) {
            return guestExportDao.observeGuestExports().map { entities ->
                entities.map { entity ->
                    ExportRecord(
                        id = entity.exportId,
                        userId = GUEST_USER_ID,
                        projectId = entity.projectId,
                        projectTitle = entity.projectTitle,
                        resolution = entity.resolution,
                        codec = entity.codec,
                        frameRate = entity.frameRate,
                        durationMs = entity.durationMs,
                        estimatedSizeMb = entity.estimatedSizeMb,
                        destination = entity.destination,
                        localOutputUri = entity.localOutputUri,
                        status = entity.status,
                        createdAt = Timestamp(Date(entity.createdAtMillis))
                    )
                }
            }
        }
        return flow {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }
            val path = "users/$uid/exports"
            emitAll(
                db.collection("users")
                    .document(uid)
                    .collection("exports")
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            ExportRecord::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val toLog = firestoreError ?: (error as? Exception)
                        if (toLog != null && auth.currentUser?.uid == uid) {
                            handleFirestoreError(toLog, OperationType.LIST, path)
                        }
                        throw error
                    }
            )
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (isGuestMode || userId == GUEST_USER_ID) {
            return flowOf(UserProfile(id = GUEST_USER_ID, userId = GUEST_USER_ID, displayName = "Guest Editor"))
        }
        return flow {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }
            val path = "users/$uid"
            emitAll(
                db.collection("users")
                    .document(uid)
                    .snapshots()
                    .map { snapshot ->
                        if (snapshot.exists()) {
                            snapshot.toObject(
                                UserProfile::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        } else null
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val toLog = firestoreError ?: (error as? Exception)
                        if (toLog != null && auth.currentUser?.uid == uid) {
                            handleFirestoreError(toLog, OperationType.GET, path)
                        }
                        throw error
                    }
            )
        }
    }

    suspend fun ensureUserProfile(
        displayName: String,
        themeMode: StudioThemeMode,
        batterySaverEnabled: Boolean,
        defaultResolution: ResolutionOption
    ): Result<Unit> {
        if (isGuestMode) return Result.success(Unit)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val safeName = displayName.trim().ifBlank { "Studio Editor" }.take(80)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "themeMode" to themeMode.name,
                    "batterySaverEnabled" to batterySaverEnabled,
                    "defaultResolution" to defaultResolution.raw,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(
        themeMode: StudioThemeMode? = null,
        batterySaverEnabled: Boolean? = null,
        defaultResolution: ResolutionOption? = null
    ): Result<Unit> {
        if (isGuestMode) return Result.success(Unit)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val updates = buildMap<String, Any> {
                if (themeMode != null) put("themeMode", themeMode.name)
                if (batterySaverEnabled != null) put("batterySaverEnabled", batterySaverEnabled)
                if (defaultResolution != null) put("defaultResolution", defaultResolution.raw)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createProject(project: VideoProject): Result<VideoProject> {
        if (isGuestMode) {
            return try {
                val localId = "guest_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                val saved = project.copy(
                    id = localId,
                    userId = GUEST_USER_ID,
                    cloudBackupStatus = "LOCAL_ONLY"
                )
                guestProjectDao.upsertGuestProject(
                    GuestProjectEntity(
                        projectId = localId,
                        title = saved.title,
                        payloadJson = saved.toBackupJson(),
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
                Result.success(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save guest project locally", e)
                Result.failure(e)
            }
        }

        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects"
        return try {
            val uid = requireUserId()
            val validDocId = "proj_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val docRef = db.collection("users").document(uid).collection("projects").document(validDocId)
            docPath = docRef.path
            val payload = project.toFirestoreCreateMap(uid)
            docRef.set(payload).await()
            Result.success(project.copy(id = validDocId, userId = uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProject(project: VideoProject): Result<Unit> {
        if (isGuestMode) {
            return try {
                require(project.id.isNotBlank()) { "Project ID must not be blank for local save." }
                val localCopy = project.copy(userId = GUEST_USER_ID, cloudBackupStatus = "LOCAL_ONLY")
                guestProjectDao.upsertGuestProject(
                    GuestProjectEntity(
                        projectId = localCopy.id,
                        title = localCopy.title,
                        payloadJson = localCopy.toBackupJson(),
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update guest project locally", e)
                Result.failure(e)
            }
        }

        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects/${project.id}"
        return try {
            val uid = requireUserId()
            require(project.id.isNotBlank()) { "Project ID must not be blank for update." }
            val docRef = db.collection("users").document(uid).collection("projects").document(project.id)
            docPath = docRef.path
            docRef.update(project.toFirestoreUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        if (isGuestMode) {
            return try {
                guestProjectDao.deleteGuestProject(projectId)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete guest project locally", e)
                Result.failure(e)
            }
        }

        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            require(projectId.isNotBlank()) { "Project ID must not be blank for delete." }
            val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordExport(record: ExportRecord): Result<ExportRecord> {
        if (isGuestMode) {
            return try {
                val exportId = "gexp_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                guestExportDao.insertGuestExport(
                    GuestExportEntity(
                        exportId = exportId,
                        projectId = record.projectId.ifBlank { "local_draft" },
                        projectTitle = record.projectTitle,
                        resolution = record.resolution,
                        codec = record.codec,
                        frameRate = record.frameRate,
                        durationMs = record.durationMs,
                        estimatedSizeMb = record.estimatedSizeMb,
                        destination = record.destination,
                        localOutputUri = record.localOutputUri,
                        status = record.status,
                        createdAtMillis = System.currentTimeMillis()
                    )
                )
                Result.success(record.copy(id = exportId, userId = GUEST_USER_ID))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record guest export locally", e)
                Result.failure(e)
            }
        }

        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/exports"
        return try {
            val uid = requireUserId()
            require(record.projectId.isNotBlank()) { "Project ID is required for export log." }
            val exportId = "exp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val docRef = db.collection("users").document(uid).collection("exports").document(exportId)
            docPath = docRef.path
            docRef.set(record.toFirestoreCreateMap(uid)).await()
            Result.success(record.copy(id = exportId, userId = uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteExport(exportId: String): Result<Unit> {
        if (isGuestMode) {
            return try {
                guestExportDao.deleteGuestExport(exportId)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete guest export locally", e)
                Result.failure(e)
            }
        }

        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/exports/$exportId"
        return try {
            val uid = requireUserId()
            require(exportId.isNotBlank()) { "Export ID must not be blank." }
            val docRef = db.collection("users").document(uid).collection("exports").document(exportId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Local Offline Backup Vault (Room SQLite) ---
    fun observeLocalBackups(userId: String): Flow<List<ProjectBackupEntity>> =
        backupDao.observeBackupsForUser(userId)

    suspend fun saveLocalOfflineBackup(
        userId: String,
        project: VideoProject,
        storageNote: String = "Local SQLite Snapshot"
    ): Result<Long> {
        return try {
            val entity = ProjectBackupEntity(
                userId = userId,
                projectId = project.id.ifBlank { "local_draft" },
                projectTitle = project.title.ifBlank { "Untitled Edit" },
                resolution = project.resolution,
                durationMs = project.totalDurationMs,
                clipCount = project.typedClips().size,
                captionCount = project.typedCaptions().size,
                payloadJson = project.toBackupJson(),
                storageNote = storageNote
            )
            val id = backupDao.insertBackup(entity)
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save offline backup", e)
            Result.failure(e)
        }
    }

    suspend fun deleteLocalBackup(backupId: Long, userId: String): Result<Unit> {
        return try {
            backupDao.deleteBackup(backupId, userId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete offline backup", e)
            Result.failure(e)
        }
    }

    companion object {
        const val GUEST_USER_ID = "local_guest"

        fun createConfiguredFirestore(context: Context): FirebaseFirestore {
            val databaseId = context.getString(R.string.firestore_database_id)
            val firestore = FirebaseFirestore.getInstance(databaseId)
            try {
                val cacheSettings = PersistentCacheSettings.newBuilder()
                    .setSizeBytes(100L * 1024L * 1024L) // 100MB persistent offline disk cache
                    .build()
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(cacheSettings)
                    .build()
                firestore.firestoreSettings = settings
            } catch (_: IllegalStateException) {
                // Settings already configured on this instance
            }
            return firestore
        }
    }
}
