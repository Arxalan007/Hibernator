package com.example.hibernator.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.hibernator.data.database.dao.*
import com.example.hibernator.data.database.entity.*

/**
 * HibernatorDatabase
 * ====================
 * Room database with version 1 schema.
 * All data is local-only — no sync, no backup (by design for privacy).
 *
 * Entities:
 * - ExcludedAppEntity: Whitelist
 * - HibernateScheduleEntity: Schedules
 * - HibernateLogEntity: History/logs
 * - SelectedAppEntity: User's current selection
 */
@Database(
    entities = [
        ExcludedAppEntity::class,
        HibernateScheduleEntity::class,
        HibernateLogEntity::class,
        SelectedAppEntity::class
    ],
    version = 1,
    exportSchema = true   // Enable schema export for migration testing
)
abstract class HibernatorDatabase : RoomDatabase() {
    abstract fun excludedAppDao(): ExcludedAppDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun hibernateLogDao(): HibernateLogDao
    abstract fun selectedAppDao(): SelectedAppDao

    companion object {
        const val DATABASE_NAME = "hibernator_db"
    }
}
