package com.example.hibernator.di

import android.content.Context
import androidx.room.Room
import com.example.hibernator.data.database.HibernatorDatabase
import com.example.hibernator.data.database.dao.*
import com.example.hibernator.data.repository.*
import com.example.hibernator.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DatabaseModule
 * ================
 * Provides the Room database and all DAOs as singletons.
 * Room handles thread-safety internally.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HibernatorDatabase {
        return Room.databaseBuilder(
            context,
            HibernatorDatabase::class.java,
            HibernatorDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // For dev; replace with proper migrations in prod
            .build()
    }

    @Provides
    fun provideExcludedAppDao(db: HibernatorDatabase): ExcludedAppDao = db.excludedAppDao()

    @Provides
    fun provideScheduleDao(db: HibernatorDatabase): ScheduleDao = db.scheduleDao()

    @Provides
    fun provideHibernateLogDao(db: HibernatorDatabase): HibernateLogDao = db.hibernateLogDao()

    @Provides
    fun provideSelectedAppDao(db: HibernatorDatabase): SelectedAppDao = db.selectedAppDao()
}

/**
 * RepositoryModule
 * ==================
 * Binds repository interfaces to their concrete implementations.
 * Using @Binds for efficiency (no object creation overhead).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository

    @Binds
    @Singleton
    abstract fun bindExclusionRepository(impl: ExclusionRepositoryImpl): ExclusionRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindLogRepository(impl: LogRepositoryImpl): LogRepository

    @Binds
    @Singleton
    abstract fun bindSelectedAppsRepository(impl: SelectedAppsRepositoryImpl): SelectedAppsRepository
}
