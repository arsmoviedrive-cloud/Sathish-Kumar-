package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "project_backups")
data class ProjectBackupEntity(
    @PrimaryKey(autoGenerate = true) val backupId: Long = 0L,
    val userId: String,
    val projectId: String,
    val projectTitle: String,
    val resolution: String,
    val durationMs: Long,
    val clipCount: Int,
    val captionCount: Int,
    val payloadJson: String,
    val storageNote: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "guest_projects")
data class GuestProjectEntity(
    @PrimaryKey val projectId: String,
    val title: String,
    val payloadJson: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "guest_exports")
data class GuestExportEntity(
    @PrimaryKey val exportId: String,
    val projectId: String,
    val projectTitle: String,
    val resolution: String,
    val codec: String,
    val frameRate: Long,
    val durationMs: Long,
    val estimatedSizeMb: Double,
    val destination: String,
    val localOutputUri: String,
    val status: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface ProjectBackupDao {
    @Query("SELECT * FROM project_backups WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeBackupsForUser(userId: String): Flow<List<ProjectBackupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: ProjectBackupEntity): Long

    @Query("DELETE FROM project_backups WHERE backupId = :backupId AND userId = :userId")
    suspend fun deleteBackup(backupId: Long, userId: String)

    @Query("SELECT * FROM project_backups WHERE backupId = :backupId AND userId = :userId LIMIT 1")
    suspend fun getBackupById(backupId: Long, userId: String): ProjectBackupEntity?
}

@Dao
interface GuestProjectDao {
    @Query("SELECT * FROM guest_projects ORDER BY updatedAtMillis DESC")
    fun observeGuestProjects(): Flow<List<GuestProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGuestProject(project: GuestProjectEntity)

    @Query("DELETE FROM guest_projects WHERE projectId = :projectId")
    suspend fun deleteGuestProject(projectId: String)
}

@Dao
interface GuestExportDao {
    @Query("SELECT * FROM guest_exports ORDER BY createdAtMillis DESC")
    fun observeGuestExports(): Flow<List<GuestExportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuestExport(export: GuestExportEntity)

    @Query("DELETE FROM guest_exports WHERE exportId = :exportId")
    suspend fun deleteGuestExport(exportId: String)
}

@Database(
    entities = [
        ProjectBackupEntity::class,
        GuestProjectEntity::class,
        GuestExportEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BackupDatabase : RoomDatabase() {
    abstract fun projectBackupDao(): ProjectBackupDao
    abstract fun guestProjectDao(): GuestProjectDao
    abstract fun guestExportDao(): GuestExportDao

    companion object {
        @Volatile
        private var INSTANCE: BackupDatabase? = null

        fun getInstance(context: Context): BackupDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BackupDatabase::class.java,
                    "framecut_offline_backups.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
