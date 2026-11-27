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

@Database(entities = [ProjectBackupEntity::class], version = 1, exportSchema = false)
abstract class BackupDatabase : RoomDatabase() {
    abstract fun projectBackupDao(): ProjectBackupDao

    companion object {
        @Volatile
        private var INSTANCE: BackupDatabase? = null

        fun getInstance(context: Context): BackupDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BackupDatabase::class.java,
                    "framecut_offline_backups.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
