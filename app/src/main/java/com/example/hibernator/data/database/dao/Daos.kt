package com.example.hibernator.data.database.dao

import androidx.room.*
import com.example.hibernator.data.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedAppDao {
    @Query("SELECT * FROM excluded_apps ORDER BY addedAt DESC")
    fun getAllExclusions(): Flow<List<ExcludedAppEntity>>

    @Query("SELECT COUNT(*) > 0 FROM excluded_apps WHERE packageName = :packageName")
    suspend fun isExcluded(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExcludedAppEntity)

    @Query("DELETE FROM excluded_apps WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM excluded_apps")
    suspend fun clearAll()

    // Clean up expired temporary exclusions
    @Query("DELETE FROM excluded_apps WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY createdAt DESC")
    fun getAllSchedules(): Flow<List<HibernateScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HibernateScheduleEntity?

    @Query("SELECT * FROM schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedules(): List<HibernateScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HibernateScheduleEntity): Long

    @Update
    suspend fun update(entity: HibernateScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface HibernateLogDao {
    @Query("SELECT * FROM hibernate_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<HibernateLogEntity>>

    @Query("SELECT * FROM hibernate_logs WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getLogsForPackage(packageName: String): Flow<List<HibernateLogEntity>>

    @Insert
    suspend fun insert(entity: HibernateLogEntity)

    @Query("DELETE FROM hibernate_logs")
    suspend fun clearAll()

    @Query("SELECT * FROM hibernate_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<HibernateLogEntity>

    // Keep only last 1000 logs to prevent DB bloat
    @Query("DELETE FROM hibernate_logs WHERE id NOT IN (SELECT id FROM hibernate_logs ORDER BY timestamp DESC LIMIT 1000)")
    suspend fun pruneOldLogs()
}

@Dao
interface SelectedAppDao {
    @Query("SELECT packageName FROM selected_apps")
    fun getAllSelected(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SelectedAppEntity)

    @Query("DELETE FROM selected_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM selected_apps")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) > 0 FROM selected_apps WHERE packageName = :packageName")
    suspend fun isSelected(packageName: String): Boolean
}
