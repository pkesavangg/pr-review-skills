package com.dmdbrands.gurus.weight.migration.di

import android.content.Context
import com.dmdbrands.gurus.weight.migration.service.MigrationRepository
import com.dmdbrands.gurus.weight.migration.service.MigrationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for migration-related classes.
 * Provides MigrationService and MigrationRepository as singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object MigrationModule {

  /**
   * Provides MigrationRepository as a singleton.
   */
  @Provides
  @Singleton
  fun provideMigrationRepository(
    @ApplicationContext context: Context
  ): MigrationRepository = MigrationRepository(context)

  /**
   * Provides MigrationService as a singleton.
   */
  @Provides
  @Singleton
  fun provideMigrationService(
    migrationRepository: MigrationRepository
  ): MigrationService = MigrationService(migrationRepository)
}
